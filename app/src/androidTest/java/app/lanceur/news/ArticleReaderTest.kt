package app.lanceur.news

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.lanceur.builtin.rss.Article
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticleReaderTest {
    @get:Rule val rule = createComposeRule()

    private val now = 1_800_000_000_000L
    private val article = Article("Titre du flux", "https://site.fr/a", "Le Journal", now - 3_600_000)

    private fun show(state: ReaderState, back: () -> Unit = {}, site: () -> Unit = {}) = rule.setContent {
        MaterialTheme { ArticleReader(article, state, now, image = { _, _ -> }, onBack = back, onOpenSite = site) }
    }

    @Test
    fun ready_article_shows_title_byline_and_every_kind_of_block() {
        show(
            ReaderState.Ready(
                ReadableArticle(
                    title = "Titre de la page", site = "Le Journal en ligne", byline = "Léa Martin", published = now - 7_200_000, image = null,
                    blocks = listOf(
                        ArticleBlock.Paragraph("Premier paragraphe."),
                        ArticleBlock.Heading("Intertitre"),
                        ArticleBlock.Quote("Une citation."),
                        ArticleBlock.Bullet("Un point"),
                    ),
                ),
            ),
        )
        rule.onNodeWithText("LE JOURNAL EN LIGNE").assertIsDisplayed()
        rule.onNodeWithText("Titre de la page").assertIsDisplayed()
        rule.onNodeWithText("Léa Martin", substring = true).assertIsDisplayed()
        listOf("Premier paragraphe.", "Intertitre", "Une citation.", "Un point").forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        rule.onNodeWithText("Lire sur le site").assertIsDisplayed()
    }

    @Test
    fun loading_shows_the_skeleton_and_back_and_site_buttons_work() {
        var back = false
        var site = false
        show(ReaderState.Loading, back = { back = true }, site = { site = true })
        rule.onNodeWithTag("reader-loading").assertIsDisplayed()
        rule.onNodeWithTag("reader-back").performClick()
        rule.onNodeWithTag("reader-open").performClick()
        assertTrue(back && site)
    }

    @Test
    fun unreadable_page_offers_the_website_with_the_feed_title() {
        var site = 0
        show(ReaderState.NotReadable(null), site = { site++ })
        rule.onNodeWithText("Titre du flux").assertIsDisplayed()
        rule.onNodeWithTag("reader-unavailable").assertIsDisplayed()
        rule.onNodeWithText("Ouvrir sur le site").performClick()
        assertEquals(1, site)
    }

    @Test
    fun tapping_an_article_on_the_news_page_opens_the_reader_instead_of_the_browser() {
        var read: Article? = null
        var opened: String? = null
        rule.setContent {
            MaterialTheme {
                NewsPage(
                    chips = listOf(NewsChip("https://site.fr/rss", "Le Journal", false)), filter = null, articles = listOf(article), footer = "", unavailable = false, refreshing = false, now = now,
                    actions = NewsActions(open = { opened = it }, read = { read = it }),
                    image = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("news-list").performScrollToNode(hasText("Titre du flux"))
        rule.onNodeWithText("Titre du flux").performClick()
        assertEquals(article, read)
        assertEquals(null, opened)
    }
}
