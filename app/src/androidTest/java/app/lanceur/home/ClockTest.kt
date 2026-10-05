package app.lanceur.home

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.Lifecycle
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

class ClockTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun time_is_refreshed_when_the_launcher_comes_back() {
        var now = LocalDateTime.of(2026, 10, 5, 8, 0)
        rule.setContent { MaterialTheme { Clock(onClockTap = {}, onClockLongPress = {}, onDateTap = {}, currentTime = { now }) } }
        rule.onNodeWithText("08:00").assertExists()

        // Écran éteint deux heures et demie : le délai de la boucle est suspendu pendant la veille profonde
        now = LocalDateTime.of(2026, 10, 5, 10, 30)
        rule.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        rule.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        rule.waitForIdle()
        rule.onNodeWithText("10:30").assertExists()
    }
}
