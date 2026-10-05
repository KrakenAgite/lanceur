package app.lanceur

import android.content.ActivityNotFoundException
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppIcon
import app.lanceur.apps.AppKey
import app.lanceur.apps.HomeRole
import app.lanceur.apps.HomeRoleWatcher
import app.lanceur.builtin.BuiltinKind
import app.lanceur.builtin.BuiltinPreview
import app.lanceur.builtin.BuiltinServices
import app.lanceur.builtin.BuiltinSettingsServices
import app.lanceur.builtin.BuiltinSettingsSheet
import app.lanceur.builtin.BuiltinSlots
import app.lanceur.builtin.BuiltinWidget
import app.lanceur.builtin.calendar.CalendarCardActions
import app.lanceur.builtin.contacts.FavoritesActions
import app.lanceur.builtin.shortcuts.ShortcutActions
import app.lanceur.home.HomeActions
import app.lanceur.home.HomePager
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
import app.lanceur.summary.DaySummaryState
import app.lanceur.ui.AppMenuAction
import app.lanceur.vault.Authenticator
import app.lanceur.vault.VaultActions
import app.lanceur.vault.VaultEvent
import app.lanceur.vault.VaultScreen
import app.lanceur.vault.VaultState
import app.lanceur.widgets.HostedWidget
import app.lanceur.widgets.PickerCatalog
import app.lanceur.widgets.ProviderEntry
import app.lanceur.widgets.VisibleWidgets
import app.lanceur.widgets.WidgetPage
import app.lanceur.widgets.WidgetPageActions
import app.lanceur.widgets.WidgetPicker
import app.lanceur.widgets.WidgetSlot
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Actions qui passent par l'activité : écrans d'Android pour lier ou configurer un widget. */
class WidgetHostActions(
    val add: (ProviderEntry) -> Unit = {},
    val reconfigure: (WidgetSlot) -> Unit = {},
)

/** Assemble les écrans : les deux pages (widgets, accueil) sont dessous, les autres écrans se superposent. */
@Composable
fun AppRoot(vm: LauncherViewModel, searchVm: SearchViewModel, container: AppContainer, widgetHostActions: WidgetHostActions) {
    val context = LocalContext.current
    val activity = LocalActivity.current ?: return
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val lists by vm.lists.collectAsStateWithLifecycle()
    val prefs by vm.prefs.collectAsStateWithLifecycle()
    val screen by vm.screen.collectAsStateWithLifecycle()
    val mode by vm.listMode.collectAsStateWithLifecycle()
    val vault by vm.vault.collectAsStateWithLifecycle()
    val widgetEditMode by vm.widgetEditMode.collectAsStateWithLifecycle()
    val homePageRequests by vm.homePageRequests.collectAsStateWithLifecycle()
    val privateSpace by container.catalog.privateSpace.collectAsStateWithLifecycle()
    val query by searchVm.query.collectAsStateWithLifecycle()
    val results by searchVm.results.collectAsStateWithLifecycle()

    val scope = rememberCoroutineScope()
    val authenticator = remember(activity) { Authenticator(activity) }
    val icon: @Composable (AppKey) -> Unit = { key -> AppIcon(key, container.iconLoader) }
    val roleWatcher = remember { HomeRoleWatcher { container.catalog.reload() } }
    var isDefault by remember { mutableStateOf(HomeRole.isHeld(context).also(roleWatcher::update)) }
    var permissionsGranted by remember { mutableStateOf(SearchPermissions.allGranted(context)) }
    var lockServiceEnabled by remember { mutableStateOf(LockScreenService.isEnabled(context)) }
    var summary by remember { mutableStateOf<DaySummaryState?>(null) }
    var widgetRefresh by remember { mutableIntStateOf(0) }
    var pickerQuery by remember { mutableStateOf("") }
    // Feuille de réglages d'un widget intégré : à l'ajout (`appWidgetId` nul) ou par ⚙
    var settingsRequest by remember { mutableStateOf<Pair<BuiltinKind, Int?>?>(null) }

    fun reloadSummary() {
        scope.launch { summary = container.daySummary.load() }
    }

    LifecycleResumeEffect(Unit) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
        permissionsGranted = SearchPermissions.allGranted(context)
        lockServiceEnabled = LockScreenService.isEnabled(context)
        widgetRefresh++
        reloadSummary()
        onPauseOrDispose { }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        permissionsGranted = SearchPermissions.allGranted(context)
        searchVm.refresh()
        reloadSummary()
    }
    var locationGranted by remember { mutableStateOf(SearchPermissions.granted(context, android.Manifest.permission.ACCESS_COARSE_LOCATION)) }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { locationGranted = it }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        isDefault = HomeRole.isHeld(context).also(roleWatcher::update)
    }

    // Pas d'écoute des widgets quand la page est désactivée (MainActivity gère onStart / onStop)
    LaunchedEffect(prefs.widgetPageEnabled) {
        if (prefs.widgetPageEnabled) container.widgetHost.startListening() else container.widgetHost.stopListening()
    }
    val availableIds = remember(prefs.widgets, widgetRefresh) { container.widgetHost.availableIds(prefs.widgets) }
    val widgetCards = remember(prefs.widgets, prefs.hidden, availableIds) {
        VisibleWidgets.compute(prefs.widgets, prefs.hidden, availableIds)
    }
    val widgetLabels = remember(prefs.widgets, widgetRefresh) {
        prefs.widgets.associate { slot ->
            slot.appWidgetId to when {
                BuiltinSlots.isBuiltin(slot) -> BuiltinSlots.kindOf(slot)?.label ?: "Widget Lanceur"
                else -> container.widgetHost.label(slot.appWidgetId)
            }
        }
    }
    val providerEntries by produceState(emptyList<ProviderEntry>(), screen) {
        if (screen == Screen.WIDGET_PICKER) value = withContext(Dispatchers.IO) { container.widgetProviders.entries() }
    }
    val pickerGroups = remember(providerEntries, prefs.hidden, pickerQuery) {
        PickerCatalog.build(providerEntries, prefs.hidden, pickerQuery, BuiltinSlots.pickerEntries())
    }
    val widgetPreview: @Composable (ProviderEntry) -> Unit = { entry ->
        val kind = BuiltinSlots.kindOf(entry.provider)
        if (kind != null) {
            BuiltinPreview(kind)
        } else {
            val sizePx = with(LocalDensity.current) { 96.dp.roundToPx() }
            val bitmap by produceState<ImageBitmap?>(null, entry) {
                value = withContext(Dispatchers.IO) { container.widgetProviders.preview(entry, sizePx) }
            }
            bitmap?.let { Image(it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
        }
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

    fun removeWidget(slot: WidgetSlot) {
        // Un widget intégré n'existe pas pour Android : rien à libérer
        if (!BuiltinSlots.isBuiltin(slot)) container.widgetHost.deleteId(slot.appWidgetId)
        vm.removeWidget(slot.appWidgetId)
    }

    BackHandler(enabled = screen != Screen.HOME || mode != ListMode.Favorites) { vm.back() }
    LaunchedEffect(screen) {
        if (screen != Screen.SEARCH) searchVm.setQuery("")
        if (screen != Screen.WIDGET_PICKER) pickerQuery = ""
    }

    // Sous la recherche translucide, seul le fond d'écran doit transparaître
    val homeAlpha by animateFloatAsState(if (screen == Screen.SEARCH) 0f else 1f, label = "homeAlpha")
    val builtinServices = BuiltinServices(
        battery = container.battery,
        nowPlaying = container.nowPlaying,
        calendar = container.calendarRange,
        calendarActions = CalendarCardActions(
            openEvent = { event ->
                container.resultActions.open(SearchResult.Event(event.eventId, event.title, event.begin, event.end, event.allDay, null))
            },
            openDay = { day -> container.appLauncher.openCalendar(day.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()) },
            requestCalendar = { permissionLauncher.launch(SearchPermissions.ALL) },
        ),
        openBatterySettings = { container.appLauncher.startSafely(Intent(Intent.ACTION_POWER_USAGE_SUMMARY)) },
        openPlayer = { container.nowPlaying.openIntent()?.let(container.appLauncher::startSafely) },
        grantMediaAccess = {
            if (!container.appLauncher.startSafely(container.nowPlaying.accessSettingsIntent())) {
                container.appLauncher.startSafely(Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
        },
        data = { prefs.widgetData[it] },
        saveData = vm::setWidgetData,
        openSettings = { slot -> BuiltinSlots.kindOf(slot)?.let { settingsRequest = it to slot.appWidgetId } },
        startTimer = { seconds ->
            val direct = Intent(AlarmClock.ACTION_SET_TIMER)
                .putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                .putExtra(AlarmClock.EXTRA_SKIP_UI, true)
            container.appLauncher.startSafely(direct).also { started ->
                if (!started) container.appLauncher.startSafely(Intent(AlarmClock.ACTION_SHOW_TIMERS))
            }
        },
        openTimers = { container.appLauncher.startSafely(Intent(AlarmClock.ACTION_SHOW_TIMERS)) },
        contacts = container.favoriteContacts,
        favoritesActions = FavoritesActions(
            call = container.resultActions::call,
            sms = container.resultActions::sms,
            open = { uri -> container.resultActions.open(SearchResult.Contact(uri, "", null)) },
            requestPermission = { permissionLauncher.launch(SearchPermissions.ALL) },
        ),
        torch = container.torch,
        shortcutActions = ShortcutActions(
            toggleTorch = { on -> if (!container.torch.set(on)) toast("Lampe indisponible") },
            internet = { container.appLauncher.startSafely(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
            bluetooth = { container.appLauncher.startSafely(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
            sound = { container.appLauncher.startSafely(Intent(Settings.Panel.ACTION_VOLUME)) },
            doNotDisturb = {
                container.appLauncher.startSafely(Intent("android.settings.ZEN_MODE_SETTINGS")) ||
                    container.appLauncher.startSafely(Intent(Settings.ACTION_SOUND_SETTINGS))
            },
        ),
        storage = container.storage,
        openStorageSettings = { container.appLauncher.startSafely(Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)) },
        weather = container.weather,
        openUrl = { url -> container.appLauncher.startSafely(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))) },
        refresh = widgetRefresh,
    )
    Box(Modifier.fillMaxSize()) {
        // Seul le fond d'écran est visible pendant les quelques millisecondes du chargement
        if (loaded) HomePager(
            modifier = Modifier.graphicsLayer { alpha = homeAlpha },
            widgetsEnabled = prefs.widgetPageEnabled,
            homePageRequests = homePageRequests,
            backEnabled = screen == Screen.HOME,
            editMode = widgetEditMode,
            onExitEdit = { vm.setWidgetEditMode(false) },
            onWidgetsShown = {
                widgetRefresh++
                reloadSummary()
            },
            widgetPage = {
                WidgetPage(
                    summary = summary,
                    cards = widgetCards,
                    editMode = widgetEditMode,
                    label = { widgetLabels[it.appWidgetId] ?: "Widget" },
                    isReconfigurable = { if (BuiltinSlots.isBuiltin(it)) BuiltinSlots.kindOf(it)?.configurable == true else container.widgetHost.isReconfigurable(it.appWidgetId) },
                    widgetView = { slot, modifier ->
                        if (BuiltinSlots.isBuiltin(slot)) BuiltinWidget(slot, builtinServices, modifier)
                        else HostedWidget(slot, container.widgetHost, modifier)
                    },
                    actions = WidgetPageActions(
                        openEvent = { event ->
                            container.resultActions.open(SearchResult.Event(event.eventId, event.title, event.begin, event.end, event.allDay, null))
                        },
                        openClock = { container.appLauncher.openClock() },
                        requestCalendar = { permissionLauncher.launch(SearchPermissions.ALL) },
                        addWidget = { vm.show(Screen.WIDGET_PICKER) },
                        setEditMode = vm::setWidgetEditMode,
                        remove = ::removeWidget,
                        resize = { slot, size -> vm.setWidgetSize(slot.appWidgetId, size) },
                        reconfigure = { slot -> BuiltinSlots.kindOf(slot)?.let { settingsRequest = it to slot.appWidgetId } ?: widgetHostActions.reconfigure(slot) },
                        reorder = vm::setWidgetsOrder,
                    ),
                )
            },
            home = {
                HomeScreen(
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
            },
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
        AnimatedVisibility(
            visible = screen == Screen.WIDGET_PICKER,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            WidgetPicker(
                groups = pickerGroups,
                query = pickerQuery,
                onQueryChange = { pickerQuery = it },
                preview = widgetPreview,
                onPick = { entry ->
                    vm.show(Screen.HOME)
                    val kind = BuiltinSlots.kindOf(entry.provider)
                    if (kind != null) {
                        if (kind.configurable) settingsRequest = kind to null else vm.addBuiltinWidget(kind)
                    } else {
                        widgetHostActions.add(entry)
                    }
                },
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
                widgetPageEnabled = prefs.widgetPageEnabled,
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
                    setWidgetPageEnabled = vm::setWidgetPageEnabled,
                ),
            )
        }
        settingsRequest?.let { (kind, id) ->
            BuiltinSettingsSheet(
                kind = kind,
                initial = id?.let { prefs.widgetData[it] },
                onSave = { data ->
                    if (id == null) vm.addBuiltinWidget(kind, data) else vm.setWidgetData(id, data)
                    settingsRequest = null
                },
                onDismiss = { settingsRequest = null },
                services = BuiltinSettingsServices(
                    searchPlaces = { container.weather.search(it) },
                    locationGranted = locationGranted,
                    requestLocation = { locationLauncher.launch(android.Manifest.permission.ACCESS_COARSE_LOCATION) },
                ),
            )
        }
    }
}
