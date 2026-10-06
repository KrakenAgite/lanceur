package app.lanceur.news

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import app.lanceur.builtin.rss.Article
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ArticleWebViewTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun bar_shows_the_source_and_closes_or_hands_over_to_the_browser() {
        var closed = false
        var browser: String? = null
        rule.setContent {
            MaterialTheme {
                ArticleWebView(url = "about:blank", source = "Le Journal", onClose = { closed = true }, onOpenInBrowser = { browser = it })
            }
        }
        rule.onNodeWithTag("article-web").assertIsDisplayed()
        rule.onNodeWithText("LE JOURNAL").assertIsDisplayed()
        rule.onNodeWithTag("article-browser").performClick()
        assertEquals("about:blank", browser)
        rule.onNodeWithTag("article-close").performClick()
        assertTrue(closed)
    }

    @Test
    fun closing_the_view_deletes_the_cookies_left_by_sites() {
        val cookies = android.webkit.CookieManager.getInstance()
        var shown by androidx.compose.runtime.mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                if (shown) ArticleWebView(url = "about:blank", source = "Le Journal", onClose = {}, onOpenInBrowser = {})
            }
        }
        rule.runOnIdle {
            cookies.setCookie("https://site.example", "session=abc")
            cookies.flush()
        }
        assertEquals("session=abc", cookies.getCookie("https://site.example"))
        shown = false
        rule.waitForIdle()
        rule.waitUntil(3_000) { cookies.getCookie("https://site.example") == null }
    }

    @Test
    fun tapping_an_article_on_the_news_page_opens_it_in_lanceur_instead_of_the_browser() {
        val article = Article("Titre du flux", "https://site.fr/a", "Le Journal", 0L)
        var read: Article? = null
        var opened: String? = null
        rule.setContent {
            MaterialTheme {
                NewsPage(
                    chips = listOf(NewsChip("https://site.fr/rss", "Le Journal", false)), filter = null, articles = listOf(article), footer = "",
                    unavailable = false, refreshing = false, now = 0L,
                    actions = NewsActions(open = { opened = it }, read = { read = it }),
                    image = { _, _ -> },
                )
            }
        }
        rule.onNodeWithTag("news-list").performScrollToNode(androidx.compose.ui.test.hasText("Titre du flux"))
        rule.onNodeWithText("Titre du flux").performClick()
        assertEquals(article, read)
        assertEquals(null, opened)
    }
}
