package app.lanceur.builtin.note

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class NoteCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun typing_saves_the_text() {
        var saved = ""
        rule.setContent { MaterialTheme { Box(Modifier.height(220.dp)) { NoteCard("", onSave = { saved = it }, saveDelayMs = 0) } } }
        rule.onNodeWithText("Touche pour écrire…").assertIsDisplayed()
        rule.onNodeWithTag("note-field").performTextInput("Acheter du pain")
        rule.waitUntil(2_000) { saved == "Acheter du pain" }
    }
}
