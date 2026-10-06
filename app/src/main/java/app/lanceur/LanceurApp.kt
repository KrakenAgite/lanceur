package app.lanceur

import android.app.Application
import android.content.res.Configuration
import app.lanceur.i18n.L10n

class LanceurApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        L10n.apply(resources.configuration.locales[0])
        container = AppContainer(this)
        container.catalog.start()
        container.cleanUpWidgetIds()
        container.watchIconStyle()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        L10n.apply(newConfig.locales[0])
    }
}
