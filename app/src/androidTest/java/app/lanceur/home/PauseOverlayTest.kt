package app.lanceur.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PauseOverlayTest {
    @get:Rule val rule = createComposeRule()

    private val entry = AppEntry(AppKey("app.reseau", "app.reseau.Main", 0), "Réseau", false)

    @Test
    fun open_is_possible_only_after_the_pause_and_cancel_opens_nothing() {
        var opened = 0
        var cancelled = 0
        rule.mainClock.autoAdvance = false
        rule.setContent {
            MaterialTheme { PauseOverlay(entry, icon = {}, onOpen = { opened++ }, onCancel = { cancelled++ }, durationMillis = 2_000) }
        }
        rule.onNodeWithText("Respire un instant…").assertExists()
        rule.onNodeWithTag("pause-open").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(2_500)
        rule.onNodeWithText("Toujours envie d'ouvrir Réseau ?").assertExists()
        rule.onNodeWithTag("pause-open").assertIsEnabled().performClick()
        assertEquals(1, opened)
        rule.onNodeWithTag("pause-cancel").performClick()
        assertEquals(1, cancelled)
    }
}
