package app.lanceur.builtin.rss

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

class RssCardsTest {
    @get:Rule val rule = createComposeRule()
    private val now = 1_000_000_000L
    private val articles = listOf(
        Article("Élections : les résultats", "https://lemonde.fr/a", "Le Monde", now - 25 * 60_000),
        Article("Astuce Linux", "https://korben.info/x", "Korben", now - 3 * 3_600_000),
    )

    @Test
    fun lists_articles_and_opens_one() {
        var opened: String? = null
        rule.setContent {
            MaterialTheme { Box(Modifier.height(280.dp)) { RssCard(RssViewState.Ready(articles, now, failed = false), WidgetSize.MEDIUM, now, onOpen = { opened = it }) } }
        }
        rule.onNodeWithText("Le Monde · il y a 25 min").assertIsDisplayed()
        rule.onNodeWithText("Astuce Linux").performClick()
        assertEquals("https://korben.info/x", opened)
    }

    @Test
    fun unavailable_message() {
        rule.setContent { MaterialTheme { RssCard(RssViewState.Unavailable, WidgetSize.MEDIUM, now, onOpen = {}) } }
        rule.onNodeWithText("Flux indisponibles").assertIsDisplayed()
    }

    @Test
    fun settings_check_and_save_a_suggestion() {
        var saved: String? = null
        rule.setContent { MaterialTheme { RssSettings(initial = null, check = { FeedCheck.Ok("Le Monde", 20) }, onSave = { saved = it }) } }
        rule.onNodeWithText("Le Monde").performClick()
        rule.onNodeWithText("Vérifier et enregistrer").performClick()
        rule.waitUntil(3_000) { saved != null }
        assertEquals(listOf("https://www.lemonde.fr/rss/une.xml"), RssData.config(saved)!!.urls)
    }

    @Test
    fun settings_refuse_http() {
        var saved: String? = null
        rule.setContent { MaterialTheme { RssSettings(initial = null, check = { FeedCheck.Ok("x", 1) }, onSave = { saved = it }) } }
        rule.onNodeWithTag("feed-url-0").performTextInput("http://example.org/feed")
        rule.onNodeWithText("Vérifier et enregistrer").performClick()
        rule.onNodeWithText("✗ Adresse HTTPS requise").assertIsDisplayed()
        assertNull(saved)
    }
}
