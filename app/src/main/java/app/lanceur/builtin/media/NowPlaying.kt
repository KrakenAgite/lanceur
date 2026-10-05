package app.lanceur.builtin.media

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale

/** Une session média à un instant. `updatedAtElapsed` : horloge `elapsedRealtime` de la dernière position connue. */
data class MediaSnapshot(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val art: ImageBitmap?,
    val durationMs: Long,
    val positionMs: Long,
    val updatedAtElapsed: Long,
    val speed: Float,
    val isPlaying: Boolean,
    val canPrevious: Boolean,
    val canNext: Boolean,
    val canSeek: Boolean,
)

sealed interface NowPlayingState {
    data object NoAccess : NowPlayingState
    data object Idle : NowPlayingState
    data class Active(val media: MediaSnapshot) : NowPlayingState
}

object NowPlaying {
    /** Celle qui joue ; sinon la plus récente (Android les range ainsi) qui a un titre ; sinon rien. */
    fun pick(sessions: List<MediaSnapshot>): NowPlayingState {
        val chosen = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull { !it.title.isNullOrBlank() }
        return chosen?.let { NowPlayingState.Active(it) } ?: NowPlayingState.Idle
    }

    /** Position extrapolée entre deux mises à jour du lecteur, pour une barre qui avance sans attendre. */
    fun positionAt(media: MediaSnapshot, nowElapsed: Long): Long {
        val moved = if (media.isPlaying) ((nowElapsed - media.updatedAtElapsed) * media.speed).toLong() else 0L
        val position = (media.positionMs + moved).coerceAtLeast(0)
        return if (media.durationMs > 0) position.coerceAtMost(media.durationMs) else position
    }

    fun timeLabel(ms: Long): String {
        val total = ms.coerceAtLeast(0) / 1000
        val hours = total / 3600
        val minutes = total % 3600 / 60
        val seconds = total % 60
        return if (hours > 0) String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
        else String.format(Locale.ROOT, "%d:%02d", minutes, seconds)
    }
}
