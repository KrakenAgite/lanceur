package app.lanceur.builtin.timer

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TimerCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun preset_starts_a_timer_and_stopwatch_runs() {
        var seconds = 0
        var stopwatch by mutableStateOf(StopwatchState())
        rule.setContent {
            MaterialTheme {
                Box(Modifier.height(200.dp)) {
                    TimerCard(stopwatch, onStopwatch = { stopwatch = it }, onTimer = { seconds = it; true }, onOtherTimer = {}, clock = { 1_000L })
                }
            }
        }
        rule.onNodeWithText("5 min").performClick()
        assertEquals(300, seconds)
        rule.onNodeWithText("Minuteur de 5 min lancé").assertIsDisplayed()
        rule.onNodeWithText("Chrono").performClick()
        rule.onNodeWithText("Démarrer").performClick()
        assertTrue(stopwatch.running)
        rule.onNodeWithText("Pause").assertIsDisplayed()
    }
}
