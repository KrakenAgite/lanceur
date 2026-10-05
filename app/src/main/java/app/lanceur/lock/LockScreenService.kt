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
        @Volatile
        private var instance: LockScreenService? = null

        /** `false` si le service n'est pas activé dans les paramètres d'accessibilité. */
        fun lock(): Boolean = instance?.performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN) ?: false

        fun isEnabled(context: Context): Boolean {
            val me = ComponentName(context, LockScreenService::class.java)
            return context.getSystemService(AccessibilityManager::class.java)
                .getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                .any { it.resolveInfo.serviceInfo.let { s -> s.packageName == me.packageName && s.name == me.className } }
        }
    }
}
