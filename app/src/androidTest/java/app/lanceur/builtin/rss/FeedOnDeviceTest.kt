package app.lanceur.builtin.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Le parseur XML d'Android n'est pas celui des tests JVM : on vérifie le refus des entités sur l'appareil. */
class FeedOnDeviceTest {
    @Test
    fun billion_laughs_is_refused_on_android() {
        val lol = (1..9).joinToString("") { i -> "<!ENTITY lol$i \"${"&lol${i - 1};".repeat(10)}\">" }
        val evil = """<?xml version="1.0"?><!DOCTYPE lolz [<!ENTITY lol0 "lol">$lol]><rss><channel><title>&lol9;</title></channel></rss>"""
        assertNull(Feed.parse(evil.toByteArray(), "x"))
    }

    @Test
    fun a_normal_feed_still_parses_on_android() {
        val feed = """<?xml version="1.0" encoding="UTF-8"?><rss><channel><title>Le Monde</title>
            <item><title>A</title><link>https://lemonde.fr/a</link></item></channel></rss>"""
        assertEquals(listOf("A"), Feed.parse(feed.toByteArray(), "x")!!.articles.map { it.title })
    }
}
