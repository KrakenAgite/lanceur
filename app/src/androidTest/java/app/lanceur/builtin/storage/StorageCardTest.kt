package app.lanceur.builtin.storage

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StorageCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun shows_both_gauges_and_opens_settings() {
        var opened = false
        val reading = StorageReading(Gauge(87_000_000_000, 128_000_000_000), Gauge(7_100_000_000, 12_000_000_000))
        rule.setContent { MaterialTheme { Box(Modifier.height(140.dp)) { StorageCard(reading, onClick = { opened = true }) } } }
        rule.onNodeWithText("87 / 128 Go").assertIsDisplayed()
        rule.onNodeWithText("7,1 / 12,0 Go").assertIsDisplayed()
        rule.onNodeWithText("Mémoire vive").performClick()
        assertTrue(opened)
    }
}
