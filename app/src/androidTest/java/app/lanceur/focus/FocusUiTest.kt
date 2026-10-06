package app.lanceur.focus

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class FocusUiTest {
    @get:Rule val rule = createComposeRule()

    private val apps = listOf("Instagram", "Mail", "TikTok").map { AppEntry(AppKey("com.${it.lowercase()}", "Main", 0L), it, false) }

    @Test
    fun stopping_needs_a_ten_second_wait() {
        var stopped = false
        rule.mainClock.autoAdvance = false
        rule.setContent { MaterialTheme { FocusExitDialog(onConfirm = { stopped = true }, onDismiss = {}) } }
        rule.onNodeWithTag("focus-stop-confirm").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithTag("focus-stop-confirm").assertIsNotEnabled()
        rule.mainClock.advanceTimeBy(5_500)
        rule.onNodeWithTag("focus-stop-confirm").assertIsEnabled().performClick()
        assertTrue(stopped)
    }

    @Test
    fun section_starts_focus_switches_mode_and_counts_checked_apps() {
        var started = false
        var focus = FocusMode(apps = setOf("com.tiktok" to 0L))
        rule.setContent {
            MaterialTheme {
                Column { FocusSection(focus, active = false, apps = apps, actions = FocusActions(start = { started = true }, update = { focus = it(focus) })) }
            }
        }
        rule.onNodeWithText("Applis masquées").assertExists()
        rule.onNodeWithText("1  ›").assertExists()
        rule.onNodeWithTag("focus-toggle").performClick()
        assertTrue(started)
        rule.onNodeWithTag("focus-filter-SHOW_ONLY").performClick()
        assertEquals(FocusFilter.SHOW_ONLY, focus.filter)
    }

    @Test
    fun app_list_searches_and_toggles() {
        val toggled = mutableListOf<Pair<String, Long>>()
        rule.setContent { MaterialTheme { FocusAppsList(FocusMode(), apps, onToggle = { toggled += it }) } }
        rule.onNodeWithTag("focus-search").performTextInput("tik")
        rule.onNodeWithText("Instagram").assertDoesNotExist()
        rule.onNodeWithTag("focus-app-com.tiktok").performClick()
        assertEquals(listOf("com.tiktok" to 0L), toggled)
    }

    @Test
    fun banner_shows_the_end_time_and_asks_before_stopping() {
        rule.setContent { MaterialTheme { FocusBanner(until = java.time.ZonedDateTime.of(2026, 10, 6, 18, 0, 0, 0, java.time.ZoneId.of("Europe/Paris")), onStop = {}) } }
        rule.onNodeWithText("🎯 Concentration · jusqu'à 18:00").assertExists()
        rule.onNodeWithTag("focus-banner").performClick()
        rule.onNodeWithText("Arrêter la concentration ?").assertExists()
    }
}
