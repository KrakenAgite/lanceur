package app.lanceur.search

import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ResultSectionsTest {
    private fun app(name: String) = SearchResult.App(AppEntry(AppKey("p.$name", "p.$name.Main", 0), name, false))
    private val event = SearchResult.Event(1, "RDV", 0, 0, false, null)

    @Test
    fun groups_consecutive_results_by_kind_in_arrival_order() {
        val sections = ResultSections.of(
            listOf(app("Banque"), app("Bandcamp"), SearchResult.Calc("1+1", "2"), event, SearchResult.Web("ba")),
        )
        assertEquals(listOf("Applis", "Calcul", "Agenda", "Web"), sections.map { it.title })
        assertEquals(2, sections[0].items.size)
    }

    @Test
    fun the_permission_hint_is_its_own_section_without_title() {
        val sections = ResultSections.of(listOf(SearchResult.PermissionHint, SearchResult.Web("x")))
        assertNull(sections[0].title)
        assertEquals(listOf(SearchResult.PermissionHint), sections[0].items)
    }

    @Test
    fun same_kind_split_by_another_kind_stays_merged_in_first_position() {
        val sections = ResultSections.of(listOf(app("A"), SearchResult.Web("a"), app("B")))
        assertEquals(listOf("Applis", "Web"), sections.map { it.title })
        assertEquals(2, sections[0].items.size)
    }

    @Test
    fun section_keys_are_stable_and_unique() {
        val keys = ResultSections.of(listOf(app("A"), SearchResult.Contact("u", "Ana", null), SearchResult.Setting("s", "Wi-Fi"))).map { it.key }
        assertEquals(keys.toSet().size, keys.size)
        assertTrue(ResultSections.of(emptyList()).isEmpty())
    }
}
