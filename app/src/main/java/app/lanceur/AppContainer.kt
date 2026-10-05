package app.lanceur

import android.content.Context
import android.util.Log
import app.lanceur.apps.AppEntry
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

/** Un seul exemplaire de chaque service pour toute l'app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val iconLoader = IconLoader(appContext)
    val catalog = AppCatalog(
        scope = appScope,
        source = LauncherAppsSource(appContext),
        onPackageChanged = iconLoader::evict,
        onError = { Log.w("Lanceur", "Lecture des applis impossible", it) },
    )
    // Même fichier que l'ancien `preferencesDataStore(name = "lanceur")` : les réglages déjà enregistrés sont conservés
    val prefsRepo = PrefsRepo(
        store = LauncherDataStore.create { appContext.preferencesDataStoreFile("lanceur") },
        onError = { Log.w("Lanceur", "Réglages illisibles ou impossibles à enregistrer", it) },
    )
    val appLauncher = AppLauncher(appContext)
    val resultActions = ResultActions(appContext, appLauncher)

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
