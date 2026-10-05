package app.lanceur.search

import app.lanceur.app
import app.lanceur.apps.LabelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSearchProviderTest {
    private val apps = listOf(app("Gmail"), app("Google Maps"), app("Maps"), app("Éditeur"), app("Bloc-notes"))
        .sortedWith(LabelOrder)

    @Test
    fun name_prefix_then_word_prefix_then_contains() {
        assertEquals(listOf("Maps", "Google Maps", "Gmail"), AppSearchProvider.rank(apps, "ma", 8).map { it.label })
    }

    @Test
    fun ignores_accents_and_case() {
        assertEquals(listOf("Éditeur"), AppSearchProvider.rank(apps, "EDIT", 8).map { it.label })
        assertEquals(listOf("Bloc-notes"), AppSearchProvider.rank(apps, "notes", 8).map { it.label })
    }

    @Test
    fun respects_the_limit_and_ignores_a_blank_query() {
        assertEquals(2, AppSearchProvider.rank(apps, "a", 2).size)
        assertTrue(AppSearchProvider.rank(apps, "  ", 8).isEmpty())
    }
}
