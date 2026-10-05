package app.lanceur

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppIcon
import app.lanceur.apps.AppKey
import app.lanceur.apps.HomeRole
import app.lanceur.apps.HomeRoleWatcher
import app.lanceur.home.HomeActions
import app.lanceur.home.HomeScreen
import app.lanceur.home.LauncherViewModel
import app.lanceur.home.ListMode
import app.lanceur.home.Screen
import app.lanceur.lock.LockScreenService
import app.lanceur.search.SearchActions
import app.lanceur.search.SearchPermissions
import app.lanceur.search.SearchResult
import app.lanceur.search.SearchScreen
import app.lanceur.search.SearchViewModel
import app.lanceur.settings.SettingsActions
import app.lanceur.settings.SettingsScreen
import app.lanceur.ui.AppMenuAction
import app.lanceur.vault.Authenticator
import app.lanceur.vault.VaultActions
import app.lanceur.vault.VaultEvent
import app.lanceur.vault.VaultScreen
import app.lanceur.vault.VaultState

/** Assemble les écrans : l'accueil est toujours dessous, les autres écrans se superposent. */
@Composable
fun AppRoot(vm: LauncherViewModel, searchVm: SearchViewModel, container: AppContainer) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val mode by vm.listMode.collectAsStateWithLifecycle()
    val vault by vm.vault.collectAsStateWithLifecycle()
    val privateSpace by container.catalog.privateSpace.collectAsStateWithLifecycle()
    val query by searchVm.query.collectAsStateWithLifecycle()
    val results by searchVm.results.collectAsStateWithLifecycle()

    val authenticator = remember(activity) { Authenticator(activity) }
    val icon: @Composable (AppKey) -> Unit = { key -> AppIcon(key, container.iconLoader) }
    val roleWatcher = remember { HomeRoleWatcher { container.catalog.reload() } }
    var isDefault by remember { mutableStateOf(HomeRole.isHeld(context).also(roleWatcher::update)) }
    var permissionsGranted by remember { mutableStateOf(SearchPermissions.allGranted(context)) }
    var lockServiceEnabled by remember { mutableStateOf(LockScreenService.isEnabled(context)) }

    LifecycleResumeEffect(Unit) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
        permissionsGranted = SearchPermissions.allGranted(context)
        lockServiceEnabled = LockScreenService.isEnabled(context)
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = SearchPermissions.allGranted(context)
        searchVm.refresh()
    }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
    }

    fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun launch(entry: AppEntry) {
        if (!container.appLauncher.launch(entry.key)) {
            toast("Appli introuvable")
            container.catalog.reload()
        }
    }

    fun openVault() {
        when {
            vm.vault.value is VaultState.Unlocked -> vm.show(Screen.VAULT)
            !authenticator.canAuthenticate() -> toast("Configure un verrouillage d'écran pour utiliser le dossier caché")
            vm.requestVault() -> authenticator.authenticate("Dossier caché") { ok ->
                vm.vaultEvent(if (ok) VaultEvent.AuthSucceeded else VaultEvent.AuthFailed)
            }
        }
    }

    fun openAccessibilitySettings() {
        container.appLauncher.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun lockScreen() {
        if (!LockScreenService.lock()) {
            toast("Active « Lanceur » dans Accessibilité pour verrouiller d'un double toucher")
            openAccessibilitySettings()
        }
    }

    fun onMenu(entry: AppEntry, action: AppMenuAction) {
        when (action) {
            AppMenuAction.ADD_FAVORITE -> vm.addFavorite(entry.key)
            AppMenuAction.REMOVE_FAVORITE -> vm.removeFavorite(entry.key)
            AppMenuAction.HIDE -> vm.hide(entry.key)
            AppMenuAction.UNHIDE -> vm.unhide(entry.key)
            AppMenuAction.INFO -> container.appLauncher.openAppInfo(entry.key)
            AppMenuAction.UNINSTALL -> container.appLauncher.uninstall(entry.key)
        }
    }

    fun openResult(result: SearchResult) {
        if (result == SearchResult.PermissionHint) {
            permissionLauncher.launch(SearchPermissions.ALL)
            return
        }
        val opened = container.resultActions.open(result)
        if (!opened && result is SearchResult.App) {
            toast("Appli introuvable")
            container.catalog.reload()
        }
        // Copier un calcul garde la recherche ouverte
        if (opened && result !is SearchResult.Calc) vm.show(Screen.HOME)
    }

    BackHandler(enabled = screen != Screen.HOME || mode != ListMode.Favorites) { vm.back() }
    LaunchedEffect(screen) { if (screen != Screen.SEARCH) searchVm.setQuery("") }

    Box(Modifier.fillMaxSize()) {
        // Seul le fond d'écran est visible pendant les quelques millisecondes du chargement
        if (loaded) HomeScreen(
            lists = lists,
            mode = mode,
            side = prefs.alphabetSide,
            icon = icon,
            actions = HomeActions(
                launch = ::launch,
                menu = ::onMenu,
                changeMode = vm::setListMode,
                openSearch = { vm.show(Screen.SEARCH) },
                openNotifications = { container.appLauncher.expandNotifications() },
                openVault = ::openVault,
                openSettings = { vm.show(Screen.SETTINGS) },
                openClock = { container.appLauncher.openClock() },
                openCalendar = { container.appLauncher.openCalendar() },
                lockScreen = ::lockScreen,
            ),
        )
        AnimatedVisibility(
            visible = screen == Screen.SEARCH,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            SearchScreen(
                query = query,
                results = results,
                icon = icon,
                actions = SearchActions(
                    queryChange = searchVm::setQuery,
                    open = ::openResult,
                    call = { container.resultActions.call(it) },
                    sms = { container.resultActions.sms(it) },
                    requestPermissions = { permissionLauncher.launch(SearchPermissions.ALL) },
                    dismissHint = { vm.dismissPermissionHint { searchVm.refresh() } },
                    close = { vm.show(Screen.HOME) },
                ),
            )
        }
        // Pas d'animation de sortie : le contenu caché disparaît immédiatement au verrouillage
        AnimatedVisibility(
            visible = screen == Screen.VAULT && vault is VaultState.Unlocked,
            enter = fadeIn(),
            exit = ExitTransition.None,
        ) {
            VaultScreen(
                hidden = lists.hiddenApps,
                privateApps = lists.privateApps,
                privateSpace = privateSpace,
                icon = icon,
                actions = VaultActions(
                    launch = ::launch,
                    menu = ::onMenu,
                    unlockPrivateSpace = {
                        // L'écran du système s'ouvre après ce callback : l'événement arrive avant la mise en arrière-plan
                        if (container.catalog.setPrivateSpaceLocked(false)) vm.vaultEvent(VaultEvent.ExternalPromptStarted)
                    },
                    lockPrivateSpace = { container.catalog.setPrivateSpaceLocked(true) },
                ),
            )
        }
        AnimatedVisibility(visible = screen == Screen.SETTINGS, enter = fadeIn(), exit = fadeOut()) {
            SettingsScreen(
                favorites = lists.favorites,
                side = prefs.alphabetSide,
                isDefaultLauncher = isDefault,
                permissionsGranted = permissionsGranted,
                lockServiceEnabled = lockServiceEnabled,
                icon = icon,
                actions = SettingsActions(
                    setDefault = {
                        try {
                            roleLauncher.launch(HomeRole.requestIntent(context))
                        } catch (e: ActivityNotFoundException) {
                            container.appLauncher.startSafely(Intent(Settings.ACTION_HOME_SETTINGS))
                        }
                    },
                    reorderFavorites = vm::setFavoritesOrder,
                    openHidden = ::openVault,
                    requestPermissions = { permissionLauncher.launch(SearchPermissions.ALL) },
                    setSide = vm::setAlphabetSide,
                    enableLockService = ::openAccessibilitySettings,
                ),
            )
        }
    }
}
