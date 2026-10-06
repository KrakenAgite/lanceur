package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SettingsPagesTest {
    @get:Rule val rule = createComposeRule()

    private var moved: Pair<PageKind, Int>? = null
    private var news: Boolean? = null
    private var widgets: Boolean? = null

    private fun show() = rule.setContent {
        MaterialTheme {
            SettingsScreen(
                favorites = emptyList(), side = AlphabetSide.RIGHT, isDefaultLauncher = true, permissionsGranted = true,
                lockServiceEnabled = true, pageOrder = PageLayout.DEFAULT_ORDER, widgetPageEnabled = true, newsEnabled = false,
                icon = {},
                initialPage = SettingsPage.PAGES,
                actions = SettingsActions(
                    movePage = { k, d -> moved = k to d },
                    setNewsEnabled = { news = it },
                    setWidgetPageEnabled = { widgets = it },
                ),
            )
        }
    }

    @Test
    fun eye_shows_state_and_toggles_pages_but_home_has_none() {
        show()
        rule.onNodeWithTag("page-eye-NEWS").performScrollTo().assertContentDescriptionEquals("Afficher Actualités")
        rule.onNodeWithTag("page-eye-NEWS").performClick()
        assertEquals(true, news)
        rule.onNodeWithTag("page-eye-WIDGETS").assertContentDescriptionEquals("Masquer Widgets")
        rule.onNodeWithTag("page-eye-WIDGETS").performClick()
        assertEquals(false, widgets)
        rule.onNodeWithTag("page-eye-HOME").assertDoesNotExist()
    }

    @Test
    fun dragging_a_preview_moves_the_page() {
        show()
        rule.onNodeWithTag("page-preview-HOME").performScrollTo().performTouchInput {
            down(center)
            advanceEventTime(800)
            repeat(24) { moveBy(Offset(-width * 0.1f, 0f)); advanceEventTime(16) }
            up()
        }
        assertEquals(PageKind.HOME to -2, moved)
    }
}
