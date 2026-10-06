package app.lanceur.builtin.rss

import org.junit.Assert.assertEquals
import org.junit.Test

class FeedImageTest {
    private fun images(items: String) = Feed.parse(
        """<rss xmlns:media="http://search.yahoo.com/mrss/" xmlns:content="http://purl.org/rss/1.0/modules/content/"><channel><title>T</title>$items</channel></rss>""".toByteArray(),
        "",
    )!!.articles.map { it.image }

    @Test
    fun media_enclosure_and_html_images() {
        val result = images(
            """
            <item><title>A</title><link>https://s.fr/a</link><media:content url="https://img.fr/a.jpg" medium="image"/></item>
            <item><title>B</title><link>https://s.fr/b</link><media:thumbnail url="https://img.fr/b.jpg"/></item>
            <item><title>C</title><link>https://s.fr/c</link><enclosure url="https://img.fr/c.png" type="image/png"/></item>
            <item><title>D</title><link>https://s.fr/d</link><description>&lt;p&gt;&lt;img src="https://img.fr/d.webp" /&gt;Texte&lt;/p&gt;</description></item>
            <item><title>E</title><link>https://s.fr/e</link><enclosure url="https://s.fr/e.mp3" type="audio/mpeg"/></item>
            """,
        )
        assertEquals(listOf("https://img.fr/a.jpg", "https://img.fr/b.jpg", "https://img.fr/c.png", "https://img.fr/d.webp", null), result)
    }

    @Test
    fun relative_images_are_resolved_and_http_refused() {
        val result = images(
            """
            <item><title>A</title><link>https://s.fr/2026/a.html</link><content:encoded><![CDATA[<img src="/img/a.jpg">]]></content:encoded></item>
            <item><title>B</title><link>https://s.fr/b</link><media:content url="http://img.fr/b.jpg" medium="image"/></item>
            """,
        )
        assertEquals(listOf("https://s.fr/img/a.jpg", null), result)
    }

    @Test
    fun atom_entries_find_their_image() {
        val feed = """<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/"><title>K</title>
            <entry><title>A</title><link href="https://k.fr/a"/><media:thumbnail url="https://k.fr/a.jpg"/></entry>
            <entry><title>B</title><link href="https://k.fr/b"/><content type="html">&lt;img src="https://k.fr/b.jpg"&gt;</content></entry></feed>"""
        assertEquals(listOf("https://k.fr/a.jpg", "https://k.fr/b.jpg"), Feed.parse(feed.toByteArray(), "")!!.articles.map { it.image })
    }
}
