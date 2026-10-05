package app.lanceur.apps

import android.content.pm.LauncherApps
import android.os.Process
import android.os.UserHandle
import android.os.UserManager

/** Type d'un profil ; partagé par la liste des applis et celle des widgets. */
object ProfileKinds {
    fun of(launcherApps: LauncherApps, user: UserHandle, me: UserHandle = Process.myUserHandle()): ProfileKind {
        if (user == me) return ProfileKind.MAIN
        return try {
            when (launcherApps.getLauncherUserInfo(user)?.userType) {
                null -> ProfileKind.UNKNOWN
                UserManager.USER_TYPE_PROFILE_PRIVATE -> ProfileKind.PRIVATE
                else -> ProfileKind.OTHER
            }
        } catch (e: SecurityException) {
            ProfileKind.UNKNOWN
        }
    }
}
