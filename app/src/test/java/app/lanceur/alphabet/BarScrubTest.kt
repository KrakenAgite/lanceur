package app.lanceur.alphabet

import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BarScrubTest {
    // Deux dossiers de 40 px à partir de 10 px, alphabet de 27 lettres de 10 px à partir de 200 px
    private val g = BarGeometry(
        width = 40f, folderCount = 2, folderTop = 10f, folderPitch = 40f,
        alphabetTop = 200f, alphabetHeight = 270f, side = AlphabetSide.RIGHT, inwardThresholdPx = 24f,
    )
    private val all = List(27) { true }

    @Test
    fun one_gesture_goes_from_letters_to_folders_and_back() {
        var phase: BarPhase = BarPhase.Idle
        phase = BarScrub.next(phase, 20f, 205f, g, all)
        assertEquals(BarPhase.OnLetter(0, 0f), phase)
        phase = BarScrub.next(phase, 20f, 60f, g, all)
        assertEquals(BarPhase.OnFolder(1), phase)
        phase = BarScrub.next(phase, 20f, 15f, g, all)
        assertEquals(BarPhase.OnFolder(0), phase)
        phase = BarScrub.next(phase, 20f, 225f, g, all)
        assertEquals(2, (phase as BarPhase.OnLetter).index)
    }

    @Test
    fun the_gap_above_the_alphabet_stays_on_the_last_folder() {
        assertEquals(BarPhase.OnFolder(1), BarScrub.next(BarPhase.OnLetter(0, 0f), 20f, 150f, g, all))
        assertFalse(BarScrub.startsOnBar(150f, g))
        assertTrue(BarScrub.startsOnBar(20f, g))
        assertTrue(BarScrub.startsOnBar(210f, g))
    }

    @Test
    fun without_folders_the_top_of_the_slide_stays_on_a() {
        val none = g.copy(folderCount = 0)
        assertEquals(0, (BarScrub.next(BarPhase.OnLetter(3, 3f), 20f, 50f, none, all) as BarPhase.OnLetter).index)
        assertFalse(BarScrub.startsOnBar(50f, none))
    }

    @Test
    fun moving_inward_keeps_what_was_open_for_the_list() {
        val list = BarScrub.next(BarPhase.OnFolder(1), -40f, 300f, g, all)
        assertEquals(BarPhase.OnList(BarPhase.OnFolder(1)), list)
        assertEquals(1, BarScrub.folderIndex(list))
        assertEquals(ScrubPhase.Idle, BarScrub.letterPhase(list))
        assertEquals(list, BarScrub.next(list, -60f, 320f, g, all))
        assertEquals(ScrubPhase.OnList(4), BarScrub.letterPhase(BarScrub.next(BarPhase.OnLetter(4, 4f), -40f, 0f, g, all)))
    }
}
