package app.lanceur.search

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.provider.CalendarContract.Instances
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Événements de maintenant à J+90 ; le filtrage sans accents est fait ici, pas en SQL. */
class CalendarProvider(private val context: Context, private val limit: Int = 5) : SearchProvider {
    override val delayMs = 80L

    override suspend fun search(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        if (!SearchPermissions.granted(context, Manifest.permission.READ_CALENDAR)) return@withContext emptyList<SearchResult>()
        val begin = System.currentTimeMillis()
        val uri = Instances.CONTENT_URI.buildUpon()
            .also { ContentUris.appendId(it, begin); ContentUris.appendId(it, begin + WINDOW_MS) }
            .build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.EVENT_LOCATION)
        val results = mutableListOf<SearchResult>()
        context.contentResolver.query(uri, projection, null, null, "${Instances.BEGIN} ASC")?.use { cursor ->
            while (results.size < limit && cursor.moveToNext()) {
                val title = cursor.getString(1)
                val location = cursor.getString(5)
                if (EventMatcher.matches(title, location, query)) {
                    results += SearchResult.Event(
                        eventId = cursor.getLong(0),
                        title = title.orEmpty(),
                        begin = cursor.getLong(2),
                        end = cursor.getLong(3),
                        allDay = cursor.getInt(4) == 1,
                        location = location?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
        results
    }

    private companion object {
        const val WINDOW_MS = 90L * 24 * 60 * 60 * 1000
    }
}
