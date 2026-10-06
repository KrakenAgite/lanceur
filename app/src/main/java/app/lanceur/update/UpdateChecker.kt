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
import kotlinx.coroutines.launch

/**
 * Regarde sur GitHub s'il existe une version plus récente de Lanceur (au plus toutes les 12 h, quand on revient sur
 * l'accueil ; jamais en arrière-plan) et le signale par une notification, une seule fois par version.
 */
class UpdateChecker(context: Context, private val network: Network, private val prefs: PrefsRepo) {
    private val appContext = context.applicationContext
    val installer = UpdateInstaller(appContext, network)

    val installed: String =
        runCatching { appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName }.getOrNull().orEmpty()

    /** Bouton « Installer » : télécharge et installe tout de suite. */
    suspend fun installNow(): Boolean {
        val release = (network.get(UpdateCheck.LATEST_URL) as? NetResult.Ok)?.let { UpdateCheck.parse(it.text()) } ?: return false
        return UpdateCheck.isNewer(release.version, installed) && installer.install(release)
    }

    /** [force] : bouton « Vérifier maintenant ». Renvoie la release plus récente, s'il y en a une. */
    suspend fun check(force: Boolean = false): Release? {
        val settings = prefs.readOrNull()?.updates ?: return null
        if (!force && (!settings.enabled || !UpdateCheck.isDue(settings.lastCheck, System.currentTimeMillis()))) return null
        val release = (network.get(UpdateCheck.LATEST_URL) as? NetResult.Ok)?.let { UpdateCheck.parse(it.text()) }
        val newer = release?.takeIf { UpdateCheck.isNewer(it.version, installed) }
        prefs.updateUpdates { it.copy(lastCheck = System.currentTimeMillis(), latest = newer?.version) }
        if (newer == null) return null
        // Installation automatique si possible ; sinon (ou en cas d'échec) une notification, une fois par version
        val installed = settings.autoInstall && installer.canInstall() && installer.install(newer)
        if (!installed && settings.notified != newer.version && notify(newer)) {
            prefs.updateUpdates { it.copy(notified = newer.version) }
        }
        return newer
    }

    fun unknownSourcesIntent(): Intent =
        Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + appContext.packageName))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun canNotify(): Boolean = appContext.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun notify(release: Release): Boolean {
        if (!canNotify()) return false
        val manager = appContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, tr("Mises à jour", "Updates"), NotificationManager.IMPORTANCE_DEFAULT))
        // Sans l'autorisation d'installer : le réglage d'Android à ouvrir ; sinon, toucher lance l'installation
        val open = if (!installer.canInstall()) {
            PendingIntent.getActivity(appContext, 0, unknownSourcesIntent(), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        } else {
            PendingIntent.getBroadcast(
                appContext,
                0,
                Intent(appContext, InstallNowReceiver::class.java).setPackage(appContext.packageName),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = android.app.Notification.Builder(appContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(tr("Lanceur ${release.version} est disponible", "Lanceur ${release.version} is available"))
            .setContentText(
                if (installer.canInstall()) tr("Touche pour l'installer", "Tap to install it")
                else tr("Touche pour autoriser Lanceur à installer ses mises à jour", "Tap to let Lanceur install its updates"),
            )
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

/** Toucher la notification « disponible » : télécharge et installe. */
class InstallNowReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val container = (context.applicationContext as app.lanceur.LanceurApp).container
        val pending = goAsync()
        container.appScope.launch {
            try { container.updates.installNow() } finally { pending.finish() }
        }
    }
}
