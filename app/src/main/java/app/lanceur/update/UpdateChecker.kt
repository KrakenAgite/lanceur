package app.lanceur.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import app.lanceur.i18n.tr
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import app.lanceur.prefs.PrefsRepo

/**
 * Regarde sur GitHub s'il existe une version plus récente de Lanceur (au plus toutes les 12 h, quand on revient sur
 * l'accueil ; jamais en arrière-plan) et le signale par une notification, une seule fois par version.
 */
class UpdateChecker(context: Context, private val network: Network, private val prefs: PrefsRepo) {
    private val appContext = context.applicationContext

    val installed: String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull().orEmpty()

    /** [force] : bouton « Vérifier maintenant ». Renvoie la release plus récente, s'il y en a une. */
    suspend fun check(force: Boolean = false): Release? {
        val settings = prefs.readOrNull()?.updates ?: return null
        if (!force && (!settings.enabled || !UpdateCheck.isDue(settings.lastCheck, System.currentTimeMillis()))) return null
        val release = (network.get(UpdateCheck.LATEST_URL) as? NetResult.Ok)?.let { UpdateCheck.parse(it.text()) }
        val newer = release?.takeIf { UpdateCheck.isNewer(it.version, installed) }
        prefs.updateUpdates { it.copy(lastCheck = System.currentTimeMillis(), latest = newer?.version) }
        if (newer != null && settings.notified != newer.version && notify(newer)) {
            prefs.updateUpdates { it.copy(notified = newer.version) }
        }
        return newer
    }

    fun canNotify(): Boolean = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(release: Release): Boolean {
        if (!canNotify()) return false
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mises à jour", "Updates"), NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(
            appContext,
            0,
            Intent(Intent.ACTION_VIEW, Uri.parse(release.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(tr("Lanceur ${release.version} est disponible", "Lanceur ${release.version} is available"))
            .setContentText(tr("Touche pour ouvrir la page de la version", "Tap to open the release page"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(NOTIFICATION_ID, notification)
        return true
    }

    private companion object {
        const val CHANNEL = "updates"
        const val NOTIFICATION_ID = 1001
    }
}
