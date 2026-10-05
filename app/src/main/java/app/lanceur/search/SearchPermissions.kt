package app.lanceur.search

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager

object SearchPermissions {
    val ALL = arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.READ_CALENDAR)

    fun granted(context: Context, permission: String): Boolean =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    fun allGranted(context: Context): Boolean = ALL.all { granted(context, it) }
}
