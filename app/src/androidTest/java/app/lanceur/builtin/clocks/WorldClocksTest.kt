package app.lanceur.builtin.clocks

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
import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WorldClocksTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun card_shows_time_and_offset() {
        val face = WorldClock.of(Cities.byId("tokyo")!!, Instant.parse("2026-10-05T12:00:00Z"), ZoneId.of("Europe/Paris"))
        rule.setContent { MaterialTheme { Box(Modifier.height(200.dp)) { WorldClocksCard(listOf(face), WidgetSize.MEDIUM) } } }
        rule.onNodeWithText("Tokyo").assertIsDisplayed()
        rule.onNodeWithText("21:00").assertIsDisplayed()
        rule.onNodeWithText("☾ +7 h").assertIsDisplayed()
    }

    @Test
    fun settings_add_a_city_to_home() {
        var saved: String? = null
        rule.setContent { MaterialTheme { WorldClocksSettings(initial = null, home = Cities.byId("paris")!!, onSave = { saved = it }) } }
        rule.onNodeWithTag("city-search").performTextInput("tokyo")
        rule.onNodeWithText("Tokyo").performClick()
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals(listOf("paris", "tokyo"), WorldClocksConfig.fromData(saved).cityIds)
    }
}
