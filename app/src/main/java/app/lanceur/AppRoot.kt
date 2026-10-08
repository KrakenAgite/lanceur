package app.lanceur

import app.lanceur.i18n.tr
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import app.lanceur.news.ArticleWebView
import app.lanceur.ui.LocalAppLabelStyle
import app.lanceur.ui.blockTouchesBelow
import app.lanceur.apps.icons.IconPacks
import app.lanceur.apps.icons.Wallpaper
import app.lanceur.settings.AppearanceState
import app.lanceur.settings.BackupState
import app.lanceur.settings.UpdatesState
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.CompositionLocalProvider
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
import app.lanceur.builtin.rememberMinuteClock
import app.lanceur.builtin.shortcuts.ShortcutActions
import app.lanceur.folders.FolderEditor
import app.lanceur.folders.FolderSheet
import app.lanceur.home.HomeActions
import app.lanceur.home.PauseOverlay
import app.lanceur.home.RenameDialog
import app.lanceur.home.HomePager
import app.lanceur.home.HomeScreen
import app.lanceur.home.LauncherViewModel
import app.lanceur.home.ListMode
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import app.lanceur.home.Screen
import app.lanceur.lock.LockScreenService
import app.lanceur.news.NewsActions
import app.lanceur.news.NewsFeed
import app.lanceur.news.NewsFeedSheet
import app.lanceur.news.NewsImage
import app.lanceur.news.NewsPage
import app.lanceur.news.NewsState
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
    var canNotify by remember { mutableStateOf(container.updates.canNotify()) }
    var canInstall by remember { mutableStateOf(container.updates.installer.canInstall()) }
    var notificationAccess by remember { mutableStateOf(container.nowPlaying.hasAccess()) }
    var isDefault by remember { mutableStateOf(HomeRole.isHeld(context).also(roleWatcher::update)) }
    var permissionsGranted by remember { mutableStateOf(SearchPermissions.allGranted(context)) }
    var lockServiceEnabled by remember { mutableStateOf(LockScreenService.isEnabled(context)) }
    var summary by remember { mutableStateOf<DaySummaryState?>(null) }
    var widgetRefresh by remember { mutableIntStateOf(0) }
    var pickerQuery by remember { mutableStateOf("") }
    // Feuille de réglages d'un widget intégré : à l'ajout (`appWidgetId` nul) ou par ⚙
    var filing by remember { mutableStateOf<AppEntry?>(null) }
    var renaming by remember { mutableStateOf<AppEntry?>(null) }
    var pausing by remember { mutableStateOf<AppEntry?>(null) }
    val shortcuts = remember { app.lanceur.apps.AppShortcuts(context) }
    val badges by app.lanceur.builtin.media.NotificationBadges.apps.collectAsStateWithLifecycle()
    var editingFolder by remember { mutableStateOf<app.lanceur.folders.Folder?>(null) }
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
        canNotify = container.updates.canNotify()
        canInstall = container.updates.installer.canInstall()
        notificationAccess = container.nowPlaying.hasAccess()
        // Au plus toutes les 12 h, seulement si l'option est active
        container.appScope.launch { container.updates.check() }
        onPauseOrDispose { }
    }
    fun quickToast(message: String) = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    // Activation du double toucher suivie en direct (y compris juste après une mise à jour)
    DisposableEffect(Unit) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                lockServiceEnabled = LockScreenService.isEnabled(context)
            }
        }
        context.contentResolver.registerContentObserver(LockScreenService.SETTING_URI, false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val notifyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { canNotify = it }
    val backupFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { scope.launch { quickToast(if (container.backups.exportTo(it)) tr("Sauvegarde enregistrée", "Backup saved") else tr("Sauvegarde impossible", "Backup failed")) } }
    }
    var pendingRestore by remember { mutableStateOf<app.lanceur.prefs.BackupFile?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { scope.launch { container.backups.read(it)?.let { b -> pendingRestore = b } ?: quickToast(tr("Ce n'est pas une sauvegarde de Lanceur", "This is not a Lanceur backup")) } }
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            runCatching { container.backups.keepAccess(it) }
            scope.launch {
                container.prefsRepo.setBackupFolder(it.toString())
                container.prefsRepo.setBackupAuto(true)
                container.backups.writeToFolder(it.toString())
            }
        }
    }
    var checkingUpdates by remember { mutableStateOf(false) }
    var installingUpdate by remember { mutableStateOf(false) }
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
                BuiltinSlots.isBuiltin(slot) -> BuiltinSlots.kindOf(slot)?.label ?: tr("Widget Lanceur", "Lanceur widget")
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

    fun launchNow(entry: AppEntry) {
        if (!container.appLauncher.launch(entry.key)) {
            toast(tr("Appli introuvable", "App not found"))
            container.catalog.reload()
        }
    }

    /** Une appli marquée passe d'abord par la pause. */
    fun launch(entry: AppEntry) {
        if (entry.key in prefs.paused) pausing = entry else launchNow(entry)
    }

    fun openVault() {
        when {
            vm.vault.value is VaultState.Unlocked -> vm.show(Screen.VAULT)
            !authenticator.canAuthenticate() -> toast(tr("Configure un verrouillage d'écran pour utiliser le dossier caché", "Set up a screen lock to use the hidden folder"))
            vm.requestVault() -> authenticator.authenticate(tr("Dossier caché", "Hidden folder")) { ok ->
                vm.vaultEvent(if (ok) VaultEvent.AuthSucceeded else VaultEvent.AuthFailed)
            }
        }
    }

    fun openAccessibilitySettings() {
        container.appLauncher.startSafely(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    fun lockScreen() {
        // Activé mais pas encore relancé par Android (quelques secondes après une mise à jour) : on n'envoie pas
        // l'utilisateur dans les réglages pour rien
        if (!LockScreenService.lock() && !LockScreenService.isEnabled(context)) {
            toast(tr("Active « Lanceur » dans Accessibilité pour verrouiller d'un double toucher", "Turn on “Lanceur” in Accessibility to lock with a double tap"))
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
            AppMenuAction.FOLDER -> filing = entry
            AppMenuAction.REMOVE_FROM_FOLDER -> (mode as? ListMode.Folder)?.let { vm.setInFolder(it.id, entry.key, false) }
            AppMenuAction.RENAME -> renaming = entry
            AppMenuAction.PAUSE -> vm.setPaused(entry.key, true)
            AppMenuAction.UNPAUSE -> vm.setPaused(entry.key, false)
        }
    }

    fun openResult(result: SearchResult) {
        if (result == SearchResult.PermissionHint) {
            permissionLauncher.launch(SearchPermissions.ALL)
            return
        }
        if (result is SearchResult.App && result.entry.key in prefs.paused) {
            vm.show(Screen.HOME)
            pausing = result.entry
            return
        }
        val opened = container.resultActions.open(result)
        if (!opened && result is SearchResult.App) {
            toast(tr("Appli introuvable", "App not found"))
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

    // Recherche et Réglages : seul le fond d'écran reste derrière, pas l'accueil
    val homeAlpha by animateFloatAsState(if (screen == Screen.SEARCH || screen == Screen.SETTINGS) 0f else 1f, label = "homeAlpha")
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
            toggleTorch = { on -> if (!container.torch.set(on)) toast(tr("Lampe indisponible", "Flashlight unavailable")) },
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
        // Seconde barrière : seuls les liens web s'ouvrent depuis un widget
        openUrl = { url ->
            val uri = android.net.Uri.parse(url)
            if (uri.scheme == "https" || uri.scheme == "http") container.appLauncher.startSafely(Intent(Intent.ACTION_VIEW, uri))
        },
        rss = container.rss,
        refresh = widgetRefresh,
    )
    val pages = remember(prefs.pageOrder, prefs.widgetPageEnabled, prefs.newsEnabled) {
        PageLayout.active(prefs.pageOrder, prefs.widgetPageEnabled, prefs.newsEnabled)
    }
    val newsState = remember(prefs.news) { NewsState.decode(prefs.news) }
    var newsFilter by remember { mutableStateOf<String?>(null) }
    var newsRefreshing by remember { mutableStateOf(false) }
    var addingFeed by remember { mutableStateOf(false) }
    // Article ouvert en mode lecture (page Actualités)
    var reading by remember { mutableStateOf<app.lanceur.builtin.rss.Article?>(null) }
    val newsNow = rememberMinuteClock()
    fun refreshNews(force: Boolean) {
        scope.launch {
            newsRefreshing = force
            val refreshed = container.news.refresh(newsState, System.currentTimeMillis(), force)
            vm.updateNews { current -> NewsState.merge(NewsState.decode(current), refreshed).encode() }
            newsRefreshing = false
        }
    }
    LaunchedEffect(newsState.feeds.map { it.url }) { if (newsState.feeds.any { it.fetchedAt == null }) refreshNews(force = false) }
    // Icônes et casse des noms des applis, partout où une appli est listée
    CompositionLocalProvider(
        LocalAppLabelStyle provides prefs.appLabelStyle,
        app.lanceur.apps.LocalAppShortcuts provides shortcuts,
        app.lanceur.builtin.media.LocalBadges provides if (prefs.badges) badges else emptySet(),
    ) {
    Box(Modifier.fillMaxSize()) {
        // Seul le fond d'écran est visible pendant les quelques millisecondes du chargement
        if (loaded) HomePager(
            modifier = Modifier.graphicsLayer { alpha = homeAlpha },
            pages = pages,
            homePageRequests = homePageRequests,
            backEnabled = screen == Screen.HOME,
            editMode = widgetEditMode,
            onExitEdit = { vm.setWidgetEditMode(false) },
            onPageShown = { kind ->
                when (kind) {
                    PageKind.WIDGETS -> {
                        widgetRefresh++
                        reloadSummary()
                    }
                    PageKind.NEWS -> refreshNews(force = false)
                    PageKind.HOME -> Unit
                }
            },
            widgetPage = {
                WidgetPage(
                    summary = summary,
                    cards = widgetCards,
                    editMode = widgetEditMode,
                    label = { widgetLabels[it.appWidgetId] ?: tr("Widget", "Widget") },
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
            newsPage = {
                val filter = NewsFeed.validFilter(newsState, newsFilter)
                NewsPage(
                    chips = NewsFeed.chips(newsState),
                    filter = filter,
                    articles = NewsFeed.articles(newsState, filter),
                    footer = NewsFeed.footer(newsState, newsNow, java.time.ZoneId.systemDefault()),
                    unavailable = NewsFeed.allFailedEmpty(newsState),
                    refreshing = newsRefreshing,
                    now = newsNow,
                    actions = NewsActions(
                        filter = { newsFilter = it },
                        add = { addingFeed = true },
                        remove = { url -> vm.updateNews { NewsState.decode(it).remove(url).encode() } },
                        open = builtinServices.openUrl,
                        read = { reading = it },
                        refresh = { refreshNews(force = true) },
                    ),
                    image = { url, m -> NewsImage(url, container.images, m) },
                    dateLabel = NewsFeed.dateLabel(newsNow, java.time.ZoneId.systemDefault()),
                )
            },
            home = {
                HomeScreen(
                    lists = lists,
                    mode = mode,
                    side = prefs.alphabetSide,
                    icon = icon,
                    clockStyle = prefs.clock,
                    paused = prefs.paused,
                    actions = HomeActions(
                        stopFocus = vm::stopFocus,
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
                        editFolder = { editingFolder = it },
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
                pageOrder = prefs.pageOrder,
                widgetPageEnabled = prefs.widgetPageEnabled,
                newsEnabled = prefs.newsEnabled,
                icon = icon,
                focus = prefs.focus,
                focusActive = lists.focusActive,
                focusApps = lists.focusCandidates,
                backup = BackupState(prefs.backup.auto, container.backups.folderName(prefs.backup.folder), prefs.backup.last),
                updates = UpdatesState(container.updates.installed, prefs.updates.enabled, prefs.updates.latest, checkingUpdates, prefs.updates.lastCheck, canNotify, prefs.updates.autoInstall, canInstall, installingUpdate),
                now = newsNow,
                notificationAccess = notificationAccess,
                appearance = AppearanceState(
                    iconStyle = prefs.iconStyle,
                    labelStyle = prefs.appLabelStyle,
                    packs = remember { IconPacks.installed(context) },
                    wallpaperLabel = remember { Wallpaper(context).label() },
                    clock = prefs.clock,
                    badges = prefs.badges,
                    notificationAccess = notificationAccess,
                ),
                actions = SettingsActions(
                    openWallpaper = { container.appLauncher.startSafely(Wallpaper(context).intent()) },
                    setIconStyle = vm::setIconStyle,
                    setAppLabelStyle = vm::setAppLabelStyle,
                    setClockStyle = vm::setClockStyle,
                    setBadges = vm::setBadges,
                    grantNotificationAccess = builtinServices.grantMediaAccess,
                    allowNotifications = { notifyLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS) },
                    backupNow = { backupFileLauncher.launch(app.lanceur.prefs.Backup.fileName(java.time.LocalDateTime.now())) },
                    restoreBackup = { restoreLauncher.launch(arrayOf("application/json", "application/octet-stream", "text/plain")) },
                    chooseBackupFolder = { folderLauncher.launch(null) },
                    setBackupAuto = { on ->
                        if (on && prefs.backup.folder == null) folderLauncher.launch(null)
                        else scope.launch { container.prefsRepo.setBackupAuto(on) }
                    },
                    setUpdatesEnabled = { on ->
                        scope.launch { container.prefsRepo.updateUpdates { it.copy(enabled = on) } }
                        if (on && !canNotify) notifyLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    },
                    checkUpdatesNow = {
                        checkingUpdates = true
                        scope.launch {
                            val newer = container.updates.check(force = true)
                            checkingUpdates = false
                            toast(newer?.let { tr("Lanceur ${it.version} est disponible", "Lanceur ${it.version} is available") } ?: tr("Lanceur est à jour", "Lanceur is up to date"))
                        }
                    },
                    installUpdate = {
                        installingUpdate = true
                        scope.launch {
                            if (!container.updates.installer.canInstall()) container.appLauncher.startSafely(container.updates.unknownSourcesIntent())
                            else if (!container.updates.installNow()) quickToast(tr("Mise à jour impossible", "Update failed"))
                            installingUpdate = false
                        }
                    },
                    setAutoInstall = { on ->
                        scope.launch { container.prefsRepo.updateUpdates { it.copy(autoInstall = on) } }
                        if (on && !container.updates.installer.canInstall()) container.appLauncher.startSafely(container.updates.unknownSourcesIntent())
                    },
                    allowInstalls = { container.appLauncher.startSafely(container.updates.unknownSourcesIntent()) },
                    openReleases = { builtinServices.openUrl(app.lanceur.update.UpdateCheck.RELEASES_PAGE) },
                    focus = app.lanceur.focus.FocusActions(
                        start = vm::startFocus,
                        stop = vm::stopFocus,
                        update = vm::updateFocus,
                        grantNotifications = builtinServices.grantMediaAccess,
                    ),
                    findIconPacks = {
                        container.appLauncher.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=icon%20pack&c=apps")))
                    },
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
                    openLanguage = {
                        container.appLauncher.startSafely(
                            Intent(android.provider.Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    },
                    setWidgetPageEnabled = vm::setWidgetPageEnabled,
                    setNewsEnabled = vm::setNewsEnabled,
                    movePage = vm::movePage,
                ),
            )
        }
        AnimatedVisibility(visible = reading != null, enter = fadeIn(), exit = fadeOut()) {
            // Garde l'article pendant le fondu de sortie
            val article = remember { reading } ?: return@AnimatedVisibility
            val current = reading ?: article
            ArticleWebView(
                url = current.link,
                source = current.source,
                onClose = { reading = null },
                onOpenInBrowser = builtinServices.openUrl,
                modifier = Modifier.blockTouchesBelow(),
            )
        }
        pendingRestore?.let { backup ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { pendingRestore = null },
                title = { Text(tr("Restaurer cette sauvegarde ?", "Restore this backup?")) },
                text = {
                    val date = java.time.format.DateTimeFormatter.ofPattern(tr("d MMMM yyyy 'à' HH:mm", "MMMM d, yyyy 'at' HH:mm"), app.lanceur.i18n.L10n.locale)
                        .format(java.time.Instant.ofEpochMilli(backup.createdAt).atZone(ZoneId.systemDefault()))
                    Text(tr("Sauvegarde du $date. Tes réglages actuels seront remplacés.", "Backup from $date. Your current settings will be replaced."))
                },
                confirmButton = {
                    androidx.compose.material3.Button(onClick = {
                        pendingRestore = null
                        scope.launch {
                            container.backups.restore(backup)
                            toast(tr("Réglages restaurés", "Settings restored"))
                        }
                    }) { Text(tr("Restaurer", "Restore")) }
                },
                dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) { Text(tr("Annuler", "Cancel")) } },
            )
        }
        if (addingFeed) {
            NewsFeedSheet(
                existing = newsState.feeds.mapTo(HashSet()) { it.url },
                check = { container.rss.check(it) },
                onAdd = { url, title ->
                    // La feuille reste ouverte : on peut ajouter plusieurs flux du catalogue d'affilée
                    vm.updateNews { NewsState.decode(it).add(url, title).encode() }
                },
                onDismiss = { addingFeed = false },
                mine = newsState.feeds.map { it.url to it.title },
                onRemoveFeeds = { urls -> vm.updateNews { urls.fold(NewsState.decode(it)) { state, url -> state.remove(url) }.encode() } },
            )
        }
        filing?.let { entry ->
            FolderSheet(
                appLabel = entry.label,
                folders = prefs.folders,
                contains = { entry.key in it.apps },
                onToggle = { folder, inFolder -> vm.setInFolder(folder.id, entry.key, inFolder) },
                onCreate = { name, icon -> vm.createFolder(name, icon, entry.key) },
                onDismiss = { filing = null },
            )
        }
        renaming?.let { entry ->
            val original = container.catalog.apps.value?.firstOrNull { it.key == entry.key }?.label ?: entry.label
            RenameDialog(
                current = entry.label,
                original = original,
                onSave = { name -> vm.setLabel(entry.key, name.takeIf { it.trim() != original }); renaming = null },
                onReset = { vm.setLabel(entry.key, null); renaming = null },
                onDismiss = { renaming = null },
            )
        }
        pausing?.let { entry ->
            PauseOverlay(
                entry = entry,
                icon = icon,
                onOpen = { pausing = null; launchNow(entry) },
                onCancel = { pausing = null },
            )
        }
        editingFolder?.let { folder ->
            FolderEditor(
                folder = folder,
                onSave = { name, icon -> vm.editFolder(folder.id, name, icon); editingFolder = null },
                onDelete = { vm.deleteFolder(folder.id); editingFolder = null },
                onDismiss = { editingFolder = null },
                apps = lists.folders.firstOrNull { it.folder.id == folder.id }?.apps.orEmpty(),
                appIcon = icon,
                onReorder = { vm.setFolderOrder(folder.id, it) },
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
                    checkFeed = { container.rss.check(it) },
                ),
            )
        }
    }
    }
}
