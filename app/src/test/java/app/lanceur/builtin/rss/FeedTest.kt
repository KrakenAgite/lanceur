package app.lanceur.builtin.rss

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedTest {
    private val rss = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:dc="http://purl.org/dc/elements/1.1/"><channel><title>Le Monde</title>
          <item><title><![CDATA[Élections : <b>les résultats</b> &amp; analyses]]></title><link>https://lemonde.fr/a</link>
            <pubDate>Mon, 05 Oct 2026 12:00:00 +0200</pubDate></item>
          <item><title>L&#39;économie</title><link>https://lemonde.fr/b</link><dc:date>2026-10-05T11:00:00Z</dc:date></item>
          <item><title>Sans date</title><link>https://lemonde.fr/c</link></item>
        </channel></rss>
    """.trimIndent().toByteArray()

    private val atom = """
        <?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom"><title>Korben</title>
          <entry><title>Astuce Linux</title><link rel="alternate" href="https://korben.info/x"/><updated>2026-10-05T13:30:00+02:00</updated></entry>
          <entry><title>Doublon</title><link href="https://lemonde.fr/a"/><published>2026-10-04T08:00:00Z</published></entry>
        </feed>
    """.trimIndent().toByteArray()

    @Test
    fun rss_items_are_cleaned_and_dated() {
        val feed = Feed.parse(rss, "secours")!!
        assertEquals("Le Monde", feed.title)
        assertEquals(listOf("Élections : les résultats & analyses", "L'économie", "Sans date"), feed.articles.map { it.title })
        assertEquals(java.time.Instant.parse("2026-10-05T10:00:00Z").toEpochMilli(), feed.articles[0].published)
        assertEquals(java.time.Instant.parse("2026-10-05T11:00:00Z").toEpochMilli(), feed.articles[1].published)
        assertNull(feed.articles[2].published)
        assertEquals("Le Monde", feed.articles[0].source)
    }

    @Test
    fun atom_entries() {
        val feed = Feed.parse(atom, "secours")!!
        assertEquals("Korben", feed.title)
        assertEquals("https://korben.info/x", feed.articles[0].link)
        assertEquals(java.time.Instant.parse("2026-10-05T11:30:00Z").toEpochMilli(), feed.articles[0].published)
    }

    @Test
    fun merge_sorts_newest_first_dedups_and_limits() {
        val merged = Feed.merge(listOf(Feed.parse(rss, "")!!, Feed.parse(atom, "")!!), limit = 3)
        assertEquals(listOf("Astuce Linux", "L'économie", "Élections : les résultats & analyses"), merged.map { it.title })
        val all = Feed.merge(listOf(Feed.parse(rss, "")!!, Feed.parse(atom, "")!!), limit = 10)
        assertEquals(4, all.size)
        assertEquals("Sans date", all.last().title)
    }

    @Test
    fun not_a_feed_and_doctype_do_not_crash() {
        assertNull(Feed.parse("<html><body>coucou</body></html>".toByteArray(), "x"))
        assertNull(Feed.parse("pas du xml".toByteArray(), "x"))
    }

    @Test
    fun doctype_does_not_crash() {
        val evil = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><title>&x;</title></channel></rss>"""
        val result = runCatching { Feed.parse(evil.toByteArray(), "x") }
        assertTrue(result.isSuccess)
        assertTrue(result.getOrNull()?.title?.contains("root") != true)
    }

    @Test
    fun config_cache_and_validation() {
        assertEquals("Adresse HTTPS requise", RssConfig.validate("http://example.org/feed"))
        assertNull(RssConfig.validate("https://example.org/feed"))
        val config = RssConfig(listOf("https://www.lemonde.fr/rss/une.xml"))
        val cache = RssCache(Feed.parse(rss, "")!!.articles, fetchedAt = 42)
        val data = RssData.encode(config, cache)
        assertEquals(config, RssData.config(data))
        assertEquals(cache, RssData.cache(data))
        assertTrue(RssSuggestions.all.all { it.url.startsWith("https://") })
        assertEquals(RssViewState.Loading, RssView.state(null, failed = false))
        assertEquals(RssViewState.Unavailable, RssView.state(null, failed = true))
    }
}
