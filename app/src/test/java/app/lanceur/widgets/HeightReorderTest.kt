package app.lanceur.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class HeightReorderTest {
    private val heights = listOf(100f, 200f, 300f)

    @Test
    fun stays_until_half_of_the_neighbour_is_passed() {
        assertEquals(HeightReorder.Result(0, 0, 90f), HeightReorder.step(heights, 0, 90f))
        assertEquals(HeightReorder.Result(0, 1, -50f), HeightReorder.step(heights, 0, 150f))
    }

    @Test
    fun a_long_drag_crosses_several_neighbours_of_different_heights() {
        assertEquals(HeightReorder.Result(0, 2, -140f), HeightReorder.step(heights, 0, 360f))
    }

    @Test
    fun dragging_up_uses_the_neighbour_above() {
        assertEquals(HeightReorder.Result(2, 1, 80f), HeightReorder.step(heights, 2, -120f))
        assertEquals(HeightReorder.Result(2, 2, -90f), HeightReorder.step(heights, 2, -90f))
    }

    @Test
    fun never_leaves_the_list() {
        assertEquals(HeightReorder.Result(2, 2, 999f), HeightReorder.step(heights, 2, 999f))
        assertEquals(HeightReorder.Result(0, 0, -999f), HeightReorder.step(heights, 0, -999f))
    }
}
