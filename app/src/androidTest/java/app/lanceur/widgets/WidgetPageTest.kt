package app.lanceur.widgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.apps.AppKey
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
        events = listOf(SummaryLine(SummaryEvent(1, "Dentiste", 0, 0, false), "14:00 Dentiste")),
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
        rule.onNodeWithText("Lundi 5 octobre").assertIsDisplayed()
        rule.onNodeWithText("14:00 Dentiste").assertIsDisplayed()
        rule.onNodeWithText("⏰ Demain 07:00").assertIsDisplayed()
        rule.onNodeWithText("CONTENU MÉTÉO").assertIsDisplayed()
        rule.onNodeWithText("Modifier").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun without_widget_it_invites_to_add_one() {
        var added = false
        show(emptyList(), editMode = false, actions = WidgetPageActions(addWidget = { added = true }))
        rule.onNodeWithText("Ajoute ton premier widget").assertIsDisplayed()
        rule.onNodeWithText("+ Ajouter un widget").performScrollTo().performClick()
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
}
