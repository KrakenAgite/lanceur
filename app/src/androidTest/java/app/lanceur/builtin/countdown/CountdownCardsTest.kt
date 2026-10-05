package app.lanceur.builtin.countdown

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CountdownCardsTest {
    @get:Rule val rule = createComposeRule()
    private val now = LocalDateTime.of(2026, 10, 5, 10, 0).atZone(ZoneId.of("Europe/Paris"))

    @Test
    fun card_shows_days_left_and_title() {
        val config = CountdownConfig("Vacances", LocalDate.of(2026, 10, 17), null, 0)
        rule.setContent { MaterialTheme { Box(Modifier.height(200.dp)) { CountdownCard(config, WidgetSize.MEDIUM, onSetUp = {}, now = now) } } }
        rule.onNodeWithText("J-12").assertIsDisplayed()
        rule.onNodeWithText("VACANCES").assertIsDisplayed()
    }

    @Test
    fun unset_card_asks_to_set_up() {
        var asked = false
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { CountdownCard(null, WidgetSize.SMALL, onSetUp = { asked = true }, now = now) } } }
        rule.onNodeWithText("Régler le compte à rebours").performClick()
        assertTrue(asked)
    }

    @Test
    fun settings_save_the_title_and_default_date() {
        var saved: String? = null
        rule.setContent { MaterialTheme { CountdownSettings(initial = null, onSave = { saved = it }, now = { 7L }) } }
        rule.onNodeWithTag("countdown-title").performTextInput("Vacances")
        rule.onNodeWithText("Enregistrer").performClick()
        val config = CountdownConfig.fromData(saved)!!
        assertEquals("Vacances", config.title)
        assertEquals(LocalDate.now().plusDays(7), config.date)
        assertEquals(7L, config.createdAt)
    }
}
