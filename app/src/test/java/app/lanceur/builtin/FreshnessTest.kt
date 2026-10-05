package app.lanceur.builtin

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FreshnessTest {
    private val paris = ZoneId.of("Europe/Paris")
    private val t0 = LocalDateTime.of(2026, 10, 5, 14, 5).atZone(paris).toInstant().toEpochMilli()
    private val min = 60_000L

    @Test
    fun stale_after_thirty_minutes() {
        assertFalse(Freshness.isStale(t0, t0 + 29 * min))
        assertTrue(Freshness.isStale(t0, t0 + 31 * min))
        assertTrue(Freshness.isStale(null, t0))
    }

    @Test
    fun labels() {
        assertEquals("Mis à jour à 14:05", Freshness.label(t0, t0 + 20 * min, paris))
        assertEquals("Mis à jour il y a 2 h", Freshness.label(t0, t0 + 125 * min, paris))
        assertEquals("Mis à jour il y a 3 j", Freshness.label(t0, t0 + 3 * 24 * 60 * min, paris))
        assertEquals("à l'instant", Freshness.ago(t0, t0 + 30_000))
        assertEquals("il y a 25 min", Freshness.ago(t0, t0 + 25 * min))
        assertEquals("il y a 3 h", Freshness.ago(t0, t0 + 190 * min))
        assertEquals("il y a 2 j", Freshness.ago(t0, t0 + 2 * 24 * 60 * min))
    }
}
