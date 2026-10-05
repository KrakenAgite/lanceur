package app.lanceur.search

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventMatcherTest {
    @Test
    fun matches_title_or_location_without_accents() {
        assertTrue(EventMatcher.matches("Réunion équipe", null, "reunion"))
        assertTrue(EventMatcher.matches(null, "Café de Flore", "flore"))
    }

    @Test
    fun rejects_non_matching_or_blank() {
        assertFalse(EventMatcher.matches("Dentiste", "Paris", "lyon"))
        assertFalse(EventMatcher.matches(null, null, "a"))
        assertFalse(EventMatcher.matches("Dentiste", null, "  "))
    }
}
