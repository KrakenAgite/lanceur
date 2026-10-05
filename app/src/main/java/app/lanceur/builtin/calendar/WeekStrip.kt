package app.lanceur.builtin.calendar

import app.lanceur.summary.DaySummary
import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

data class WeekItem(val event: SummaryEvent, val title: String, val color: Int?)

/** `birthdays` : noms courts, affichés avec 🎁 en tête de colonne ; `more` : événements qui ne tiennent pas. */
data class WeekDay(
    val date: LocalDate,
    val label: String,
    val isToday: Boolean,
    val birthdays: List<String>,
    val items: List<WeekItem>,
    val more: Int,
)

data class WeekStripState(val title: String, val days: List<WeekDay>)

object WeekStrip {
    private val LABELS = listOf("L", "M", "M", "J", "V", "S", "D")
    private val DAY_MONTH = DateTimeFormatter.ofPattern("d MMMM", Locale.FRENCH)
    private val SHORT = DateTimeFormatter.ofPattern("d MMM", Locale.FRENCH)

    fun weekStart(date: LocalDate): LocalDate = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    fun perDay(size: WidgetSize): Int = when (size) {
        WidgetSize.SMALL -> 2
        WidgetSize.MEDIUM -> 4
        WidgetSize.LARGE -> 6
    }

    fun build(weekStart: LocalDate, today: LocalDate, events: List<SummaryEvent>, zone: ZoneId, perDay: Int): WeekStripState {
        val byDay = EventDays.byDay(events, zone)
        val days = (0L until 7L).map { offset ->
            val date = weekStart.plusDays(offset)
            val (birthdays, others) = byDay[date].orEmpty().partition { DaySummary.isBirthday(it.title, it.allDay) }
            val sorted = others.sortedWith(compareByDescending<SummaryEvent> { it.allDay }.thenBy { it.begin })
            WeekDay(
                date = date,
                label = LABELS[offset.toInt()],
                isToday = date == today,
                birthdays = birthdays.map { DaySummary.shortBirthdayTitle(it.title) },
                items = sorted.take(perDay).map { WeekItem(it, it.title.ifBlank { "(Sans titre)" }, it.color) },
                more = (sorted.size - perDay).coerceAtLeast(0),
            )
        }
        val end = weekStart.plusDays(6)
        val title = if (end.month == weekStart.month) "${weekStart.dayOfMonth} – ${DAY_MONTH.format(end)}"
        else "${SHORT.format(weekStart)} – ${SHORT.format(end)}"
        return WeekStripState(title, days)
    }
}
