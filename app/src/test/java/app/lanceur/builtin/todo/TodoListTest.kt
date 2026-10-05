package app.lanceur.builtin.todo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoListTest {
    @Test
    fun add_ignores_blank_text_and_numbers_items() {
        val list = TodoList().add("  pain ").add("   ").add("lait")
        assertEquals(listOf(TodoItem(1, "pain", false), TodoItem(2, "lait", false)), list.items)
    }

    @Test
    fun done_items_move_to_the_end_and_can_be_cleared() {
        val list = TodoList().add("a").add("b").add("c").toggle(1)
        assertEquals(listOf("b", "c", "a"), list.visible.map { it.text })
        assertTrue(list.hasDone)
        assertEquals(listOf("b", "c"), list.clearDone().items.map { it.text })
        assertFalse(list.clearDone().hasDone)
        assertEquals(listOf("a", "c"), list.remove(2).items.map { it.text })
    }

    @Test
    fun round_trip_and_garbage() {
        val list = TodoList().add("a|b=c").add("d").toggle(2)
        assertEquals(list, TodoList.fromData(list.toData()))
        assertEquals(TodoList(), TodoList.fromData(null))
        assertEquals(TodoList(), TodoList.fromData("items=pas|un|nombre"))
        assertEquals(3L, list.add("e").items.last().id)
    }
}
