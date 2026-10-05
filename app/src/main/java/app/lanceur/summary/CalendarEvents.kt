package app.lanceur.summary

import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances

/** Occurrences d'événements entre `begin` et `end` (ms), triées par début. Hors du fil principal, permission accordée. */
object CalendarEvents {
    fun query(context: Context, begin: Long, end: Long): List<SummaryEvent> {
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
