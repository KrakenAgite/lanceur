package app.lanceur.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.LauncherApps
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Liste des applis lançables de tous les profils, tenue à jour en direct. */
class AppCatalog(
    private val context: Context,
    private val scope: CoroutineScope,
    private val onPackageChanged: (String) -> Unit = {},
) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)

    private val _apps = MutableStateFlow<List<AppEntry>?>(null)

    /** `null` tant que le premier chargement n'est pas terminé. */
    val apps: StateFlow<List<AppEntry>?> = _apps.asStateFlow()

    private val _privateSpace = MutableStateFlow(PrivateSpaceState.ABSENT)
    val privateSpace: StateFlow<PrivateSpaceState> = _privateSpace.asStateFlow()

    private var reloadJob: Job? = null

    private val callback = object : LauncherApps.Callback() {
        override fun onPackageRemoved(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageAdded(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackageChanged(packageName: String, user: UserHandle) = changed(packageName)
        override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
        override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) = reload()
    }

    /** Verrouillage ou déverrouillage de l'Espace privé, ajout ou retrait d'un profil. */
    private val profileReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = reload()
    }

    fun start() {
        launcherApps.registerCallback(callback, Handler(Looper.getMainLooper()))
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PROFILE_AVAILABLE)
            addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            addAction(Intent.ACTION_PROFILE_ADDED)
            addAction(Intent.ACTION_PROFILE_REMOVED)
        }
        context.registerReceiver(profileReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        reload()
    }

    fun reload() {
        reloadJob?.cancel()
        reloadJob = scope.launch(Dispatchers.Default) {
            val profiles = launcherApps.profiles
            val privateUser = profiles.firstOrNull(::isPrivate)
            val privateLocked = privateUser?.let { userManager.isQuietModeEnabled(it) } ?: true
            val entries = profiles.flatMap { user ->
                val isPrivate = user == privateUser
                if (isPrivate && privateLocked) {
                    emptyList()
                } else {
                    val serial = userManager.getSerialNumberForUser(user)
                    launcherApps.getActivityList(null, user).map { info ->
                        val component = info.componentName
                        AppEntry(AppKey(component.packageName, component.className, serial), info.label.toString(), isPrivate)
                    }
                }
            }
            _apps.value = entries
            _privateSpace.value = when {
                privateUser == null -> PrivateSpaceState.ABSENT
                privateLocked -> PrivateSpaceState.LOCKED
                else -> PrivateSpaceState.UNLOCKED
            }
        }
    }

    /**
     * Le catalogue se met à jour via `profileReceiver`. Renvoie `true` si le système va afficher
     * son propre verrou (le déverrouillage attend alors la confirmation de l'utilisateur).
     */
    fun setPrivateSpaceLocked(locked: Boolean): Boolean {
        val user = launcherApps.profiles.firstOrNull(::isPrivate) ?: return false
        return try {
            !userManager.requestQuietModeEnabled(locked, user) && !locked
        } catch (e: SecurityException) {
            Log.w(TAG, "Espace privé : demande refusée", e)
            false
        }
    }

    private fun changed(packageName: String) {
        onPackageChanged(packageName)
        reload()
    }

    private fun isPrivate(user: UserHandle): Boolean = try {
        launcherApps.getLauncherUserInfo(user)?.userType == UserManager.USER_TYPE_PROFILE_PRIVATE
    } catch (e: SecurityException) {
        false
    }

    private companion object {
        const val TAG = "Lanceur"
    }
}
