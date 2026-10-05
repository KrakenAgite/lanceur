package app.lanceur.builtin.countdown

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountdownTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0).atZone(paris)
    private fun config(day: Int, time: LocalTime? = null, month: Int = 10) =
        CountdownConfig("Vacances", LocalDate.of(2026, month, day), time, createdAt = 0)

    @Test
    fun days_left_today_and_past() {
        assertEquals("J-12", Countdown.of(config(17), now).big)
        assertEquals("12 j 4 h", Countdown.of(config(17, LocalTime.of(14, 0)), now).big)
        assertEquals("4 h 30 min", Countdown.of(config(5, LocalTime.of(14, 30)), now).big)
        assertEquals("C'est aujourd'hui 🎉", Countdown.of(config(5), now).big)
        assertEquals("C'est aujourd'hui 🎉", Countdown.of(config(5, LocalTime.of(8, 0)), now).big)
        assertEquals("Il y a 1 jour", Countdown.of(config(4), now).big)
        assertEquals("Il y a 3 jours", Countdown.of(config(2), now).big)
    }

    @Test
    fun date_text_and_progress() {
        assertEquals("Samedi 17 octobre 2026", Countdown.of(config(17), now).dateText)
        assertEquals("Samedi 17 octobre 2026 à 14:00", Countdown.of(config(17, LocalTime.of(14, 0)), now).dateText)
        val created = LocalDateTime.of(2026, 10, 1, 0, 0).atZone(paris).toInstant().toEpochMilli()
        val progress = Countdown.of(config(9).copy(createdAt = created), now).progress
        assertEquals(0.552f, progress, 0.01f)
        assertEquals(1f, Countdown.of(config(2).copy(createdAt = created), now).progress, 0f)
    }

    @Test
    fun data_round_trip_and_garbage() {
        val c = config(17, LocalTime.of(14, 0)).copy(createdAt = 42)
        assertEquals(c, CountdownConfig.fromData(c.toData()))
        assertEquals(config(17), CountdownConfig.fromData(config(17).toData()))
        assertNull(CountdownConfig.fromData(null))
        assertNull(CountdownConfig.fromData("date=pas une date"))
    }
}
