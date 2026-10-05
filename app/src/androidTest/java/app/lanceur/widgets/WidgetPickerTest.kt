package app.lanceur.widgets

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WidgetPickerTest {
    @get:Rule val rule = createComposeRule()

    private val entry = ProviderEntry(AppKey("app.meteo", "app.meteo.Jour", 0), ProfileKind.MAIN, "Météo", "Prévisions", 110)
    private val groups = listOf(PickerGroup("Météo", "app.meteo", isWork = false, entries = listOf(entry)))

    @Test
    fun lists_apps_and_picks_a_widget() {
        var picked: ProviderEntry? = null
        rule.setContent { MaterialTheme { WidgetPicker(groups, "", {}, preview = {}, onPick = { picked = it }) } }
        rule.onNodeWithText("Météo").assertIsDisplayed()
        rule.onNodeWithText("Prévisions").performClick()
        assertEquals(entry, picked)
    }

    @Test
    fun typing_updates_the_filter() {
        var query = ""
        rule.setContent { MaterialTheme { WidgetPicker(groups, query, { query = it }, preview = {}, onPick = {}) } }
        rule.onNodeWithTag("picker-filter").performTextInput("cal")
        assertEquals("cal", query)
    }

    @Test
    fun builtin_widgets_show_their_own_starting_size() {
        // Les 5 premiers tiennent à l'écran (la liste ne compose que les lignes visibles)
        val lanceur = app.lanceur.builtin.BuiltinSlots.pickerEntries().take(5)
        val groups = listOf(PickerGroup("Lanceur", "app.lanceur.builtin", isWork = false, entries = lanceur))
        rule.setContent { MaterialTheme { WidgetPicker(groups, "", {}, preview = {}, onPick = {}) } }
        rule.onAllNodesWithText("Taille de départ : M").assertCountEquals(3)
        rule.onAllNodesWithText("Taille de départ : S").assertCountEquals(2)
    }
}
