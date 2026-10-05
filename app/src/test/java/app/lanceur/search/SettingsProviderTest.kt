package app.lanceur.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsProviderTest {
    private fun labels(query: String) = SettingsProvider.match(query).map { it.label }

    @Test
    fun finds_settings_by_name_or_keyword() {
        assertEquals(listOf("Wi-Fi"), labels("wi"))
        assertEquals(listOf("Affichage"), labels("lumi"))
        assertEquals(listOf("Batterie"), labels("batt"))
        assertTrue("Paramètres" in labels("parametres"))
    }

    @Test
    fun ignores_one_letter_queries_and_caps_results() {
        assertTrue(labels("w").isEmpty())
        assertTrue(labels("re").size <= 3)
    }
}
