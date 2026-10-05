package app.lanceur.builtin.clocks

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockTest {
    private val paris = ZoneId.of("Europe/Paris")
    private fun city(id: String) = Cities.byId(id)!!
    private val noon = Instant.parse("2026-10-05T12:00:00Z")

    @Test
    fun offsets_seen_from_paris() {
        assertEquals("+7 h", WorldClock.of(city("tokyo"), noon, paris).offset)
        assertEquals("21:00", WorldClock.of(city("tokyo"), noon, paris).time)
        assertEquals("−6 h", WorldClock.of(city("new-york"), noon, paris).offset)
        assertEquals("+3 h 30", WorldClock.of(city("new-delhi"), noon, paris).offset)
        assertEquals("même heure", WorldClock.of(city("paris"), noon, paris).offset)
    }

    @Test
    fun relative_day_and_daylight() {
        val evening = Instant.parse("2026-10-05T20:00:00Z")
        val tokyo = WorldClock.of(city("tokyo"), evening, paris)
        assertEquals("demain", tokyo.dayHint)
        assertFalse(tokyo.isDay)
        assertEquals("hier", WorldClock.of(city("honolulu"), Instant.parse("2026-10-05T05:00:00Z"), paris).dayHint)
        assertNull(WorldClock.of(city("tokyo"), noon, paris).dayHint)
        assertTrue(WorldClock.of(city("paris"), noon, paris).isDay)
    }

    @Test
    fun cities_search_without_accents_and_home() {
        assertEquals("São Paulo", Cities.search("sao").first().name)
        assertEquals("Montréal", Cities.search("MONTREAL").single().name)
        assertEquals(Cities.all, Cities.search(""))
        assertEquals(Cities.all.size, Cities.all.map { it.id }.toSet().size)
        assertEquals("paris", Cities.home(paris, noon).id)
        assertEquals("paris", Cities.home(ZoneId.of("Europe/Andorra"), noon).id)
    }

    @Test
    fun config_round_trip() {
        val config = WorldClocksConfig(listOf("paris", "tokyo"))
        assertEquals(config, WorldClocksConfig.fromData(config.toData()))
        assertEquals(WorldClocksConfig(emptyList()), WorldClocksConfig.fromData(null))
    }
}
