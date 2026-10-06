package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPagesTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun pages_can_be_moved_and_switched_but_home_stays() {
        var moved: Pair<PageKind, Int>? = null
        var news: Boolean? = null
        rule.setContent {
            MaterialTheme {
                SettingsScreen(
                    favorites = emptyList(), side = AlphabetSide.RIGHT, isDefaultLauncher = true, permissionsGranted = true,
                    lockServiceEnabled = true, pageOrder = PageLayout.DEFAULT_ORDER, widgetPageEnabled = true, newsEnabled = false,
                    icon = {}, actions = SettingsActions(movePage = { k, d -> moved = k to d }, setNewsEnabled = { news = it }),
                )
            }
        }
        rule.onNodeWithContentDescription("Monter Accueil").performScrollTo().performClick()
        assertEquals(PageKind.HOME to -1, moved)
        rule.onNodeWithTag("page-switch-NEWS").performScrollTo().performClick()
        assertEquals(true, news)
        rule.onNodeWithTag("page-switch-HOME").performScrollTo().assertIsNotEnabled()
    }
}
