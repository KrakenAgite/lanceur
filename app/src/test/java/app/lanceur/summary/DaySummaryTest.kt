package app.lanceur.summary

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DaySummaryTest {
    private val paris = ZoneId.of("Europe/Paris")

    // Lundi 5 octobre 2026, 10:00 à Paris
    private val now = ZonedDateTime.of(2026, 10, 5, 10, 0, 0, 0, paris)

    private fun at(day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, paris).toInstant().toEpochMilli()

    private fun utcMidnight(day: Int) = LocalDate.of(2026, 10, day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    private fun timed(id: Long, title: String, day: Int, hour: Int, minute: Int = 0, hours: Int = 1) =
        SummaryEvent(id, title, at(day, hour, minute), at(day, hour + hours, minute), allDay = false)

    private fun allDay(id: Long, title: String, day: Int) = SummaryEvent(id, title, utcMidnight(day), utcMidnight(day + 1), allDay = true)

    @Test
    fun keeps_three_upcoming_events_of_today_and_tomorrow() {
        val events = listOf(
            timed(1, "Petit-déjeuner", 5, 8), // terminé
            timed(2, "Dentiste", 5, 14),
            timed(3, "Train", 6, 9, 30),
            allDay(4, "Anniversaire", 6),
            timed(5, "Sport", 5, 18),
            timed(6, "Plus tard", 7, 9), // après-demain
        )
        val summary = DaySummary.build(now, events, nextAlarmMillis = null, calendarGranted = true)
        assertEquals(listOf("14:00 Dentiste", "18:00 Sport", "Demain Anniversaire"), summary.events.map { it.text })
        assertEquals(listOf(2L, 5L, 4L), summary.events.map { it.event.eventId })
    }

    @Test
    fun an_ongoing_event_is_still_shown() {
        val summary = DaySummary.build(now, listOf(timed(1, "Réunion", 5, 9, hours = 2)), null, true)
        assertEquals(listOf("09:00 Réunion"), summary.events.map { it.text })
    }

    @Test
    fun today_all_day_event_has_no_time_and_untitled_events_get_a_title() {
        val summary = DaySummary.build(now, listOf(allDay(1, "Férié", 5), timed(2, " ", 5, 15)), null, true)
        assertEquals(listOf("Férié", "15:00 (Sans titre)"), summary.events.map { it.text })
    }

    @Test
    fun date_and_alarm_labels() {
        assertEquals("Lundi 5 octobre", DaySummary.build(now, emptyList(), null, true).dateLabel)
        assertEquals("Aujourd'hui 22:00", DaySummary.alarmLabel(at(5, 22), now))
        assertEquals("Demain 07:00", DaySummary.alarmLabel(at(6, 7), now))
        assertEquals("lun. 12 oct. 07:00", DaySummary.alarmLabel(at(12, 7), now))
        assertNull(DaySummary.build(now, emptyList(), null, true).alarmLabel)
    }

    @Test
    fun without_calendar_permission_no_event_is_listed() {
        val summary = DaySummary.build(now, listOf(timed(2, "Dentiste", 5, 14)), at(6, 7), calendarGranted = false)
        assertFalse(summary.calendarGranted)
        assertTrue(summary.events.isEmpty())
        assertEquals("Demain 07:00", summary.alarmLabel)
    }
}
