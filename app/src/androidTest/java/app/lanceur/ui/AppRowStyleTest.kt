package app.lanceur.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Rule
import org.junit.Test

class AppRowStyleTest {
    @get:Rule val rule = createComposeRule()

    private val entry = AppEntry(AppKey("com.a", "com.a.Main", 0L), "Calendrier", false)

    private fun show(style: AppLabelStyle) = rule.setContent {
        MaterialTheme {
            CompositionLocalProvider(LocalAppLabelStyle provides style) {
                AppRow(entry, icon = { Box(Modifier.testTag("icon")) }, menuItems = emptyList(), onClick = {}, onMenu = {})
            }
        }
    }

    @Test
    fun default_shows_the_icon_and_the_name_as_is() {
        show(AppLabelStyle())
        rule.onNodeWithTag("icon", useUnmergedTree = true).assertExists()
        rule.onNodeWithText("Calendrier").assertExists()
    }

    @Test
    fun icons_can_be_hidden_and_names_put_in_capitals() {
        show(AppLabelStyle(showIcons = false, uppercase = true))
        rule.onNodeWithTag("icon", useUnmergedTree = true).assertDoesNotExist()
        rule.onNodeWithText("CALENDRIER").assertExists()
    }
}
