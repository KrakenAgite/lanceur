package app.lanceur.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import org.junit.Assert.assertEquals
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

    @Test
    fun a_dot_follows_the_name_only_when_the_app_has_notifications() {
        var badges by androidx.compose.runtime.mutableStateOf(emptySet<Pair<String, Long>>())
        rule.setContent {
            MaterialTheme {
                CompositionLocalProvider(app.lanceur.builtin.media.LocalBadges provides badges) {
                    AppRow(entry, icon = {}, menuItems = emptyList(), onClick = {}, onMenu = {})
                }
            }
        }
        rule.onNodeWithTag("badge", useUnmergedTree = true).assertDoesNotExist()
        badges = setOf("com.a" to 0L)
        rule.onNodeWithTag("badge", useUnmergedTree = true).assertExists()
        badges = setOf("com.a" to 10L)
        rule.onNodeWithTag("badge", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun favorite_info_and_uninstall_are_icons_on_top_and_the_rest_stays_in_text() {
        val picked = mutableListOf<AppMenuAction>()
        rule.setContent {
            MaterialTheme {
                AppRow(
                    entry, icon = {},
                    menuItems = listOf(AppMenuAction.REMOVE_FAVORITE, AppMenuAction.HIDE, AppMenuAction.INFO, AppMenuAction.UNINSTALL),
                    onClick = {}, onMenu = { picked += it },
                )
            }
        }
        rule.onNodeWithText("Calendrier").performTouchInput { longClick() }
        rule.onNodeWithContentDescription("Retirer des favoris").assertExists()
        rule.onNodeWithContentDescription("Désinstaller").assertExists()
        rule.onNodeWithText("Cacher").assertExists()
        rule.onNodeWithText("Infos de l'appli").assertDoesNotExist()
        rule.onNodeWithTag("menu-INFO").performClick()
        assertEquals(listOf(AppMenuAction.INFO), picked)
    }
}
