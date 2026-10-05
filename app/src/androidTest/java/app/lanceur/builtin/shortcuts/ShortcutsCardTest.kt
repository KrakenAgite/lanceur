package app.lanceur.builtin.shortcuts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ShortcutsCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun buttons_trigger_their_action() {
        val calls = mutableListOf<String>()
        val actions = ShortcutActions(
            toggleTorch = { calls += "torch:$it" },
            internet = { calls += "internet" },
            bluetooth = { calls += "bluetooth" },
            sound = { calls += "sound" },
            doNotDisturb = { calls += "dnd" },
        )
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { ShortcutsCard(TorchState(available = true, on = false), actions) } } }
        listOf("Lampe", "Internet", "Bluetooth", "Son", "Ne pas déranger").forEach { rule.onNodeWithText(it).performClick() }
        assertEquals(listOf("torch:true", "internet", "bluetooth", "sound", "dnd"), calls)
    }

    @Test
    fun unavailable_torch_is_disabled() {
        rule.setContent { MaterialTheme { ShortcutsCard(TorchState(available = false, on = false), ShortcutActions()) } }
        rule.onNodeWithText("Lampe").assertIsNotEnabled()
    }
}
