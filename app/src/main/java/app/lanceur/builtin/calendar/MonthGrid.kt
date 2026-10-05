package app.lanceur.builtin.calendar

import app.lanceur.summary.DaySummary
import app.lanceur.summary.SummaryEvent
import app.lanceur.summary.SummaryLine
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** `colors` : couleurs d'agenda distinctes (`null` = couleur du thème), au plus 3 ; un anniversaire donne 🎁 à la place. */
data class GridDay(val date: LocalDate, val inMonth: Boolean, val isToday: Boolean, val colors: List<Int?>, val birthday: Boolean)

data class MonthGridState(val month: YearMonth, val title: String, val weeks: List<List<GridDay>>)

object MonthGrid {
    const val MAX_DOTS = 3
    private val TITLE = DateTimeFormatter.ofPattern("LLLL yyyy", Locale.FRENCH)

    fun build(month: YearMonth, today: LocalDate, events: List<SummaryEvent>, zone: ZoneId): MonthGridState {
        val byDay = EventDays.byDay(events, zone)
        val start = month.atDay(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val end = month.atEndOfMonth().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        val days = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.map { date ->
            val (birthdays, others) = byDay[date].orEmpty().partition { DaySummary.isBirthday(it.title, it.allDay) }
            GridDay(
                date = date,
                inMonth = YearMonth.from(date) == month,
                isToday = date == today,
                colors = others.map { it.color }.distinct().take(MAX_DOTS),
                birthday = birthdays.isNotEmpty(),
            )
        }.toList()
        return MonthGridState(month, title(month), days.chunked(7))
    }

    /** « octobre 2026 » (minuscule, à la française). */
    fun title(month: YearMonth): String = TITLE.format(month)

    /** Chronologie d'un jour : anniversaires, puis journées entières, puis par heure. */
    fun dayLines(day: LocalDate, events: List<SummaryEvent>, zone: ZoneId): List<SummaryLine> =
        events.filter { day in EventDays.of(it, zone) }
            .map { DaySummary.lineFor(it, zone) }
            .sortedWith(compareByDescending<SummaryLine> { it.birthday }.thenByDescending { it.event.allDay }.thenBy { it.event.begin })
}
