package app.lanceur.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckTest {
    @Test
    fun the_latest_release_is_read_from_github() {
        val json = """{"tag_name":"v1.5.0","name":"Lanceur 1.5.0","html_url":"https://github.com/KrakenAgite/lanceur/releases/tag/v1.5.0","draft":false,"prerelease":false}"""
        assertEquals(Release("1.5.0", "https://github.com/KrakenAgite/lanceur/releases/tag/v1.5.0"), UpdateCheck.parse(json))
        assertNull(UpdateCheck.parse("""{"message":"Not Found"}"""))
        assertNull(UpdateCheck.parse("pas du json"))
        assertNull(UpdateCheck.parse(json.replace("\"draft\":false", "\"draft\":true")))
    }

    @Test
    fun versions_compare_number_by_number() {
        assertTrue(UpdateCheck.isNewer("1.5.0", "1.4.0"))
        assertTrue(UpdateCheck.isNewer("1.10.0", "1.9.3"))
        assertTrue(UpdateCheck.isNewer("2.0", "1.9.9"))
        assertFalse(UpdateCheck.isNewer("1.4.0", "1.4.0"))
        assertFalse(UpdateCheck.isNewer("1.3.9", "1.4.0"))
        assertFalse(UpdateCheck.isNewer("abc", "1.4.0"))
    }

    @Test
    fun checks_at_most_every_twelve_hours() {
        assertTrue(UpdateCheck.isDue(lastCheck = null, now = 0L))
        assertFalse(UpdateCheck.isDue(lastCheck = 0L, now = 11 * 3_600_000L))
        assertTrue(UpdateCheck.isDue(lastCheck = 0L, now = 12 * 3_600_000L))
    }
}
