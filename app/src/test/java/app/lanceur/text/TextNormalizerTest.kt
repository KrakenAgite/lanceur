package app.lanceur.text

import org.junit.Assert.assertEquals
import org.junit.Test

class TextNormalizerTest {
    @Test
    fun removes_accents_and_uppercases() {
        assertEquals("EDITEUR", TextNormalizer.fold("Éditeur"))
        assertEquals("CAFE NOEL", TextNormalizer.fold("café noël"))
    }

    @Test
    fun expands_ligatures() {
        assertEquals("OEUVRES", TextNormalizer.fold("Œuvres"))
        assertEquals("AEGIS", TextNormalizer.fold("ægis"))
    }

    @Test
    fun keeps_digits_and_symbols() {
        assertEquals("1PASSWORD", TextNormalizer.fold("1Password"))
        assertEquals("€URO", TextNormalizer.fold("€uro"))
    }

    @Test
    fun empty_stays_empty() {
        assertEquals("", TextNormalizer.fold(""))
    }
}
