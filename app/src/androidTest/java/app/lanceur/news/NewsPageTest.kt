package app.lanceur.news

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import app.lanceur.builtin.rss.Article
import app.lanceur.builtin.rss.FeedCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class NewsPageTest {
    @get:Rule val rule = createComposeRule()
    private val now = 10_000_000L
    private val chips = listOf(NewsChip("https://lm/rss", "Le Monde", false), NewsChip("https://k/feed", "Korben", true))
    private val articles = listOf(
        Article("Élections : les résultats", "https://lm/a", "Le Monde", now - 25 * 60_000, image = "https://img/a.jpg"),
        Article("Astuce Linux", "https://k/x", "Korben", now - 3 * 3_600_000),
    )

    private fun show(actions: NewsActions, chips: List<NewsChip> = this.chips, articles: List<Article> = this.articles, unavailable: Boolean = false) =
        rule.setContent {
            MaterialTheme {
                NewsPage(chips, filter = null, articles = articles, footer = "Mis à jour à 14:05", unavailable = unavailable, refreshing = false, now = now, actions = actions, image = { url, m -> androidx.compose.foundation.layout.Box(m) { androidx.compose.material3.Text("IMAGE $url") } })
            }
        }

    @Test
    fun bubbles_filter_and_articles_open() {
        var filter: String? = "?"
        var opened: String? = null
        show(NewsActions(filter = { filter = it }, open = { opened = it }))
        rule.onNodeWithText("Korben ⚠").performClick()
        assertEquals("https://k/feed", filter)
        rule.onNodeWithText("Tout").performClick()
        assertEquals(null, filter)
        rule.onNodeWithText("IMAGE https://img/a.jpg").assertIsDisplayed()
        rule.onNodeWithText("LE MONDE").assertIsDisplayed()
        // La deuxième carte est sous la première (grande image) : on fait défiler la liste jusqu'à elle
        rule.onNodeWithTag("news-list").performScrollToNode(androidx.compose.ui.test.hasText("Astuce Linux"))
        rule.onNodeWithText("Astuce Linux").performClick()
        assertEquals("https://k/x", opened)
    }

    @Test
    fun long_press_removes_and_plus_adds() {
        var removed: String? = null
        var added = false
        show(NewsActions(remove = { removed = it }, add = { added = true }))
        rule.onNodeWithText("Le Monde").performTouchInput { longClick() }
        rule.onNodeWithText("Retirer ce flux").performClick()
        assertEquals("https://lm/rss", removed)
        rule.onNodeWithTag("news-add").performClick()
        assertTrue(added)
    }

    @Test
    fun empty_and_unavailable_states() {
        var added = false
        show(NewsActions(add = { added = true }), chips = emptyList(), articles = emptyList())
        rule.onNodeWithText("Ajoute tes premiers flux").performClick()
        assertTrue(added)
    }

    @Test
    fun unavailable_offers_retry() {
        var refreshed = false
        show(NewsActions(refresh = { refreshed = true }), articles = emptyList(), unavailable = true)
        rule.onNodeWithText("Réessayer").performClick()
        assertTrue(refreshed)
    }

    @Test
    fun catalog_filters_by_language_and_theme_then_adds_with_its_label() {
        val added = mutableListOf<Pair<String, String>>()
        rule.setContent { MaterialTheme { NewsFeedForm(existing = setOf("https://feeds.bbci.co.uk/news/business/rss.xml"), check = { FeedCheck.Ok("Long feed title", 20) }, onAdd = { u, t -> added += u to t }) } }
        rule.onNodeWithTag("theme-ECONOMY").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Ajouter franceinfo · Économie").performScrollTo().performClick()
        rule.waitUntil(3_000) { added.isNotEmpty() }
        assertEquals("https://www.francetvinfo.fr/economie.rss" to "franceinfo · Économie", added.single())
        rule.onNodeWithContentDescription("Déjà ajouté : franceinfo · Économie").assertExists()
        // Autre langue : le flux déjà sur la page est marqué
        rule.onNodeWithTag("region-UK").performScrollTo().performClick()
        rule.onNodeWithTag("theme-ECONOMY").performScrollTo().performClick()
        rule.onNodeWithContentDescription("Déjà ajouté : BBC News · Business").performScrollTo().assertExists()
    }

    @Test
    fun catalog_search_finds_feeds_in_every_language() {
        rule.setContent { MaterialTheme { NewsFeedForm(existing = emptySet(), check = { FeedCheck.Ok("x", 1) }, onAdd = { _, _ -> }) } }
        rule.onNodeWithTag("catalog-search").performScrollTo().performTextInput("football")
        rule.onNodeWithContentDescription("Ajouter BBC Sport · Football").performScrollTo().assertExists()
        rule.onNodeWithContentDescription("Ajouter L'Équipe · Football").performScrollTo().assertExists()
    }

    @Test
    fun form_refuses_http() {
        rule.setContent { MaterialTheme { NewsFeedForm(existing = emptySet(), check = { FeedCheck.Ok("x", 1) }, onAdd = { _, _ -> }) } }
        rule.onNodeWithTag("news-url").performTextInput("http://x.fr/rss")
        rule.onNodeWithText("Vérifier et ajouter").performClick()
        rule.onNodeWithText("✗ Adresse HTTPS requise").assertIsDisplayed()
    }
}
