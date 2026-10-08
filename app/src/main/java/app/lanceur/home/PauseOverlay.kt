package app.lanceur.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.i18n.tr
import app.lanceur.ui.blockTouchesBelow

/** Durée de la pause avant une appli marquée (`AppMenuAction.PAUSE`). */
const val PAUSE_MILLIS = 5_000

/**
 * Pause avant d'ouvrir une appli choisie : un cercle se remplit pendant quelques secondes, puis « Ouvrir » devient
 * possible. « Pas maintenant » et Retour referment sans rien ouvrir.
 */
@Composable
fun PauseOverlay(
    entry: AppEntry,
    icon: @Composable (AppKey) -> Unit,
    onOpen: () -> Unit,
    onCancel: () -> Unit,
    durationMillis: Int = PAUSE_MILLIS,
) {
    BackHandler(onBack = onCancel)
    val progress = remember(entry.key) { Animatable(0f) }
    LaunchedEffect(entry.key) { progress.animateTo(1f, tween(durationMillis, easing = LinearEasing)) }
    val ready = progress.value >= 1f
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
            .blockTouchesBelow()
            .systemBarsPadding()
            .testTag("pause"),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress.value },
                    modifier = Modifier.size(120.dp),
                    strokeWidth = 4.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                icon(entry.key)
            }
            Text(
                if (ready) tr("Toujours envie d'ouvrir ${entry.label} ?", "Still want to open ${entry.label}?")
                else tr("Respire un instant…", "Take a breath…"),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpen, enabled = ready, modifier = Modifier.fillMaxWidth().testTag("pause-open")) {
                Text(tr("Ouvrir ${entry.label}", "Open ${entry.label}"))
            }
            TextButton(onClick = onCancel, modifier = Modifier.testTag("pause-cancel")) { Text(tr("Pas maintenant", "Not now")) }
        }
    }
}
