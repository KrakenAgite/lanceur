package app.lanceur.apps

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.net.Uri
import android.os.UserManager
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.util.Log

/** Toutes les actions qui ouvrent quelque chose hors du launcher. */
class AppLauncher(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    /** `false` si l'appli n'existe plus ou ne peut pas être lancée. */
    fun launch(key: AppKey): Boolean = runCatching {
        val user = userManager.getUserForSerialNumber(key.userSerial) ?: return false
        launcherApps.startMainActivity(key.component(), user, null, null)
    }.onFailure { Log.w(TAG, "Lancement impossible : ${key.encode()}", it) }.isSuccess

    fun openAppInfo(key: AppKey) {
        runCatching {
            val user = userManager.getUserForSerialNumber(key.userSerial) ?: return
            launcherApps.startAppDetailsActivity(key.component(), user, null, null)
        }.onFailure { Log.w(TAG, "Infos de l'appli indisponibles", it) }
    }

    fun uninstall(key: AppKey) {
        uninstallIntent(key)?.let(::startSafely)
    }

    /** Désinstalle la copie du bon profil (perso, pro…), pas forcément celle du profil principal. */
    fun uninstallIntent(key: AppKey): Intent? {
        val user = userManager.getUserForSerialNumber(key.userSerial) ?: return null
        return Intent(Intent.ACTION_DELETE, Uri.fromParts("package", key.packageName, null))
            .putExtra(Intent.EXTRA_USER, user)
    }

    fun openClock() {
        startSafely(Intent(AlarmClock.ACTION_SHOW_ALARMS))
    }

    fun openCalendar() {
        val uri = CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
            .also { ContentUris.appendId(it, System.currentTimeMillis()) }
            .build()
        startSafely(Intent(Intent.ACTION_VIEW, uri))
    }

    /** API cachée mais utilisée par tous les launchers ; sans effet si Android la bloque. */
    fun expandNotifications() {
        try {
            val manager = context.getSystemService(StatusBarManager::class.java)
            StatusBarManager::class.java.getMethod("expandNotificationsPanel").invoke(manager)
        } catch (e: Exception) {
            Log.w(TAG, "Volet des notifications indisponible", e)
        }
    }

    fun startSafely(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: RuntimeException) {
        // Aucune appli (ActivityNotFoundException), autorisation refusée (SecurityException),
        // intent trop gros pour le système… : jamais une raison de faire planter l'écran d'accueil
        Log.w(TAG, "Impossible d'ouvrir $intent", e)
        false
    }

    private fun AppKey.component() = ComponentName(packageName, className)

    private companion object {
        const val TAG = "Lanceur"
    }
}
