package app.lanceur.builtin.todo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class TodoCardTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun add_check_and_clear() {
        var list by mutableStateOf(TodoList())
        rule.setContent { MaterialTheme { Box(Modifier.height(260.dp)) { TodoCard(list, onChange = { list = it }) } } }
        rule.onNodeWithTag("todo-new").performTextInput("Pain")
        rule.onNodeWithTag("todo-new").performImeAction()
        assertEquals(listOf("Pain"), list.items.map { it.text })
        rule.onNodeWithText("Pain").performClick()
        assertTrue(list.items.single().done)
        rule.onNodeWithText("Effacer les tâches faites").performClick()
        assertTrue(list.items.isEmpty())
    }
}
