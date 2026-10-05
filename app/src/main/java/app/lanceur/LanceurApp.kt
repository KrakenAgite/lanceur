package app.lanceur

import android.app.Application

class LanceurApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.catalog.start()
        container.cleanUpWidgetIds()
    }
}
