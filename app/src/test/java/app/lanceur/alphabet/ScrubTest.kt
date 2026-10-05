package app.lanceur.alphabet

import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Test

class ScrubTest {
    // 27 lettres sur 270 px : 10 px par lettre ; barre de 36 px de large ; seuil de 24 px
    private val all = List(27) { true }
    private fun input(x: Float, y: Float, side: AlphabetSide = AlphabetSide.RIGHT) =
        ScrubInput(x = x, y = y, width = 36f, height = 270f, side = side, inwardThresholdPx = 24f)

    @Test
    fun finger_on_the_bar_selects_the_letter_under_it() {
        assertEquals(ScrubPhase.OnBar(1, 1f), Scrub.next(ScrubPhase.Idle, input(18f, 15f), all))
    }

    @Test
    fun empty_letter_jumps_to_the_nearest_non_empty_one() {
        val noB = all.toMutableList().apply { this[1] = false }
        assertEquals(ScrubPhase.OnBar(0, 1f), Scrub.next(ScrubPhase.Idle, input(18f, 15f), noB))
    }

    @Test
    fun moving_inward_keeps_the_letter_and_targets_the_list() {
        assertEquals(ScrubPhase.OnList(3), Scrub.next(ScrubPhase.OnBar(3, 3f), input(-30f, 200f), all))
        assertEquals(ScrubPhase.OnList(3), Scrub.next(ScrubPhase.OnBar(3, 3f), input(70f, 200f, AlphabetSide.LEFT), all))
    }

    @Test
    fun coming_back_to_the_bar_selects_again() {
        assertEquals(ScrubPhase.OnBar(5, 5f), Scrub.next(ScrubPhase.OnList(3), input(10f, 55f), all))
    }

    @Test
    fun finger_outside_vertically_is_clamped() {
        assertEquals(ScrubPhase.OnBar(26, 26.5f), Scrub.next(ScrubPhase.Idle, input(18f, 999f), all))
        assertEquals(ScrubPhase.OnBar(0, -0.5f), Scrub.next(ScrubPhase.Idle, input(18f, -50f), all))
    }

    @Test
    fun no_letter_at_all_stays_idle() {
        assertEquals(ScrubPhase.Idle, Scrub.next(ScrubPhase.Idle, input(18f, 15f), List(27) { false }))
    }
}
