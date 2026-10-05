package app.lanceur.builtin.contacts

import org.junit.Assert.assertEquals
import org.junit.Test

class FavoriteContactTest {
    private fun contact(name: String) = FavoriteContact("uri", name, null, null)

    @Test
    fun first_name_and_initial() {
        assertEquals("Jean-Côme", contact("Jean-Côme Chopin").firstName)
        assertEquals("Maman", contact("Maman").firstName)
        assertEquals("É", contact("élodie Martin").initial)
        assertEquals("?", contact("  ").initial)
        assertEquals("?", contact("  ").firstName)
    }
}
