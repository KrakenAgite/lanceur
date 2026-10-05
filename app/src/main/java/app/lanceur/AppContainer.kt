package app.lanceur

import android.content.Context
import app.lanceur.apps.AppCatalog
import app.lanceur.apps.AppLauncher
import app.lanceur.apps.IconLoader
import app.lanceur.prefs.PrefsRepo
import app.lanceur.prefs.launcherDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Un seul exemplaire de chaque service pour toute l'app. */
class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val iconLoader = IconLoader(appContext)
    val catalog = AppCatalog(appContext, appScope, onPackageChanged = iconLoader::evict)
    val prefsRepo = PrefsRepo(appContext.launcherDataStore)
    val appLauncher = AppLauncher(appContext)
}
