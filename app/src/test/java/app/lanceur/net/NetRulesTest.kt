package app.lanceur.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetRulesTest {
    @Test
    fun only_https_is_allowed() {
        assertTrue(NetRules.allowed("https://www.lemonde.fr/rss/une.xml"))
        assertTrue(NetRules.allowed("HTTPS://EXAMPLE.ORG"))
        assertFalse(NetRules.allowed("http://example.org/feed"))
        assertFalse(NetRules.allowed("file:///etc/hosts"))
        assertFalse(NetRules.allowed("pas une adresse"))
    }

    @Test
    fun relative_redirects_are_resolved() {
        assertEquals("https://example.org/b/feed", NetRules.next("https://example.org/a/feed", "/b/feed"))
        assertEquals("https://other.org/x", NetRules.next("https://example.org/a", "https://other.org/x"))
    }

    @Test
    fun redirect_to_http_is_refused() {
        assertNull(NetRules.next("https://example.org/a", "http://example.org/a"))
        assertNull(NetRules.next("https://example.org/a", null))
    }
}
