package app.lanceur.lock

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager

/**
 * Seul moyen pour une appli de mettre l'écran en veille sans bloquer l'empreinte au déverrouillage.
 * Le service ne reçoit aucun événement et ne lit rien de ce qui s'affiche (voir `res/xml/lock_service.xml`).
 */
class LockScreenService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onUnbind(intent: Intent?): Boolean {
        instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    companion object {
        /** Le réglage à surveiller pour suivre l'activation sans attendre un retour sur l'accueil. */
        val SETTING_URI: android.net.Uri = android.provider.Settings.Secure.getUriFor(android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)

        @Volatile
        private var instance: LockScreenService? = null

        /** `false` si le service n'est pas activé dans les paramètres d'accessibilité. */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        /**
         * Le choix enregistré dans Android compte, même si le service n'est pas encore relancé (juste après une
         * mise à jour de Lanceur, il n'apparaît pas tout de suite parmi les services en marche).
         */
        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LockScreenService::class.java)
            val saved = runCatching {
                android.provider.Settings.Secure.getString(context.contentResolver, android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            }.getOrNull()
            if (LockServiceSetting.isEnabledIn(saved, me.packageName, me.className)) return true
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> s.packageName == me.packageName && s.name == me.className } }
        }
    }
}
