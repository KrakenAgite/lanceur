package app.lanceur.builtin.note

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteDataTest {
    @Test
    fun text_round_trip_and_empty_by_default() {
        val text = "Courses :\n- pain = 2\n- lait"
        assertEquals(text, NoteData.text(NoteData.of(text)))
        assertEquals("", NoteData.text(null))
        assertEquals("", NoteData.text("n'importe quoi"))
    }
}
