package app.lanceur.builtin.timer

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.ui.cardBackground
import kotlinx.coroutines.delay

/** `onTimer` lance un minuteur de l'Horloge (secondes) ; `false` : l'Horloge a seulement été ouverte. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerCard(
    stopwatch: StopwatchState,
    onStopwatch: (StopwatchState) -> Unit,
    onTimer: (Int) -> Boolean,
    onOtherTimer: () -> Unit,
    modifier: Modifier = Modifier,
    clock: () -> Long = System::currentTimeMillis,
) {
    var chrono by remember { mutableStateOf(stopwatch.running) }
    var message by remember { mutableStateOf<String?>(null) }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(stopwatch.running) {
        now = clock()
        while (stopwatch.running) {
            delay(100)
            now = clock()
        }
    }
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 16.dp, vertical = 12.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf(tr("Minuteur", "Timer"), tr("Chrono", "Stopwatch")).forEachIndexed { index, label ->
                SegmentedButton(
                    selected = chrono == (index == 1),
                    onClick = { chrono = index == 1 },
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    icon = {},
                    label = { Text(label) },
                )
            }
        }
        if (!chrono) {
            Column(Modifier.weight(1f).padding(top = 8.dp), verticalArrangement = Arrangement.Center) {
                (TimerPresets.minutes.map { it as Int? } + null).chunked(4).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { minutes ->
                            AssistChip(
                                onClick = {
                                    if (minutes == null) onOtherTimer()
                                    else message = if (onTimer(TimerPresets.seconds(minutes))) tr("Minuteur de ${TimerPresets.label(minutes)} lancé", "${TimerPresets.label(minutes)} timer started") else null
                                },
                                label = { Text(minutes?.let(TimerPresets::label) ?: tr("Autre", "Other")) },
                            )
                        }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        } else {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text(StopwatchState.format(stopwatch.elapsed(now)), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
                if (stopwatch.laps.isNotEmpty()) {
                    Text(
                        stopwatch.laps.joinToString("  ·  ") { StopwatchState.format(it) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = {
                        val t = clock()
                        onStopwatch(if (stopwatch.running) stopwatch.pause(t) else stopwatch.start(t))
                    }) { Text(if (stopwatch.running) tr("Pause", "Pause") else tr("Démarrer", "Start")) }
                    OutlinedButton(onClick = { onStopwatch(stopwatch.lap(clock())) }, enabled = stopwatch.running) { Text(tr("Tour", "Lap")) }
                    TextButton(onClick = { onStopwatch(stopwatch.reset()) }, enabled = !stopwatch.running && stopwatch.accumulated > 0) {
                        Text(tr("Réinitialiser", "Reset"))
                    }
                }
            }
        }
    }
}
