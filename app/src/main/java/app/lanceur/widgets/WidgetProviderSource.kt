package app.lanceur.widgets

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.pm.LauncherApps
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.apps.ProfileKinds
import kotlin.math.roundToInt

/** Widgets installés, profil principal et profil pro seulement (jamais l'Espace privé). */
class WidgetProviderSource(private val context: Context) {
    private val manager = AppWidgetManager.getInstance(context)
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    @Volatile
    private var infos: Map<AppKey, AppWidgetProviderInfo> = emptyMap()

    /** Appels système bloquants : à faire hors du fil principal. */
    fun entries(): List<ProviderEntry> {
        val density = context.resources.displayMetrics.density
        val found = HashMap<AppKey, AppWidgetProviderInfo>()
        val result = launcherApps.profiles.flatMap { user ->
            val kind = ProfileKinds.of(launcherApps, user)
            if (kind != ProfileKind.MAIN && kind != ProfileKind.OTHER) return@flatMap emptyList<ProviderEntry>()
            val serial = userManager.getSerialNumberForUser(user)
            manager.getInstalledProvidersForProfile(user).map { info ->
                val key = AppKey(info.provider.packageName, info.provider.className, serial)
                found[key] = info
                ProviderEntry(
                    provider = key,
                    profileKind = kind,
                    appLabel = appLabel(info.provider.packageName, user),
                    widgetLabel = info.loadLabel(context.packageManager),
                    minHeightDp = (info.minHeight / density).roundToInt(),
                )
            }
        }
        infos = found
        return result
    }

    /** Aperçu fourni par l'appli (ou son icône), réduit pour tenir dans un carré de `sizePx`. */
    fun preview(entry: ProviderEntry, sizePx: Int): ImageBitmap? = runCatching {
        val info = infos[entry.provider] ?: return null
        val drawable = info.loadPreviewImage(context, 0) ?: info.loadIcon(context, 0) ?: return null
        val width = drawable.intrinsicWidth.coerceAtLeast(1)
        val height = drawable.intrinsicHeight.coerceAtLeast(1)
        val scale = sizePx.toFloat() / maxOf(width, height)
        drawable.toBitmap((width * scale).roundToInt().coerceAtLeast(1), (height * scale).roundToInt().coerceAtLeast(1)).asImageBitmap()
    }.getOrNull()

    private fun appLabel(packageName: String, user: UserHandle): String = runCatching {
        launcherApps.getApplicationInfo(packageName, 0, user).loadLabel(context.packageManager).toString()
    }.getOrDefault(packageName)
}
