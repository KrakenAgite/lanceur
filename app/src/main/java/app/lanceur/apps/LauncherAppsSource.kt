package app.lanceur.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

/** Lecture des applis et des profils via `LauncherApps` (appels système bloquants). */
class LauncherAppsSource(private val context: Context) : CatalogSource {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val me = Process.myUserHandle()

    override fun start(onChange: (changedPackage: String?) -> Unit) {
        launcherApps.registerCallback(
            object : LauncherApps.Callback() {
                override fun onPackageRemoved(packageName: String, user: UserHandle) = onChange(packageName)
                override fun onPackageAdded(packageName: String, user: UserHandle) = onChange(packageName)
                override fun onPackageChanged(packageName: String, user: UserHandle) = onChange(packageName)
                override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange(null)
                override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = onChange(null)
            },
            Handler(Looper.getMainLooper()),
        )
        // Verrouillage ou déverrouillage de l'Espace privé, ajout ou retrait d'un profil
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
        }
        context.registerReceiver(
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) = onChange(null)
            },
            filter,
            Context.RECEIVER_NOT_EXPORTED,
        )
    }

    override suspend fun snapshot(): List<ProfileSnapshot> = launcherApps.profiles.map { user ->
        val kind = kindOf(user)
        val quiet = kind == ProfileKind.PRIVATE && userManager.isQuietModeEnabled(user)
        val apps = if (kind == ProfileKind.UNKNOWN || quiet) {
            emptyList()
        } else {
            val serial = userManager.getSerialNumberForUser(user)
            launcherApps.getActivityList(null, user).map { info ->
                val component = info.componentName
                AppEntry(AppKey(component.packageName, component.className, serial), info.label.toString(), isPrivateSpace = false)
            }
        }
        ProfileSnapshot(kind, quiet, apps)
    }

    override fun setPrivateSpaceLocked(locked: Boolean): Boolean {
        val user = launcherApps.profiles.firstOrNull { kindOf(it) == ProfileKind.PRIVATE } ?: return false
        return try {
            // `false` au déverrouillage : le système demande d'abord le verrou de l'Espace privé
            !userManager.requestQuietModeEnabled(locked, user) && !locked
        } catch (e: SecurityException) {
            Log.w(TAG, "Espace privé : demande refusée", e)
            false
        }
    }

    private fun kindOf(user: UserHandle): ProfileKind = ProfileKinds.of(launcherApps, user, me)

    private companion object {
        const val TAG = "Lanceur"
    }
}
