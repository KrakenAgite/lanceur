package app.lanceur.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.doubleClick
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.AppLists
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {
    @get:Rule val rule = createComposeRule()

    private fun app(label: String): AppEntry {
        val id = label.lowercase().filter { it.isLetter() }
        return AppEntry(AppKey("app.$id", "app.$id.Main", 0), label, false)
    }

    private val agenda = app("Agenda")
    private val banque = app("Banque")
    private val bloc = app("Bloc-notes")
    private val chrome = app("Chrome")
    private val lists = AppLists(
        allVisible = listOf(agenda, banque, bloc, chrome),
        favorites = listOf(chrome),
        hiddenApps = emptyList(),
        privateApps = emptyList(),
    )

    private fun show(lists: AppLists = this.lists, actions: (setMode: (ListMode) -> Unit) -> HomeActions) {
        rule.setContent {
            var mode by remember { mutableStateOf<ListMode>(ListMode.Favorites) }
            MaterialTheme {
                HomeScreen(lists = lists, mode = mode, side = AlphabetSide.RIGHT, actions = actions { mode = it }, icon = {})
            }
        }
    }

    @Test
    fun touching_a_letter_shows_its_apps_and_tapping_one_launches_it() {
        var launched: AppEntry? = null
        show { setMode -> HomeActions(launch = { launched = it }, changeMode = setMode) }
        rule.onNodeWithText("Chrome").assertIsDisplayed()

        rule.onNodeWithTag("alphabet").performTouchInput {
            down(Offset(centerX, height / 27f * 1.5f)) // lettre B
            up()
        }
        rule.onNodeWithText("Banque").assertIsDisplayed()
        rule.onNodeWithText("Bloc-notes").assertIsDisplayed()
        rule.onNodeWithText("Chrome").assertDoesNotExist()

        rule.onNodeWithText("Bloc-notes").performClick()
        assertEquals(bloc, launched)
    }

    @Test
    fun sliding_from_the_alphabet_onto_a_row_launches_it() {
        var launched: AppEntry? = null
        show { setMode -> HomeActions(launch = { launched = it }, changeMode = setMode) }

        rule.onNodeWithTag("alphabet").performTouchInput { down(Offset(centerX, height / 27f * 1.5f)) }
        rule.waitForIdle()
        val row = rule.onNodeWithText("Banque").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("alphabet").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("alphabet").performTouchInput {
            moveTo(Offset(row.center.x - bar.left, row.center.y - bar.top))
            up()
        }
        rule.waitForIdle()
        assertEquals(banque, launched)
    }

    @Test
    fun swiping_up_on_favorites_opens_search() {
        var opened = false
        show { HomeActions(openSearch = { opened = true }) }
        rule.onRoot().performTouchInput { swipeUp(startY = height * 0.7f, endY = height * 0.2f) }
        rule.waitForIdle()
        assertTrue(opened)
    }

    @Test
    fun a_cancelled_slide_launches_nothing() {
        // Écran éteint ou appel entrant au milieu du geste : Android annule le toucher
        var launched: AppEntry? = null
        show { setMode -> HomeActions(launch = { launched = it }, changeMode = setMode) }

        rule.onNodeWithTag("alphabet").performTouchInput { down(Offset(centerX, height / 27f * 1.5f)) }
        rule.waitForIdle()
        val row = rule.onNodeWithText("Banque").fetchSemanticsNode().boundsInRoot
        val bar = rule.onNodeWithTag("alphabet").fetchSemanticsNode().boundsInRoot
        rule.onNodeWithTag("alphabet").performTouchInput {
            moveTo(Offset(row.center.x - bar.left, row.center.y - bar.top))
            cancel()
        }
        rule.waitForIdle()
        assertNull(launched)
    }

    @Test
    fun double_tapping_an_empty_area_locks_the_screen() {
        var locks = 0
        show { HomeActions(lockScreen = { locks++ }) }
        rule.onRoot().performTouchInput { doubleClick(Offset(centerX, height * 0.4f)) }
        rule.waitForIdle()
        assertEquals(1, locks)
    }

    @Test
    fun double_tapping_an_app_does_not_lock() {
        var locked = false
        show { HomeActions(lockScreen = { locked = true }) }
        rule.onNodeWithText("Chrome").performTouchInput { doubleClick() }
        rule.waitForIdle()
        assertFalse(locked)
    }

    @Test
    fun a_single_tap_on_an_empty_area_still_returns_to_favorites() {
        var locked = false
        show { setMode -> HomeActions(changeMode = setMode, lockScreen = { locked = true }) }
        rule.onNodeWithTag("alphabet").performTouchInput {
            down(Offset(centerX, height / 27f * 1.5f)) // lettre B
            up()
        }
        rule.onNodeWithText("Chrome").assertDoesNotExist()

        rule.onRoot().performTouchInput { click(Offset(centerX, height * 0.4f)) }
        rule.waitUntil(timeoutMillis = 2_000) { rule.onAllNodesWithText("Chrome").fetchSemanticsNodes().isNotEmpty() }
        assertFalse(locked)
    }

    @Test
    fun folder_icons_open_their_apps_and_close_again() {
        val work = app.lanceur.folders.Folder(4, "Travail", app.lanceur.folders.FolderIcon.WORK, listOf(agenda.key))
        var edited: app.lanceur.folders.Folder? = null
        show(lists.copy(folders = listOf(app.lanceur.prefs.FolderApps(work, listOf(agenda))))) { setMode ->
            HomeActions(changeMode = setMode, editFolder = { edited = it })
        }
        rule.onNodeWithTag("folder-4").performClick()
        rule.onNodeWithText("Travail").assertIsDisplayed()
        rule.onNodeWithText("Agenda").assertIsDisplayed()
        rule.onNodeWithText("Chrome").assertDoesNotExist()

        rule.onNodeWithTag("folder-4").performClick()
        rule.onNodeWithText("Chrome").assertIsDisplayed()

        rule.onNodeWithTag("folder-4").performTouchInput { longClick() }
        assertEquals(work, edited)
    }

    @Test
    fun sliding_over_folders_opens_each_one_and_releasing_on_a_row_launches_it() {
        val work = app.lanceur.folders.Folder(1, "Travail", app.lanceur.folders.FolderIcon.WORK, listOf(agenda.key))
        val games = app.lanceur.folders.Folder(2, "Jeux", app.lanceur.folders.FolderIcon.GAMES, listOf(banque.key))
        var launched: AppEntry? = null
        val folders = listOf(app.lanceur.prefs.FolderApps(work, listOf(agenda)), app.lanceur.prefs.FolderApps(games, listOf(banque)))
        show(lists.copy(folders = folders)) { setMode -> HomeActions(launch = { launched = it }, changeMode = setMode) }

        val first = rule.onNodeWithTag("folder-1").fetchSemanticsNode().boundsInRoot.center
        val second = rule.onNodeWithTag("folder-2").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            down(first)
            moveTo(second)
        }
        rule.onNodeWithText("Jeux").assertIsDisplayed()
        val row = rule.onNodeWithText("Banque").fetchSemanticsNode().boundsInRoot.center
        rule.onRoot().performTouchInput {
            moveTo(Offset(second.x - 200f, row.y))
            up()
        }
        assertEquals(banque, launched)
    }
}
