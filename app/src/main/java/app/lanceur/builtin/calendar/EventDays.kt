package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

internal object EventDays {
    private const val MAX_DAYS = 62L

    /** Jours couverts. Les journées entières sont enregistrées en UTC par Android : les lire dans le fuseau décalerait d'un jour. */
    fun of(event: SummaryEvent, zone: ZoneId): List<LocalDate> {
        val z = if (event.allDay) ZoneOffset.UTC else zone
        val first = Instant.ofEpochMilli(event.begin).atZone(z).toLocalDate()
        val lastMillis = if (event.end > event.begin) event.end - 1 else event.begin
        val last = Instant.ofEpochMilli(lastMillis).atZone(z).toLocalDate()
        return generateSequence(first) { it.plusDays(1) }.takeWhile { !it.isAfter(last) }.take(MAX_DAYS.toInt()).toList()
    }

    fun byDay(events: List<SummaryEvent>, zone: ZoneId): Map<LocalDate, List<SummaryEvent>> {
        val out = HashMap<LocalDate, MutableList<SummaryEvent>>()
        events.forEach { event -> of(event, zone).forEach { out.getOrPut(it) { mutableListOf() } += event } }
        return out
    }
}
