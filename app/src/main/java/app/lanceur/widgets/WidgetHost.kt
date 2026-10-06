package app.lanceur.widgets

import app.lanceur.i18n.tr
import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import app.lanceur.apps.AppKey

/** Hôte Android des widgets de Lanceur (identifiant d'hôte fixe : les widgets survivent aux redémarrages). */
class WidgetHost(context: Context) {
    private val appContext = context.applicationContext
    private val manager = AppWidgetManager.getInstance(appContext)
    private val host = AppWidgetHost(appContext, HOST_ID)
    private val userManager = appContext.getSystemService(UserManager::class.java)

    fun startListening() {
        runCatching { host.startListening() }.onFailure { Log.w(TAG, "Écoute des widgets impossible", it) }
    }

    fun stopListening() {
        runCatching { host.stopListening() }.onFailure { Log.w(TAG, "Arrêt de l'écoute des widgets impossible", it) }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        runCatching { host.deleteAppWidgetId(id) }.onFailure { Log.w(TAG, "Libération du widget $id impossible", it) }
    }

    fun hostIds(): IntArray = runCatching { host.appWidgetIds }.getOrDefault(IntArray(0))

    fun info(id: Int): AppWidgetProviderInfo? = runCatching { manager.getAppWidgetInfo(id) }.getOrNull()

    fun availableIds(slots: List<WidgetSlot>): Set<Int> = slots.mapNotNullTo(HashSet()) { slot ->
        slot.appWidgetId.takeIf { info(it) != null }
    }

    fun label(id: Int): String = info(id)?.loadLabel(appContext.packageManager) ?: tr("Widget", "Widget")

    /** `true` si Android a lié le widget sans rien demander ; sinon il faut lancer [bindIntent]. */
    fun bindIfAllowed(id: Int, provider: AppKey): Boolean {
        val user = user(provider) ?: return false
        return runCatching { manager.bindAppWidgetIdIfAllowed(id, user, provider.component(), null) }.getOrDefault(false)
    }

    fun bindIntent(id: Int, provider: AppKey): Intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider.component())
        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, user(provider))

    fun needsConfiguration(id: Int): Boolean {
        val info = info(id) ?: return false
        return info.configure != null &&
            (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0
    }

    fun isReconfigurable(id: Int): Boolean {
        val info = info(id) ?: return false
        return info.configure != null && (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0
    }

    /** `false` si l'écran de configuration n'a pas pu s'ouvrir. */
    fun startConfiguration(activity: Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: RuntimeException) {
        Log.w(TAG, "Configuration du widget $id impossible", e)
        false
    }

    fun createView(context: Context, id: Int): AppWidgetHostView? {
        val info = info(id) ?: return null
        return runCatching { host.createView(context, id, info) }.getOrNull()
    }

    private fun user(provider: AppKey): UserHandle? = userManager.getUserForSerialNumber(provider.userSerial)

    private fun AppKey.component() = ComponentName(packageName, className)

    private companion object {
        const val HOST_ID = 1024
        const val TAG = "Lanceur"
    }
}
