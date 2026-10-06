package app.lanceur.update

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.util.Log
import app.lanceur.i18n.tr
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Mise à jour sans passer par GitHub ni le navigateur : télécharge l'APK de la release, vérifie son empreinte
 * SHA-256 publiée, puis le confie à l'installeur d'Android (qui vérifie aussi que la signature est la même).
 * La première fois, Android demande une confirmation ; ensuite Lanceur, installeur de l'appli, se met à jour seul.
 */
class UpdateInstaller(context: Context, private val network: Network) {
    private val appContext = context.applicationContext

    /** « Installer des applis inconnues » autorisé pour Lanceur. */
    fun canInstall(): Boolean = appContext.packageManager.canRequestPackageInstalls()

    suspend fun install(release: Release): Boolean {
        val url = release.apkUrl ?: return false
        val sha = release.sha256 ?: return false
        val apk = File(appContext.cacheDir, "update.apk")
        if (network.download(url, apk) !is NetResult.Ok) return false
        return withContext(Dispatchers.IO) {
            runCatching {
                if (!apk.inputStream().use { UpdateCheck.matches(it, sha) }) error("Empreinte différente de celle publiée")
                val installer = appContext.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(appContext.packageName)
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("lanceur.apk", 0, apk.length()).use { out -> apk.inputStream().use { it.copyTo(out) }; session.fsync(out) }
                    val result = PendingIntent.getBroadcast(
                        appContext,
                        id,
                        Intent(appContext, InstallResultReceiver::class.java).setPackage(appContext.packageName),
                        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                    )
                    session.commit(result.intentSender)
                }
                true
            }.onFailure { Log.w("Lanceur", "Mise à jour impossible", it) }.getOrDefault(false).also { if (!it) apk.delete() }
        }
    }
}

/** Réponse de l'installeur : confirmation à demander, ou échec à signaler. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) ?: return
                // Au premier plan, la fenêtre de confirmation s'ouvre ; sinon une notification la propose
                if (runCatching { context.startActivity(confirm) }.isFailure) notify(context, confirm)
            }
            PackageInstaller.STATUS_SUCCESS -> File(context.cacheDir, "update.apk").delete()
            else -> Log.w("Lanceur", "Installation refusée : ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }

    private fun notify(context: Context, confirm: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("updates", tr("Mises à jour", "Updates"), NotificationManager.IMPORTANCE_DEFAULT))
        val tap = PendingIntent.getActivity(context, 1, confirm, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        manager.notify(
            1002,
            android.app.Notification.Builder(context, "updates")
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(tr("Mise à jour de Lanceur prête", "Lanceur update ready"))
                .setContentText(tr("Touche pour l'installer", "Tap to install it"))
                .setContentIntent(tap)
                .setAutoCancel(true)
                .build(),
        )
    }
}
