package app.lanceur.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.res.Configuration
import android.graphics.drawable.BitmapDrawable
import android.os.Process
import android.os.UserManager
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.lanceur.apps.icons.IconPacks
import app.lanceur.apps.icons.IconRenderer
import app.lanceur.apps.icons.IconShape
import app.lanceur.apps.icons.IconStyle
import app.lanceur.apps.icons.LoadedPack
import app.lanceur.apps.icons.ThemedColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Charge les icônes hors du fil principal, selon le style choisi (pack, forme, thématisées) ;
 * l'icône garde le badge du profil (pro, privé).
 */
class IconLoader(context: Context) {
    private val appContext = context.applicationContext
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = LruCache<AppKey, ImageBitmap>(400)

    @Volatile private var style = IconStyle()
    @Volatile private var pack: LoadedPack? = null

    /** Change à chaque nouveau style : les icônes affichées se rechargent. */
    var version by mutableIntStateOf(0)
        private set

    fun cached(key: AppKey): ImageBitmap? = cache.get(key)

    suspend fun setStyle(newStyle: IconStyle) {
        if (newStyle == style) return
        apply(newStyle)
    }

    /** Pack mis à jour ou désinstallé : on relit ses correspondances. */
    suspend fun packChanged(packageName: String) {
        if (packageName == style.pack) apply(style)
    }

    private suspend fun apply(newStyle: IconStyle) {
        val newPack = withContext(Dispatchers.IO) { newStyle.pack?.let { IconPacks.load(appContext, it) } }
        style = newStyle
        pack = newPack
        cache.evictAll()
        version++
    }

    suspend fun load(key: AppKey, sizePx: Int): ImageBitmap? = cache.get(key) ?: withContext(Dispatchers.IO) {
        runCatching {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return@runCatching null
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(key.packageName, key.className))
            val info = launcherApps.resolveActivity(intent, user) ?: return@runCatching null
            val current = style
            val density = appContext.resources.displayMetrics.densityDpi
            val fromPack = pack?.let { p -> p.filter.drawableFor(key)?.let { p.drawable(it, density) } }
            val bitmap = when {
                fromPack != null -> IconRenderer.renderPack(fromPack, sizePx)
                current.shape == IconShape.SYSTEM && !current.themed -> return@runCatching info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap()
                else -> IconRenderer.render(info.getIcon(density), current.shape, if (current.themed) themedColors() else null, sizePx)
            }
            val badged = if (user == Process.myUserHandle()) bitmap
            else appContext.packageManager.getUserBadgedIcon(BitmapDrawable(appContext.resources, bitmap), user).toBitmap(sizePx, sizePx)
            badged.asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Comme les icônes thématisées du Pixel : teintes claires le jour, sombres la nuit. */
    private fun themedColors(): ThemedColors {
        val dark = appContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        return if (dark) ThemedColors(appContext.getColor(android.R.color.system_neutral1_800), appContext.getColor(android.R.color.system_accent1_100))
        else ThemedColors(appContext.getColor(android.R.color.system_accent1_100), appContext.getColor(android.R.color.system_neutral2_700))
    }

    /** Une appli mise à jour peut changer d'icône. */
    fun evict(packageName: String) {
        cache.snapshot().keys.filter { it.packageName == packageName }.forEach { cache.remove(it) }
    }
}
