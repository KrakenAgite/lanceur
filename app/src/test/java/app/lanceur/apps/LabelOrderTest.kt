package app.lanceur.apps

import app.lanceur.app
import org.junit.Assert.assertEquals
import org.junit.Test

class LabelOrderTest {
    @Test
    fun sorts_ignoring_case_and_accents() {
        val sorted = listOf(app("zoom"), app("Éditeur"), app("agenda"), app("Banque")).sortedWith(LabelOrder)
        assertEquals(listOf("agenda", "Banque", "Éditeur", "zoom"), sorted.map { it.label })
    }

    @Test
    fun same_label_in_two_profiles_has_a_stable_order() {
        val personal = app("Chrome", serial = 0)
        val work = app("Chrome", serial = 10)
        assertEquals(listOf(personal, work), listOf(work, personal).sortedWith(LabelOrder))
        assertEquals(listOf(personal, work), listOf(personal, work).sortedWith(LabelOrder))
    }
}
