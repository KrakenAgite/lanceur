package app.lanceur.apps

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.os.UserManager
import android.util.Log
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** Raccourci d'une appli (« Nouveau message », « Itinéraire maison »…), tel qu'Android le fournit. */
class AppShortcut(val label: String, val icon: ImageBitmap?, internal val info: ShortcutInfo)

/**
 * Raccourcis des applis, pour le menu de l'appui long. Android ne les donne qu'à l'écran d'accueil par défaut :
 * sinon la liste est simplement vide.
 */
open class AppShortcuts(private val context: Context?) {
    private val launcherApps get() = context?.getSystemService(LauncherApps::class.java)
    private val userManager get() = context?.getSystemService(UserManager::class.java)

    /** Appel système : à faire hors du fil principal. Au plus [limit] raccourcis, dans l'ordre de l'appli. */
    open fun list(key: AppKey, iconPx: Int, limit: Int = 4): List<AppShortcut> = runCatching {
        val apps = launcherApps ?: return emptyList()
        if (!apps.hasShortcutHostPermission()) return emptyList()
        val user = userManager?.getUserForSerialNumber(key.userSerial) ?: return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(key.packageName)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC,
            )
        apps.getShortcuts(query, user).orEmpty()
            .filter { it.isEnabled }
            .sortedWith(compareBy({ !it.isDeclaredInManifest }, { it.rank }))
            .take(limit)
            .map { info ->
                val label = (info.shortLabel ?: info.longLabel)?.toString().orEmpty()
                val icon = runCatching { apps.getShortcutIconDrawable(info, 0)?.toBitmap(iconPx, iconPx)?.asImageBitmap() }.getOrNull()
                AppShortcut(label, icon, info)
            }
            .filter { it.label.isNotBlank() }
    }.onFailure { Log.w("Lanceur", "Raccourcis indisponibles", it) }.getOrDefault(emptyList())

    open fun open(shortcut: AppShortcut): Boolean = runCatching {
        launcherApps?.startShortcut(shortcut.info, null, null) ?: return false
        true
    }.onFailure { Log.w("Lanceur", "Raccourci impossible à ouvrir", it) }.getOrDefault(false)

    companion object {
        val NONE = AppShortcuts(null)
    }
}

/** Fournis par l'écran racine ; les tests et les aperçus n'en ont aucun. */
val LocalAppShortcuts = staticCompositionLocalOf { AppShortcuts.NONE }
