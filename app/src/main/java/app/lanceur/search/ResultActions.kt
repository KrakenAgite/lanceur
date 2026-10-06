package app.lanceur.search

import app.lanceur.i18n.tr
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.Settings
import app.lanceur.apps.AppLauncher

/** Transforme un résultat en action Android. Renvoie `false` si rien n'a pu être ouvert. */
class ResultActions(private val context: Context, private val launcher: AppLauncher) {
    fun open(result: SearchResult): Boolean = when (result) {
        is SearchResult.App -> launcher.launch(result.entry.key)
        is SearchResult.Calc -> {
            // Android affiche lui-même la confirmation « copié »
            context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(tr("Calcul", "Calculation"), result.value))
            true
        }
        is SearchResult.Contact -> launcher.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(result.lookupUri)))
        is SearchResult.Event -> launcher.startSafely(
            Intent(Intent.ACTION_VIEW, ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, result.eventId))
                .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, result.begin)
                .putExtra(CalendarContract.EXTRA_EVENT_END_TIME, result.end),
        )
        is SearchResult.Setting ->
            launcher.startSafely(Intent(result.action)) || launcher.startSafely(Intent(Settings.ACTION_SETTINGS))
        is SearchResult.Web ->
            launcher.startSafely(Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, result.query)) ||
                launcher.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(result.query))))
        SearchResult.PermissionHint -> false
    }

    fun call(phone: String) {
        launcher.startSafely(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", phone, null)))
    }

    fun sms(phone: String) {
        launcher.startSafely(Intent(Intent.ACTION_SENDTO, Uri.fromParts("smsto", phone, null)))
    }
}
