package app.lanceur.news

import app.lanceur.builtin.rss.RssConfig
import app.lanceur.i18n.L10n
import app.lanceur.i18n.Lang
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FeedCatalogTest {
    @After fun backToFrench() { L10n.lang = Lang.FR }

    @Test
    fun catalog_is_large_https_and_without_duplicates() {
        assertTrue(FeedCatalog.all.size >= 120)
        assertTrue(FeedCatalog.all.all { RssConfig.validate(it.url) == null })
        assertEquals(FeedCatalog.all.size, FeedCatalog.all.map { it.url }.distinct().size)
    }

    @Test
    fun every_theme_has_feeds_in_every_region() {
        FeedRegion.entries.forEach { region ->
            assertEquals(FeedTheme.entries, FeedCatalog.themes(region))
        }
    }

    @Test
    fun franceinfo_offers_many_themes() {
        val themes = FeedCatalog.all.filter { it.source == "franceinfo" }.map { it.theme }.toSet()
        assertTrue(themes.size >= 8)
    }

    @Test
    fun search_ignores_accents_and_case_and_combines_words() {
        assertTrue(FeedCatalog.search("foot").any { it.source == "L'Équipe" })
        assertTrue(FeedCatalog.search("foot").any { it.source == "BBC Sport" })
        assertTrue(FeedCatalog.search("ECONOMIE franceinfo").all { it.source == "franceinfo" && it.theme == FeedTheme.ECONOMY })
        assertTrue(FeedCatalog.search("  ").isEmpty())
    }

    @Test
    fun region_and_labels_follow_the_app_language() {
        assertEquals(FeedRegion.FR, FeedCatalog.defaultRegion())
        assertEquals("franceinfo · Économie", FeedCatalog.feeds(FeedRegion.FR, FeedTheme.ECONOMY).first { it.source == "franceinfo" && it.name == "Économie" }.label)
        assertEquals("Korben", FeedCatalog.all.first { it.source == "Korben" }.label)
        L10n.lang = Lang.EN
        assertEquals(FeedRegion.UK, FeedCatalog.defaultRegion())
        assertEquals("Business", FeedTheme.ECONOMY.label)
    }
}
