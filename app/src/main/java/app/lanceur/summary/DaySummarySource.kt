package app.lanceur.summary

import android.Manifest
import android.app.AlarmManager
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import app.lanceur.search.SearchPermissions
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Événements d'aujourd'hui et de demain, et alarme suivante (sans autorisation). */
class DaySummarySource(private val context: Context) {
    suspend fun load(now: ZonedDateTime = ZonedDateTime.now()): DaySummaryState = withContext(Dispatchers.IO) {
        val granted = SearchPermissions.granted(context, Manifest.permission.READ_CALENDAR)
        val events = if (granted) runCatching { queryEvents(now) }.getOrDefault(emptyList()) else emptyList()
        val alarm = context.getSystemService(AlarmManager::class.java).nextAlarmClock?.triggerTime
        DaySummary.build(now, events, alarm, granted)
    }

    private fun queryEvents(now: ZonedDateTime): List<SummaryEvent> {
        // Fenêtre élargie d'un jour de chaque côté : les événements « toute la journée » sont en UTC ; DaySummary trie
        val begin = now.minusDays(1).toInstant().toEpochMilli()
        val end = now.toLocalDate().plusDays(3).atStartOfDay(now.zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, end) }
            .build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR)
        val events = mutableListOf<SummaryEvent>()
        context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                events += SummaryEvent(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = cursor.getLong(2),
                    end = cursor.getLong(3),
                    allDay = cursor.getInt(4) == 1,
                    color = if (cursor.isNull(5)) null else cursor.getInt(5),
                )
            }
        }
        return events
    }
}
