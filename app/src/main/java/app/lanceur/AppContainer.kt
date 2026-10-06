package app.lanceur

import android.content.Context
import android.util.Log
import app.lanceur.apps.AppEntry
import app.lanceur.builtin.battery.BatterySource
import app.lanceur.builtin.calendar.CalendarRangeSource
import app.lanceur.builtin.contacts.FavoriteContactsSource
import app.lanceur.builtin.media.NowPlayingSource
import app.lanceur.builtin.rss.RssSource
import app.lanceur.builtin.shortcuts.TorchController
import app.lanceur.builtin.storage.StorageSource
import app.lanceur.builtin.weather.WeatherSource
import app.lanceur.net.Network
import app.lanceur.news.ImageLoader
import app.lanceur.news.NewsSource
import app.lanceur.search.AppSearchProvider
import app.lanceur.search.CalcProvider
import app.lanceur.search.CalendarProvider
import app.lanceur.search.ContactsProvider
import app.lanceur.search.PermissionHintProvider
import app.lanceur.search.ResultActions
import app.lanceur.search.SearchEngine
import app.lanceur.search.SettingsProvider
import app.lanceur.search.WebProvider
import app.lanceur.apps.AppCatalog
import app.lanceur.apps.AppLauncher
import app.lanceur.apps.IconLoader
import app.lanceur.apps.LauncherAppsSource
import app.lanceur.prefs.PrefsRepo
import androidx.datastore.preferences.preferencesDataStoreFile
import app.lanceur.prefs.LauncherDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import app.lanceur.summary.DaySummarySource
import app.lanceur.widgets.WidgetHost
import app.lanceur.widgets.WidgetIds
import app.lanceur.widgets.WidgetProviderSource
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Un seul exemplaire de chaque service pour toute l'app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val iconLoader = IconLoader(appContext)
    val catalog = AppCatalog(
        scope = appScope,
        source = LauncherAppsSource(appContext),
        onPackageChanged = { pkg ->
            iconLoader.evict(pkg)
            appScope.launch { iconLoader.packChanged(pkg) }
        },
        onError = { Log.w("Lanceur", "Lecture des applis impossible", it) },
    )
    // Même fichier que l'ancien `preferencesDataStore(name = "lanceur")` : les réglages déjà enregistrés sont conservés
    val prefsRepo = PrefsRepo(
        store = LauncherDataStore.create { appContext.preferencesDataStoreFile("lanceur") },
        onError = { Log.w("Lanceur", "Réglages illisibles ou impossibles à enregistrer", it) },
    )
    val appLauncher = AppLauncher(appContext)
    val resultActions = ResultActions(appContext, appLauncher)
    val widgetHost = WidgetHost(appContext)
    val widgetProviders = WidgetProviderSource(appContext)
    val daySummary = DaySummarySource(appContext)
    val favoriteContacts = FavoriteContactsSource(appContext)
    val torch = TorchController(appContext)
    val storage = StorageSource(appContext)
    val network = Network()
    val weather = WeatherSource(appContext, network)
    val rss = RssSource(network)
    val news = NewsSource(network)
    val images = ImageLoader(appContext, network)
    val battery = BatterySource(appContext)
    val nowPlaying = NowPlayingSource(appContext)
    val calendarRange = CalendarRangeSource(appContext)

    /** Les icônes suivent le style enregistré (pack, forme, thématisées). */
    fun watchIconStyle() {
        appScope.launch { prefsRepo.prefs.map { it.iconStyle }.distinctUntilChanged().collect { iconLoader.setStyle(it) } }
    }

    /** Libère les identifiants réservés par un ajout interrompu (Lanceur tué pendant la configuration). */
    fun cleanUpWidgetIds() {
        appScope.launch {
            // Réglages illisibles : surtout ne rien supprimer (on perdrait tous les widgets)
            val slots = prefsRepo.readOrNull()?.widgets ?: return@launch
            WidgetIds.orphans(widgetHost.hostIds(), slots).forEach(widgetHost::deleteId)
        }
    }

    /** L'ordre de la liste est l'ordre d'affichage des résultats. */
    fun searchEngine(visibleApps: () -> List<AppEntry>, showPermissionHint: () -> Boolean) = SearchEngine(
        providers = listOf(
            AppSearchProvider(visibleApps),
            CalcProvider,
            ContactsProvider(appContext),
            CalendarProvider(appContext),
            SettingsProvider,
            PermissionHintProvider(showPermissionHint),
            WebProvider,
        ),
        onError = { Log.w("Lanceur", "Source de recherche en erreur", it) },
    )
}
