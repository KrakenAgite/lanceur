package app.lanceur.builtin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import org.junit.Rule
import org.junit.Test

class BuiltinPreviewTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun every_kind_draws_a_miniature_with_sample_data() {
        rule.setContent {
            MaterialTheme {
                androidx.compose.foundation.layout.Column {
                    BuiltinKind.entries.forEach { Box(Modifier.size(96.dp, 64.dp)) { BuiltinPreview(it) } }
                }
            }
        }
        rule.onAllNodesWithText("78 %", useUnmergedTree = true).assertCountEquals(2)
        rule.onNodeWithText("Bohemian Rhapsody", useUnmergedTree = true).assertExists()
    }
}
