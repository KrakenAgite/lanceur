package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.home.PageLayout
import app.lanceur.i18n.L10n
import app.lanceur.i18n.Lang
import app.lanceur.prefs.AlphabetSide
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsEnglishTest {
    @get:Rule val rule = createComposeRule()

    @After fun backToFrench() { L10n.lang = Lang.FR }

    @Test
    fun settings_are_in_english_and_language_row_opens_the_system_screen() {
        L10n.lang = Lang.EN
        var opened = false
        rule.setContent {
            MaterialTheme {
                SettingsScreen(
                    favorites = emptyList(), side = AlphabetSide.RIGHT, isDefaultLauncher = true, permissionsGranted = true,
                    lockServiceEnabled = true, pageOrder = PageLayout.DEFAULT_ORDER, widgetPageEnabled = true, newsEnabled = false,
                    icon = {}, actions = SettingsActions(openLanguage = { opened = true }),
                )
            }
        }
        rule.onNodeWithText("Settings").assertIsDisplayed()
        rule.onNodeWithText("Language").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("Change").performClick()
        assertTrue(opened)
        rule.onNodeWithTag("page-eye-NEWS").performScrollTo().assertContentDescriptionEquals("Show News")
        rule.onNodeWithText("Home").assertIsDisplayed()
    }
}
