package app.lanceur.builtin.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class NowPlayingCardTest {
    @get:Rule val rule = createComposeRule()

    private val song = MediaSnapshot(
        "com.spotify.music", "Spotify", "Bohemian Rhapsody", "Queen", null,
        durationMs = 355_000, positionMs = 102_000, updatedAtElapsed = 0, speed = 1f, isPlaying = true,
        canPrevious = true, canNext = false, canSeek = true,
    )

    private fun show(state: NowPlayingState, actions: NowPlayingActions) = rule.setContent {
        MaterialTheme { Box(Modifier.height(200.dp)) { NowPlayingCard(state, WidgetSize.MEDIUM, actions, clock = { 0L }) } }
    }

    @Test
    fun active_shows_the_song_and_drives_the_player() {
        val calls = mutableListOf<String>()
        show(
            NowPlayingState.Active(song),
            NowPlayingActions(playPause = { calls += "pause" }, previous = { calls += "prev" }, open = { calls += "open" }),
        )
        rule.onNodeWithText("Bohemian Rhapsody").assertIsDisplayed()
        rule.onNodeWithText("Queen · Spotify").assertIsDisplayed()
        rule.onNodeWithText("1:42").assertIsDisplayed()
        rule.onNodeWithText("5:55").assertIsDisplayed()
        rule.onNodeWithContentDescription("Pause").performClick()
        rule.onNodeWithContentDescription("Précédent").performClick()
        rule.onNodeWithContentDescription("Suivant").assertIsNotEnabled()
        // Le titre lui-même : le centre de la carte fusionnée tombe sur la barre de progression
        rule.onNodeWithText("Bohemian Rhapsody", useUnmergedTree = true).performClick()
        assertEquals(listOf("pause", "prev", "open"), calls)
    }

    @Test
    fun no_access_offers_to_allow() {
        var asked = false
        show(NowPlayingState.NoAccess, NowPlayingActions(grantAccess = { asked = true }))
        rule.onNodeWithText("Autoriser l'accès aux lecteurs").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun idle_says_nothing_is_playing() {
        show(NowPlayingState.Idle, NowPlayingActions())
        rule.onNodeWithText("Rien en lecture").assertIsDisplayed()
    }
}
