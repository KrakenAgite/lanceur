package app.lanceur.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.apps.AppKey
import app.lanceur.builtin.BuiltinKind
import app.lanceur.builtin.BuiltinSlots
import app.lanceur.summary.DaySummaryState
import app.lanceur.summary.SummaryEvent
import app.lanceur.summary.SummaryLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WidgetPageTest {
    @get:Rule val rule = createComposeRule()

    private val slot = WidgetSlot(3, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)
    private val summary = DaySummaryState(
        dateLabel = "Lundi 5 octobre",
        events = listOf(
            SummaryLine(SummaryEvent(9, "Jean-Côme Chopin - Anniversaire", 0, 0, true), "Jean-Côme Chopin - Anniversaire", "🎁", "Jean-Côme Chopin", tomorrow = false, birthday = true),
            SummaryLine(SummaryEvent(1, "Dentiste", 0, 0, false), "14:00 Dentiste", "14:00", "Dentiste", tomorrow = false),
            SummaryLine(SummaryEvent(2, "Algorithmique", 0, 0, false, color = 0xFF3366CC.toInt()), "Demain 08:00 Algorithmique", "08:00", "Algorithmique", tomorrow = true),
        ),
        alarmLabel = "Demain 07:00",
        calendarGranted = true,
    )

    private fun show(cards: List<WidgetCard>, editMode: Boolean, actions: WidgetPageActions, onWidgetClick: () -> Unit = {}) {
        rule.setContent {
            MaterialTheme {
                WidgetPage(
                    summary = summary,
                    cards = cards,
                    editMode = editMode,
                    label = { "Météo" },
                    isReconfigurable = { false },
                    widgetView = { _, modifier -> Box(modifier.clickable(onClick = onWidgetClick)) { Text("CONTENU MÉTÉO") } },
                    actions = actions,
                )
            }
        }
    }

    @Test
    fun shows_the_summary_and_the_widgets() {
        show(listOf(WidgetCard.Live(slot)), editMode = false, actions = WidgetPageActions())
        rule.onNodeWithText("LUNDI").assertIsDisplayed()
        rule.onNodeWithText("🎁").assertIsDisplayed()
        rule.onNodeWithText("Jean-Côme Chopin").assertIsDisplayed()
        rule.onNodeWithText("5 octobre").assertIsDisplayed()
        rule.onNodeWithText("14:00").assertIsDisplayed()
        rule.onNodeWithText("Dentiste").assertIsDisplayed()
        rule.onNodeWithText("DEMAIN").assertIsDisplayed()
        rule.onNodeWithText("Algorithmique").assertIsDisplayed()
        rule.onNodeWithText("⏰ Demain 07:00").assertIsDisplayed()
        rule.onNodeWithText("CONTENU MÉTÉO").assertIsDisplayed()
        rule.onNodeWithText("Modifier").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun without_widget_it_invites_to_add_one() {
        var added = false
        show(emptyList(), editMode = false, actions = WidgetPageActions(addWidget = { added = true }))
        // La grande carte vide se touche directement
        rule.onNodeWithText("Ajoute ton premier widget").performScrollTo().performClick()
        assertTrue(added)
    }

    @Test
    fun edit_mode_changes_the_size_and_finishes() {
        var resized: Pair<WidgetSlot, WidgetSize>? = null
        var editMode: Boolean? = null
        show(
            listOf(WidgetCard.Live(slot)),
            editMode = true,
            actions = WidgetPageActions(resize = { s, size -> resized = s to size }, setEditMode = { editMode = it }),
        )
        rule.onNodeWithText("L").performClick()
        assertEquals(slot to WidgetSize.LARGE, resized)
        rule.onNodeWithText("Terminé").performScrollTo().performClick()
        assertEquals(false, editMode)
    }

    @Test
    fun edit_mode_blocks_touches_to_the_widget() {
        var clicks = 0
        show(listOf(WidgetCard.Live(slot)), editMode = true, actions = WidgetPageActions(), onWidgetClick = { clicks++ })
        rule.onNodeWithText("CONTENU MÉTÉO").performClick()
        assertEquals(0, clicks)
    }

    @Test
    fun an_unavailable_widget_can_be_removed() {
        var removed: WidgetSlot? = null
        show(listOf(WidgetCard.Unavailable(slot)), editMode = false, actions = WidgetPageActions(remove = { removed = it }))
        rule.onNodeWithText("Widget indisponible").assertIsDisplayed()
        rule.onNodeWithText("Retirer").performClick()
        assertEquals(slot, removed)
    }

    @Test
    fun edit_mode_hides_sizes_for_single_size_builtin_widgets() {
        val battery = BuiltinSlots.create(BuiltinKind.BATTERY_RING, emptyList())
        show(listOf(WidgetCard.Live(battery)), editMode = true, actions = WidgetPageActions())
        rule.onAllNodesWithText("S").assertCountEquals(0)
        rule.onAllNodesWithText("M").assertCountEquals(0)
    }

    @Test
    fun edit_mode_offers_the_builtin_sizes() {
        val month = BuiltinSlots.create(BuiltinKind.CALENDAR_MONTH, emptyList())
        var resized: WidgetSize? = null
        show(listOf(WidgetCard.Live(month)), editMode = true, actions = WidgetPageActions(resize = { _, size -> resized = size }))
        rule.onNodeWithText("L").performClick()
        assertEquals(WidgetSize.LARGE, resized)
    }
}
