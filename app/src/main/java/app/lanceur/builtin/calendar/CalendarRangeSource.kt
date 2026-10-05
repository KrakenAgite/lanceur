package app.lanceur.builtin.calendar

import android.Manifest
import android.content.Context
import app.lanceur.search.SearchPermissions
import app.lanceur.summary.CalendarEvents
import app.lanceur.summary.SummaryEvent
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class CalendarLoad(val granted: Boolean, val events: List<SummaryEvent>)

class CalendarRangeSource(private val context: Context) {
    /** Événements touchant [begin, endExclusive[. Un jour de marge de chaque côté : les journées entières sont en UTC. */
    suspend fun load(begin: LocalDate, endExclusive: LocalDate, zone: ZoneId = ZoneId.systemDefault()): CalendarLoad =
        withContext(Dispatchers.IO) {
            if (!SearchPermissions.granted(context, Manifest.permission.READ_CALENDAR)) return@withContext CalendarLoad(false, emptyList())
            val from = begin.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val to = endExclusive.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            CalendarLoad(true, runCatching { CalendarEvents.query(context, from, to) }.getOrDefault(emptyList()))
        }
}
