package app.lanceur.builtin.media

import org.junit.Assert.assertEquals
import org.junit.Test

class NowPlayingTest {
    private fun media(pkg: String, playing: Boolean, title: String? = "Titre", position: Long = 10_000, duration: Long = 200_000, speed: Float = 1f) =
        MediaSnapshot(pkg, pkg, title, "Artiste", null, duration, position, updatedAtElapsed = 1_000, speed = speed, isPlaying = playing, canPrevious = true, canNext = true, canSeek = true)

    @Test
    fun picks_the_playing_session_then_the_most_recent_one() {
        val paused = media("a", playing = false)
        val playing = media("b", playing = true)
        assertEquals(NowPlayingState.Active(playing), NowPlaying.pick(listOf(paused, playing)))
        assertEquals(NowPlayingState.Active(paused), NowPlaying.pick(listOf(paused, media("c", playing = false))))
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(emptyList()))
    }

    @Test
    fun paused_sessions_without_title_are_ignored() {
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(listOf(media("a", playing = false, title = null))))
        assertEquals(NowPlayingState.Idle, NowPlaying.pick(listOf(media("a", playing = false, title = " "))))
    }

    @Test
    fun position_advances_while_playing_and_stays_in_bounds() {
        assertEquals(15_000, NowPlaying.positionAt(media("a", true), nowElapsed = 6_000))
        assertEquals(20_000, NowPlaying.positionAt(media("a", true, speed = 2f), nowElapsed = 6_000))
        assertEquals(10_000, NowPlaying.positionAt(media("a", false), nowElapsed = 60_000))
        assertEquals(200_000, NowPlaying.positionAt(media("a", true), nowElapsed = 10_000_000))
        assertEquals(0, NowPlaying.positionAt(media("a", true, position = -5_000, speed = 0f), nowElapsed = 1_000))
    }

    @Test
    fun time_labels() {
        assertEquals("1:42", NowPlaying.timeLabel(102_000))
        assertEquals("0:05", NowPlaying.timeLabel(5_900))
        assertEquals("1:02:03", NowPlaying.timeLabel(3_723_000))
    }
}
