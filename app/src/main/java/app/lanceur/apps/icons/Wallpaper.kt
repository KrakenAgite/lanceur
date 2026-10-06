package app.lanceur.apps.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** L'outil de fond d'écran du téléphone (Google, Samsung, Xiaomi…) et son nom. */
class Wallpaper(private val context: Context) {
    private fun choice(): Pair<String, String>? {
        val pm = context.packageManager
        val found = pm.queryIntentActivities(Intent(Intent.ACTION_SET_WALLPAPER), PackageManager.MATCH_DEFAULT_ONLY)
        val pkg = WallpaperPicker.pick(found.map { it.activityInfo.packageName }) ?: return null
        val label = found.first { it.activityInfo.packageName == pkg }.loadLabel(pm).toString()
        return pkg to label
    }

    fun label(): String? = runCatching { choice()?.second }.getOrNull()

    fun intent(): Intent {
        val pkg = runCatching { choice()?.first }.getOrNull()
        return Intent(Intent.ACTION_SET_WALLPAPER).apply { if (pkg != null) setPackage(pkg) }
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
