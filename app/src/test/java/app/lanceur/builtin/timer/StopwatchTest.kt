package app.lanceur.builtin.timer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StopwatchTest {
    @Test
    fun start_pause_resume() {
        val s = StopwatchState().start(1_000)
        assertTrue(s.running)
        assertEquals(500, s.elapsed(1_500))
        val paused = s.pause(3_000)
        assertFalse(paused.running)
        assertEquals(2_000, paused.elapsed(99_000))
        assertEquals(2_500, paused.start(10_000).elapsed(10_500))
        assertEquals(StopwatchState(), paused.reset())
    }

    @Test
    fun laps_keep_the_last_three() {
        var s = StopwatchState().start(0)
        listOf(1_000L, 2_000, 3_000, 4_000).forEach { s = s.lap(it) }
        assertEquals(listOf(2_000L, 3_000, 4_000), s.laps)
    }

    @Test
    fun clock_going_back_never_gives_negative_time() {
        assertEquals(0, StopwatchState().start(10_000).elapsed(5_000))
    }

    @Test
    fun format_and_round_trip() {
        assertEquals("00:42,3", StopwatchState.format(42_345))
        assertEquals("1:02:03,4", StopwatchState.format(3_723_456))
        val s = StopwatchState(startedAt = 5, accumulated = 7, laps = listOf(1, 2))
        assertEquals(s, StopwatchState.fromData(s.toData()))
        assertEquals(StopwatchState(), StopwatchState.fromData("n'importe quoi"))
        assertEquals(listOf("1 min", "3 min", "5 min", "10 min", "15 min", "30 min"), TimerPresets.minutes.map(TimerPresets::label))
        assertEquals(300, TimerPresets.seconds(5))
    }
}
