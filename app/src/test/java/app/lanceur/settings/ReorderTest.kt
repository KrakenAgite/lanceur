package app.lanceur.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ReorderTest {
    private val list = listOf("a", "b", "c", "d")

    @Test
    fun moves_an_item_down_or_up() {
        assertEquals(listOf("b", "c", "a", "d"), Reorder.move(list, 0, 2))
        assertEquals(listOf("d", "a", "b", "c"), Reorder.move(list, 3, 0))
    }

    @Test
    fun invalid_moves_return_the_same_list() {
        assertEquals(list, Reorder.move(list, 1, 1))
        assertEquals(list, Reorder.move(list, -1, 2))
        assertEquals(list, Reorder.move(list, 0, 9))
    }
}
