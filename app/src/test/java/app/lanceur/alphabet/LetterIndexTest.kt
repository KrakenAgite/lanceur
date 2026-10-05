package app.lanceur.alphabet

import app.lanceur.app
import app.lanceur.apps.LabelOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterIndexTest {
    @Test
    fun letters_are_A_to_Z_then_hash() {
        assertEquals(27, LetterIndex.LETTERS.size)
        assertEquals('A', LetterIndex.LETTERS.first())
        assertEquals('#', LetterIndex.LETTERS.last())
    }

    @Test
    fun letter_of_folds_accents_ligatures_and_symbols() {
        assertEquals('E', LetterIndex.letterOf("Éditeur"))
        assertEquals('O', LetterIndex.letterOf("Œuvres"))
        assertEquals('Z', LetterIndex.letterOf("  zoom"))
        assertEquals('#', LetterIndex.letterOf("1Password"))
        assertEquals('#', LetterIndex.letterOf("🎵 Musique"))
        assertEquals('#', LetterIndex.letterOf(""))
        assertEquals('#', LetterIndex.letterOf("   "))
    }

    @Test
    fun build_groups_apps_and_marks_empty_letters() {
        val apps = listOf(app("Appareil photo"), app("1Password"), app("Éditeur"), app("Agenda")).sortedWith(LabelOrder)
        val sections = LetterIndex.build(apps)
        assertEquals(27, sections.size)
        assertEquals(listOf("Agenda", "Appareil photo"), sections[0].apps.map { it.label })
        assertTrue(sections[1].isEmpty)
        assertEquals(listOf("Éditeur"), sections[4].apps.map { it.label })
        assertEquals(listOf("1Password"), sections[26].apps.map { it.label })
    }
}
