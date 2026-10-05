package app.lanceur.builtin.calendar

import app.lanceur.summary.SummaryEvent
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekStripTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val monday = LocalDate.of(2026, 10, 5)
    private fun at(day: Int, hour: Int) = LocalDateTime.of(2026, 10, day, hour, 0).atZone(paris).toInstant().toEpochMilli()
    private fun timed(id: Long, title: String, day: Int, hour: Int) = SummaryEvent(id, title, at(day, hour), at(day, hour + 1), false, 7)
    private fun utcDay(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun week_starts_on_monday() {
        assertEquals(monday, WeekStrip.weekStart(LocalDate.of(2026, 10, 8)))
        assertEquals(monday, WeekStrip.weekStart(monday))
        assertEquals(monday, WeekStrip.weekStart(LocalDate.of(2026, 10, 11)))
    }

    @Test
    fun seven_days_with_labels_today_and_title() {
        val strip = WeekStrip.build(monday, LocalDate.of(2026, 10, 7), emptyList(), paris, perDay = 2)
        assertEquals(listOf("L", "M", "M", "J", "V", "S", "D"), strip.days.map { it.label })
        assertEquals(listOf(5, 6, 7, 8, 9, 10, 11), strip.days.map { it.date.dayOfMonth })
        assertTrue(strip.days[2].isToday)
        assertEquals("5 – 11 octobre", strip.title)
        assertEquals("28 sept. – 4 oct.", WeekStrip.build(LocalDate.of(2026, 9, 28), monday, emptyList(), paris, 2).title)
    }

    @Test
    fun extra_events_become_plus_n_and_birthdays_go_to_the_header() {
        val events = listOf(timed(1, "C", 6, 15), timed(2, "A", 6, 8), timed(3, "B", 6, 10), SummaryEvent(4, "Léa - Anniversaire", utcDay(6), utcDay(7), true))
        val tuesday = WeekStrip.build(monday, monday, events, paris, perDay = 2).days[1]
        assertEquals(listOf("A", "B"), tuesday.items.map { it.title })
        assertEquals(1, tuesday.more)
        assertEquals(listOf("Léa"), tuesday.birthdays)
    }

    @Test
    fun events_per_day_follow_the_size() {
        assertEquals(listOf(2, 4, 6), WidgetSize.entries.map(WeekStrip::perDay))
    }
}
