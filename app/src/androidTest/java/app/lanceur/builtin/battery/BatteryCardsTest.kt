package app.lanceur.builtin.battery

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class BatteryCardsTest {
    @get:Rule val rule = createComposeRule()

    private val charging = BatteryInfo(78, ChargeStatus.CHARGING, 42 * 60_000L, 312, powerSave = false)

    @Test
    fun ring_shows_level_status_temperature_and_opens_settings() {
        var clicked = false
        rule.setContent { MaterialTheme { BatteryRingCard(charging, onClick = { clicked = true }) } }
        rule.onNodeWithText("78 %").assertIsDisplayed()
        rule.onNodeWithText("En charge · pleine dans 42 min").assertIsDisplayed()
        rule.onNodeWithText("31 °C").assertIsDisplayed()
        rule.onNodeWithText("Économiseur désactivé").assertIsDisplayed()
        rule.onNodeWithText("78 %").performClick()
        assertTrue(clicked)
    }

    @Test
    fun bar_shows_level_and_short_charge_time() {
        rule.setContent { MaterialTheme { BatteryBarCard(charging.copy(level = 15), onClick = {}) } }
        rule.onNodeWithText("15 %").assertIsDisplayed()
        rule.onNodeWithText("⚡ 42 min").assertIsDisplayed()
    }
}
