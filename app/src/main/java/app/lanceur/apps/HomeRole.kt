package app.lanceur.apps

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent

object HomeRole {
    fun isHeld(context: Context): Boolean =
        context.getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_HOME)

    fun requestIntent(context: Context): Intent =
        context.getSystemService(RoleManager::class.java).createRequestRoleIntent(RoleManager.ROLE_HOME)
}
