package app.lanceur.alphabet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveMathTest {
    @Test
    fun wave_is_maximal_under_the_finger_and_symmetric() {
        assertEquals(1f, WaveMath.factor(0f), 1e-6f)
        assertEquals(0.6065f, WaveMath.factor(1.5f), 1e-3f)
        assertEquals(WaveMath.factor(2f), WaveMath.factor(-2f), 1e-6f)
        assertTrue(WaveMath.factor(6f) < 0.001f)
        assertEquals(2.2f, WaveMath.scale(0f), 1e-6f)
        assertEquals(28f, WaveMath.shiftDp(0f), 1e-6f)
    }

    @Test
    fun nearest_enabled_letter_prefers_the_upper_one_on_ties() {
        val enabled = listOf(false, false, true, false, true)
        assertEquals(2, WaveMath.nearestEnabled(0, enabled))
        assertEquals(2, WaveMath.nearestEnabled(3, enabled))
        assertEquals(4, WaveMath.nearestEnabled(4, enabled))
        assertEquals(4, WaveMath.nearestEnabled(10, enabled))
        assertEquals(-1, WaveMath.nearestEnabled(1, listOf(false, false)))
        assertEquals(-1, WaveMath.nearestEnabled(0, emptyList()))
    }
}
