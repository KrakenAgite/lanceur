package app.lanceur.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.AppLists
import androidx.activity.compose.BackHandler
import androidx.test.espresso.Espresso
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomePagerTest {
    @get:Rule val rule = createComposeRule()

    private fun app(label: String): AppEntry {
        val id = label.lowercase()
        return AppEntry(AppKey("app.$id", "app.$id.Main", 0), label, false)
    }

    private val lists = AppLists(listOf(app("Banque"), app("Chrome")), listOf(app("Chrome")), emptyList(), emptyList())

    private fun simpleHome(): @Composable () -> Unit = { Box(Modifier.fillMaxSize()) { Text("ACCUEIL") } }

    @Test
    fun swiping_right_shows_the_widget_page() {
        rule.setContent {
            MaterialTheme {
                HomePager(true, 0, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithText("PAGE WIDGETS").assertIsNotDisplayed()
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
    }

    @Test
    fun a_disabled_page_cannot_be_reached() {
        rule.setContent {
            MaterialTheme {
                HomePager(false, 0, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
        rule.onNodeWithText("PAGE WIDGETS").assertDoesNotExist()
    }

    @Test
    fun going_home_and_disabling_bring_back_the_home_page() {
        var requests by mutableIntStateOf(0)
        var enabled by mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                HomePager(enabled, requests, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        requests++
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()

        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        enabled = false
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
    }

    @Test
    fun a_gesture_started_on_the_alphabet_stays_with_the_alphabet() {
        rule.setContent {
            MaterialTheme {
                HomePager(
                    true, 0, false, {}, {},
                    widgetPage = { Text("PAGE WIDGETS") },
                    home = { HomeScreen(lists, ListMode.Favorites, AlphabetSide.RIGHT, HomeActions(), icon = {}) },
                )
            }
        }
        rule.onNodeWithTag("alphabet").performTouchInput {
            down(Offset(centerX, height / 27f * 1.5f))
            repeat(10) { moveBy(Offset(-40f, 0f)) }
            repeat(20) { moveBy(Offset(40f, 0f)) }
            up()
        }
        rule.onNodeWithText("PAGE WIDGETS").assertIsNotDisplayed()
    }

    @Test
    fun back_belongs_to_an_overlay_opened_from_the_widget_page() {
        var overlayBack = false
        var backEnabled by mutableStateOf(true)
        rule.setContent {
            MaterialTheme {
                // Comme AppRoot : le gestionnaire de l'écran superposé est déclaré avant le défilement
                BackHandler(enabled = !backEnabled) { overlayBack = true }
                HomePager(true, 0, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome(), backEnabled = backEnabled)
            }
        }
        rule.onNodeWithTag("pager").performTouchInput { swipeRight() }
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
        backEnabled = false // le sélecteur s'ouvre par-dessus
        rule.waitForIdle()
        Espresso.pressBack()
        rule.waitForIdle()
        assertTrue(overlayBack)
        rule.onNodeWithText("PAGE WIDGETS").assertIsDisplayed()
    }

    @Test
    fun home_during_a_fling_toward_widgets_still_returns_home() {
        var requests by mutableIntStateOf(0)
        rule.setContent {
            MaterialTheme {
                HomePager(true, requests, false, {}, {}, widgetPage = { Text("PAGE WIDGETS") }, home = simpleHome())
            }
        }
        rule.mainClock.autoAdvance = false
        // Glissement court et rapide : la page est encore plus près de l'accueil, mais son élan l'emporte vers les widgets
        rule.onNodeWithTag("pager").performTouchInput { swipeRight(startX = 0f, endX = width * 0.3f, durationMillis = 40) }
        requests++ // Accueil pressé pendant cet élan
        rule.mainClock.autoAdvance = true
        rule.waitForIdle()
        rule.onNodeWithText("ACCUEIL").assertIsDisplayed()
        rule.onNodeWithText("PAGE WIDGETS").assertIsNotDisplayed()
    }
}
