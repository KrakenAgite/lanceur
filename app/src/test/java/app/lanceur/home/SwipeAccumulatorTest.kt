package app.lanceur.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SwipeAccumulatorTest {
    @Test
    fun fires_once_per_gesture_past_the_threshold() {
        val swipe = SwipeAccumulator(100f)
        assertNull(swipe.add(-60f))
        assertEquals(Swipe.UP, swipe.add(-50f))
        assertNull(swipe.add(-500f))
        swipe.reset()
        assertEquals(Swipe.DOWN, swipe.add(120f))
    }

    @Test
    fun back_and_forth_below_the_threshold_does_nothing() {
        val swipe = SwipeAccumulator(100f)
        assertNull(swipe.add(-80f))
        assertNull(swipe.add(70f))
    }
}
