package app.lanceur.builtin.media

import app.lanceur.i18n.tr
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize
import kotlinx.coroutines.delay

class NowPlayingActions(
    val playPause: () -> Unit = {},
    val next: () -> Unit = {},
    val previous: () -> Unit = {},
    val seekTo: (Long) -> Unit = {},
    val open: () -> Unit = {},
    val grantAccess: () -> Unit = {},
)

@Composable
fun NowPlayingCard(
    state: NowPlayingState,
    size: WidgetSize,
    actions: NowPlayingActions,
    modifier: Modifier = Modifier,
    clock: () -> Long = android.os.SystemClock::elapsedRealtime,
) {
    when (state) {
        is NowPlayingState.Active -> ActiveCard(state.media, size, actions, modifier, clock)
        NowPlayingState.Idle -> Row(
            modifier.fillMaxSize().background(cardBackground()).padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text("🎵", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.width(12.dp))
            Text(tr("Rien en lecture", "Nothing playing"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        NowPlayingState.NoAccess -> Column(
            modifier.fillMaxSize().background(cardBackground()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (size != WidgetSize.SMALL) {
                Text(
                    tr("Pour voir et piloter tes lecteurs, Lanceur a besoin de l'accès aux notifications. Il ne les lit pas.", "To see and control your players, Lanceur needs notification access. It doesn't read them."),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Button(onClick = actions.grantAccess) { Text(tr("Autoriser l'accès aux lecteurs", "Allow player access")) }
            if (size == WidgetSize.LARGE) {
                Text(
                    tr("Option grisée ? Infos de l'appli › ⋮ › Autoriser les paramètres restreints", "Option greyed out? App info › ⋮ › Allow restricted settings"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ActiveCard(media: MediaSnapshot, size: WidgetSize, actions: NowPlayingActions, modifier: Modifier, clock: () -> Long) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(media) {
        now = clock()
        while (media.isPlaying) {
            delay(500)
            now = clock()
        }
    }
    var dragging by remember(media) { mutableStateOf<Float?>(null) }
    Box(modifier.fillMaxSize().clickable(onClick = actions.open)) {
        // Fond : pochette floutée, ou dégradé du thème sans pochette
        if (media.art != null) {
            Image(media.art, contentDescription = null, modifier = Modifier.matchParentSize().blur(28.dp), contentScale = ContentScale.Crop)
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer))))
        }
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.65f)))))
        Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 12.dp)) {
            if (size == WidgetSize.LARGE) CardLabel(tr("En cours", "Now playing"), Modifier.padding(bottom = 8.dp), color = Color.White.copy(alpha = 0.85f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val thumb = if (size == WidgetSize.LARGE) 96.dp else 56.dp
                Box(Modifier.size(thumb).clip(RoundedCornerShape(12.dp)).background(Color.White.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
                    if (media.art != null) Image(media.art, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Text("🎵", style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(media.title.orEmpty(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(media.artist?.takeIf { it.isNotBlank() }, media.appLabel).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (size != WidgetSize.SMALL && media.durationMs > 0) {
                val position = NowPlaying.positionAt(media, now)
                Slider(
                    value = dragging ?: (position.toFloat() / media.durationMs),
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { actions.seekTo((it * media.durationMs).toLong()) }
                        dragging = null
                    },
                    enabled = media.canSeek,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.3f)),
                )
                Row(Modifier.fillMaxWidth()) {
                    val shown = dragging?.let { (it * media.durationMs).toLong() } ?: position
                    Text(NowPlaying.timeLabel(shown), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                    Spacer(Modifier.weight(1f))
                    Text(NowPlaying.timeLabel(media.durationMs), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                val tint = IconButtonDefaults.iconButtonColors(contentColor = Color.White, disabledContentColor = Color.White.copy(alpha = 0.35f))
                IconButton(onClick = actions.previous, enabled = media.canPrevious, colors = tint) { Icon(MediaIcons.SkipPrevious, contentDescription = tr("Précédent", "Previous")) }
                FilledIconButton(
                    onClick = actions.playPause,
                    modifier = Modifier.size(52.dp),
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White, contentColor = Color.Black),
                ) {
                    if (media.isPlaying) Icon(MediaIcons.Pause, contentDescription = tr("Pause", "Pause"))
                    else Icon(Icons.Default.PlayArrow, contentDescription = tr("Lecture", "Play"))
                }
                IconButton(onClick = actions.next, enabled = media.canNext, colors = tint) { Icon(MediaIcons.SkipNext, contentDescription = tr("Suivant", "Next")) }
            }
        }
    }
}
