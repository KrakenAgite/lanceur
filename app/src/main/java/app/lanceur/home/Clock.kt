package app.lanceur.home

import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
private val DATE get() = DateTimeFormatter.ofPattern(tr("EEEE d MMMM", "EEEE, MMMM d"), L10n.locale)

/** Heure et date. L'appui long sur l'heure est le geste discret du dossier caché. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Clock(
    onClockTap: () -> Unit,
    onClockLongPress: () -> Unit,
    onDateTap: () -> Unit,
    modifier: Modifier = Modifier,
    currentTime: () -> LocalDateTime = LocalDateTime::now,
) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    // Recalée à chaque retour au premier plan : pendant la veille profonde, `delay` est suspendu
    val now by produceState(currentTime(), lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                value = currentTime()
                delay(60_000L - System.currentTimeMillis() % 60_000L)
            }
        }
    }
    // Ombre douce de la couleur opposée au texte : lisible sur tous les fonds d'écran, sans cadre
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val shadow = androidx.compose.ui.graphics.Shadow(
        color = (if (dark) androidx.compose.ui.graphics.Color.Black else androidx.compose.ui.graphics.Color.White).copy(alpha = 0.45f),
        offset = androidx.compose.ui.geometry.Offset(0f, 2f),
        blurRadius = 12f,
    )
    Column(modifier.padding(horizontal = 24.dp, vertical = 32.dp)) {
        Text(
            now.format(TIME),
            style = MaterialTheme.typography.displayLarge.copy(shadow = shadow, fontWeight = androidx.compose.ui.text.font.FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.combinedClickable(onClick = onClockTap, onLongClick = onClockLongPress),
        )
        Text(
            now.format(DATE).replaceFirstChar { it.titlecase(Locale.FRENCH) },
            style = MaterialTheme.typography.titleMedium.copy(shadow = shadow),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.clickable(onClick = onDateTap),
        )
    }
}
