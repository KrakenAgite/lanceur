package app.lanceur.news

import app.lanceur.builtin.rss.Article
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsStateTest {
    private val lemonde = "https://www.lemonde.fr/rss/une.xml"
    private val korben = "https://korben.info/feed"
    private fun art(title: String, link: String, at: Long?, image: String? = null) = Article(title, link, "", at, image)

    private val state = NewsState(
        listOf(
            FeedSnapshot(lemonde, "Le Monde", listOf(art("A", "https://l/a", 300, "https://i/a.jpg"), art("B", "https://l/b", 100)), fetchedAt = 1_000, failed = false),
            FeedSnapshot(korben, "Korben", listOf(art("K", "https://k/k", 200), art("Dup", "https://l/a", 50), art("Sans date", "https://k/s", null)), fetchedAt = 2_000, failed = true),
        ),
    )

    @Test
    fun articles_merge_sort_dedup_filter_and_take_the_feed_name() {
        assertEquals(listOf("A", "K", "B", "Sans date"), NewsFeed.articles(state, null).map { it.title })
        assertEquals("Le Monde", NewsFeed.articles(state, null).first().source)
        assertEquals("https://i/a.jpg", NewsFeed.articles(state, null).first().image)
        assertEquals(listOf("K", "Dup", "Sans date"), NewsFeed.articles(state, korben).map { it.title })
        val many = NewsState(listOf(FeedSnapshot(lemonde, "LM", (1..40).map { art("$it", "https://l/$it", it.toLong()) })))
        assertEquals(NewsFeed.PER_FEED, NewsFeed.articles(many, null).size)
    }

    @Test
    fun chips_disambiguate_same_names_and_flag_failures() {
        assertEquals(listOf(NewsChip(lemonde, "Le Monde", false), NewsChip(korben, "Korben", true)), NewsFeed.chips(state))
        val twins = NewsState(listOf(FeedSnapshot("https://a.fr/rss", "Actu"), FeedSnapshot("https://www.b.com/rss", "Actu")))
        assertEquals(listOf("Actu · a.fr", "Actu · b.com"), NewsFeed.chips(twins).map { it.label })
    }

    @Test
    fun add_remove_and_filter_reset() {
        val added = state.add("https://x.fr/rss", "X").add(lemonde, "doublon")
        assertEquals(3, added.feeds.size)
        val removed = added.remove(korben)
        assertEquals(listOf(lemonde, "https://x.fr/rss"), removed.feeds.map { it.url })
        assertNull(NewsFeed.validFilter(removed, korben))
        assertEquals(lemonde, NewsFeed.validFilter(removed, lemonde))
    }

    @Test
    fun encode_round_trip() {
        assertEquals(state, NewsState.decode(state.encode()))
        assertEquals(NewsState(), NewsState.decode(null))
        assertEquals(NewsState(), NewsState.decode("n'importe quoi"))
    }

    @Test
    fun merge_keeps_feeds_added_or_removed_meanwhile() {
        val refreshed = NewsState(state.feeds.map { it.copy(fetchedAt = 9_000, failed = false) })
        val current = state.remove(korben).add("https://x.fr/rss", "X")
        val merged = NewsState.merge(current, refreshed)
        assertEquals(listOf(lemonde, "https://x.fr/rss"), merged.feeds.map { it.url })
        assertEquals(9_000L, merged.feeds.first().fetchedAt)
        assertNull(merged.feeds.last().fetchedAt)
    }

    @Test
    fun footer_texts() {
        val paris = ZoneId.of("Europe/Paris")
        val at = LocalDateTime.of(2026, 10, 6, 14, 5).atZone(paris).toInstant().toEpochMilli()
        val ok = NewsState(listOf(FeedSnapshot(lemonde, "LM", fetchedAt = at)))
        assertEquals("Mis à jour à 14:05", NewsFeed.footer(ok, at + 60_000, paris))
        val offline = NewsState(listOf(FeedSnapshot(lemonde, "LM", fetchedAt = at, failed = true)))
        assertEquals("Hors ligne · il y a 2 h", NewsFeed.footer(offline, at + 2 * 3_600_000, paris))
        assertNull(NewsFeed.footer(NewsState(), at, paris))
        assertTrue(NewsFeed.allFailedEmpty(NewsState(listOf(FeedSnapshot(lemonde, "LM", failed = true)))))
    }
}
