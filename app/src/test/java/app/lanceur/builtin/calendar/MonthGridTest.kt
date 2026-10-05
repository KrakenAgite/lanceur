package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthGridTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val today = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int, month: Int = 10) = LocalDateTime.of(2026, month, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private fun utcDay(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun timed(id: Long, title: String, day: Int, hour: Int, color: Int? = null) =
        SummaryEvent(id, title, at(day, hour), at(day, hour + 1), false, color)
    private fun allDay(id: Long, title: String, from: Int, toExclusive: Int) =
        SummaryEvent(id, title, utcDay(from), utcDay(toExclusive), true)

    @Test
    fun october_2026_starts_on_thursday_with_monday_first_weeks() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, emptyList(), paris)
        assertEquals("octobre 2026", grid.title)
        assertEquals(5, grid.weeks.size)
        assertEquals(LocalDate.of(2026, 9, 28), grid.weeks.first().first().date)
        assertEquals(3, grid.weeks.first().count { !it.inMonth })
        assertEquals(LocalDate.of(2026, 11, 1), grid.weeks.last().last().date)
        assertTrue(grid.weeks.flatten().single { it.isToday }.date == today)
    }

    @Test
    fun august_2026_needs_six_weeks() {
        assertEquals(6, MonthGrid.build(YearMonth.of(2026, 8), today, emptyList(), paris).weeks.size)
    }

    @Test
    fun at_most_three_distinct_colors_per_day() {
        val events = listOf(1, 2, 3, 4, 1).mapIndexed { i, c -> timed(i.toLong(), "E$i", 14, 8 + i, color = c) }
        val day = MonthGrid.build(YearMonth.of(2026, 10), today, events, paris).weeks.flatten().first { it.date.dayOfMonth == 14 && it.inMonth }
        assertEquals(listOf<Int?>(1, 2, 3), day.colors)
    }

    @Test
    fun all_day_events_use_utc_dates() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Férié", 12, 13)), paris)
        val marked = grid.weeks.flatten().filter { it.colors.isNotEmpty() }.map { it.date.dayOfMonth }
        assertEquals(listOf(12), marked)
    }

    @Test
    fun multi_day_event_marks_each_day() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Vacances", 19, 22)), paris)
        assertEquals(listOf(19, 20, 21), grid.weeks.flatten().filter { it.colors.isNotEmpty() }.map { it.date.dayOfMonth })
    }

    @Test
    fun birthday_is_a_gift_not_a_dot() {
        val grid = MonthGrid.build(YearMonth.of(2026, 10), today, listOf(allDay(1, "Léa - Anniversaire", 17, 18)), paris)
        val day = grid.weeks.flatten().first { it.date.dayOfMonth == 17 && it.inMonth }
        assertTrue(day.birthday)
        assertTrue(day.colors.isEmpty())
        assertFalse(grid.weeks.flatten().first { it.date.dayOfMonth == 16 && it.inMonth }.birthday)
    }

    @Test
    fun day_lines_put_birthdays_first_then_all_day_then_by_time() {
        val events = listOf(timed(1, "Sport", 14, 18), allDay(2, "Férié", 14, 15), timed(3, "Dentiste", 14, 9), allDay(4, "Léa - Anniversaire", 14, 15))
        val lines = MonthGrid.dayLines(LocalDate.of(2026, 10, 14), events, paris)
        assertEquals(listOf("Léa", "Férié", "Dentiste", "Sport"), lines.map { it.title })
        assertEquals(listOf("🎁", "Journée", "09:00", "18:00"), lines.map { it.timeLabel })
    }
}
