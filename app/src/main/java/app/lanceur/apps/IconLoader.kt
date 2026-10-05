package app.lanceur.apps

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.os.UserManager
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Charge les icônes hors du fil principal ; l'icône inclut le badge du profil (pro, privé). */
class IconLoader(context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val cache = LruCache<AppKey, ImageBitmap>(400)

    fun cached(key: AppKey): ImageBitmap? = cache.get(key)

    suspend fun load(key: AppKey, sizePx: Int): ImageBitmap? = cache.get(key) ?: withContext(Dispatchers.IO) {
        runCatching {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return@runCatching null
            val intent = Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(ComponentName(key.packageName, key.className))
            val info = launcherApps.resolveActivity(intent, user) ?: return@runCatching null
            info.getBadgedIcon(0).toBitmap(sizePx, sizePx).asImageBitmap()
        }.getOrNull()?.also { cache.put(key, it) }
    }

    /** Une appli mise à jour peut changer d'icône. */
    fun evict(packageName: String) {
        cache.snapshot().keys.filter { it.packageName == packageName }.forEach { cache.remove(it) }
    }
}
