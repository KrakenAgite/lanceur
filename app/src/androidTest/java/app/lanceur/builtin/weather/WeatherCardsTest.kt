package app.lanceur.builtin.weather

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class WeatherCardsTest {
    @get:Rule val rule = createComposeRule()

    private val forecast = Forecast(
        temp = 16, apparent = 15, icon = "⛅", condition = "Éclaircies", wind = 12, rain = 20, min = 9, max = 17,
        hours = (15..20).map { HourForecast("$it h", "🌧", it) },
        days = listOf("Mar.", "Mer.", "Jeu.", "Ven.", "Sam.").map { DayForecast(it, "☁", 8, 15, 30) },
    )
    private val ready = WeatherViewState.Ready("Nantes", forecast, "Mis à jour à 14:05")

    @Test
    fun large_card_shows_now_hours_and_days() {
        var opened = false
        rule.setContent { MaterialTheme { Box(Modifier.height(340.dp)) { WeatherCard(ready, WidgetSize.LARGE, onOpen = { opened = true }, onChooseCity = {}) } } }
        rule.onNodeWithText("16°").assertIsDisplayed()
        rule.onNodeWithText("Min 9° · Max 17°").assertIsDisplayed()
        rule.onNodeWithText("15 h").assertIsDisplayed()
        rule.onNodeWithText("Sam.").assertIsDisplayed()
        rule.onNodeWithText("16°").performClick()
        assertTrue(opened)
    }

    @Test
    fun no_position_offers_a_city() {
        var chose = false
        rule.setContent { MaterialTheme { Box(Modifier.height(220.dp)) { WeatherCard(WeatherViewState.NoPosition, WidgetSize.MEDIUM, onOpen = {}, onChooseCity = { chose = true }) } } }
        rule.onNodeWithText("Choisir une ville").performClick()
        assertTrue(chose)
    }

    @Test
    fun settings_pick_a_found_city() {
        var saved: String? = null
        rule.setContent {
            MaterialTheme {
                WeatherSettings(
                    initial = null,
                    search = { listOf(Place("Nantes", "Pays de la Loire", 47.21, -1.55)) },
                    locationGranted = false,
                    requestLocation = {},
                    onSave = { saved = it },
                )
            }
        }
        rule.onNodeWithText("Enregistrer").assertIsNotEnabled()
        rule.onNodeWithTag("place-search").performTextInput("nan")
        rule.waitUntil(3_000) { rule.onAllNodes(androidx.compose.ui.test.hasText("Nantes, Pays de la Loire")).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText("Nantes, Pays de la Loire").performClick()
        rule.onNodeWithText("Enregistrer").performClick()
        assertEquals("Nantes", WeatherData.config(saved)!!.place!!.name)
    }

    @Test
    fun choosing_position_asks_for_location() {
        var asked = false
        rule.setContent {
            MaterialTheme { WeatherSettings(initial = null, search = { emptyList() }, locationGranted = false, requestLocation = { asked = true }, onSave = {}) }
        }
        rule.onNodeWithText("Ma position").performClick()
        assertTrue(asked)
    }
}
