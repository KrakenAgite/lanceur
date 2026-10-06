package app.lanceur.focus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
import app.lanceur.text.TextNormalizer
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.cardBackground
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import kotlinx.coroutines.delay

private val ROW_SHAPE = RoundedCornerShape(20.dp)

/** Bandeau de l'accueil pendant la concentration ; « Arrêter » passe par l'attente de [FocusExitDialog]. */
@Composable
fun FocusBanner(until: ZonedDateTime?, onStop: () -> Unit, modifier: Modifier = Modifier) {
    var asking by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(CircleShape)
            .background(cardBackground())
            .clickable { asking = true }
            .padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp)
            .testTag("focus-banner"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "🎯 " + tr("Concentration", "Focus") + (until?.let { " · " + tr("jusqu'à ", "until ") + "%02d:%02d".format(it.hour, it.minute) } ?: ""),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
        )
        TextButton(onClick = { asking = true }) { Text(tr("Arrêter", "Stop")) }
    }
    if (asking) FocusExitDialog(onConfirm = { asking = false; onStop() }, onDismiss = { asking = false })
}

/** Sortir demande d'attendre quelques secondes : de quoi renoncer à une envie passagère. */
@Composable
fun FocusExitDialog(onConfirm: () -> Unit, onDismiss: () -> Unit, seconds: Int = FocusMode.EXIT_DELAY_SECONDS) {
    var left by remember { mutableIntStateOf(seconds) }
    LaunchedEffect(Unit) {
        while (left > 0) {
            delay(1_000)
            left--
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Arrêter la concentration ?", "Stop focus?")) },
        text = {
            Text(
                if (left > 0) tr("Respire un instant… encore $left s.", "Take a breath… $left s left.")
                else tr("Les applis masquées vont réapparaître.", "Hidden apps will come back."),
            )
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = left == 0, modifier = Modifier.testTag("focus-stop-confirm")) {
                Text(if (left > 0) "$left s" else tr("Arrêter", "Stop"))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Rester concentré", "Stay focused")) } },
    )
}

class FocusActions(
    val start: () -> Unit = {},
    val stop: () -> Unit = {},
    val update: ((FocusMode) -> FocusMode) -> Unit = {},
)

/** Réglages › Concentration. [apps] : les applis de la liste de l'accueil, celles qu'on peut cocher. */
@Composable
fun FocusSection(focus: FocusMode, active: Boolean, apps: List<AppEntry>, actions: FocusActions) {
    var choosing by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    var addingSchedule by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme

    SectionTitle(tr("Concentration", "Focus"))
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(if (active) tr("En cours", "On") else tr("Arrêtée", "Off"), style = MaterialTheme.typography.titleMedium)
            Text(
                tr("Masque des applis pour rester concentré", "Hides apps to help you focus"),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        if (active) {
            FilledTonalButton(onClick = { asking = true }, modifier = Modifier.testTag("focus-toggle")) { Text(tr("Arrêter", "Stop")) }
        } else {
            Button(onClick = actions.start, modifier = Modifier.testTag("focus-toggle")) { Text(tr("Démarrer", "Start")) }
        }
    }
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FocusFilter.entries.forEach { f ->
            Chip(f.label, selected = focus.filter == f, tag = "focus-filter-${f.name}") { actions.update { it.copy(filter = f) } }
        }
    }
    val count = apps.count { (it.key.packageName to it.key.userSerial) in focus.apps }
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(ROW_SHAPE).background(cardBackground()).clickable { choosing = true }
            .padding(horizontal = 18.dp, vertical = 14.dp).testTag("focus-choose"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (focus.filter == FocusFilter.HIDE) tr("Applis masquées", "Hidden apps") else tr("Applis affichées", "Shown apps"),
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
        )
        Text("$count  ›", color = colors.primary, fontWeight = FontWeight.SemiBold)
    }
    if (focus.filter == FocusFilter.SHOW_ONLY && count == 0) {
        HintText(tr("Aucune appli cochée : toutes seront masquées pendant la concentration.", "No app checked: all will be hidden during focus."))
    }
    Text(tr("Horaires (facultatif)", "Schedule (optional)"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
    focus.schedules.forEachIndexed { index, schedule ->
        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(schedule.label(L10n.locale), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = { actions.update { f -> f.copy(schedules = f.schedules.filterIndexed { i, _ -> i != index }) } }) {
                Text(tr("Retirer", "Remove"))
            }
        }
    }
    TextButton(onClick = { addingSchedule = true }, modifier = Modifier.testTag("focus-add-schedule")) { Text("+ " + tr("Ajouter une plage", "Add a time range")) }

    if (choosing) FocusAppsSheet(focus, apps, onToggle = { key -> actions.update { it.toggle(key) } }, onDismiss = { choosing = false })
    if (asking) FocusExitDialog(onConfirm = { asking = false; actions.stop() }, onDismiss = { asking = false })
    if (addingSchedule) {
        ScheduleDialog(
            onAdd = { s -> addingSchedule = false; actions.update { it.copy(schedules = it.schedules + s) } },
            onDismiss = { addingSchedule = false },
        )
    }
}

fun FocusMode.toggle(key: Pair<String, Long>): FocusMode = copy(apps = if (key in apps) apps - key else apps + key)

@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.testTag(tag).clip(CircleShape).background(if (selected) colors.primary else cardBackground()).clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.onPrimary else colors.onSurface,
        )
    }
}

/** Les applis de la liste de l'accueil (celles qu'on lance), avec une recherche. */
@Composable
fun FocusAppsList(focus: FocusMode, apps: List<AppEntry>, onToggle: (Pair<String, Long>) -> Unit) {
    var query by remember { mutableStateOf("") }
    val shown = remember(apps, query) {
        val q = TextNormalizer.fold(query.trim())
        if (q.isEmpty()) apps else apps.filter { TextNormalizer.fold(it.label).contains(q) }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp)) {
        Text(focus.filter.label, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 4.dp))
        Text(
            if (focus.filter == FocusFilter.HIDE) tr("Cochées : invisibles pendant la concentration", "Checked: invisible during focus")
            else tr("Cochées : les seules visibles pendant la concentration", "Checked: the only ones visible during focus"),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Chercher une appli", "Search an app")) },
            singleLine = true,
            shape = ROW_SHAPE,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).testTag("focus-search"),
        )
        LazyColumn(Modifier.heightIn(max = 520.dp)) {
            items(shown, key = { it.key.encode() }) { entry ->
                val key = entry.key.packageName to entry.key.userSerial
                Row(
                    Modifier.fillMaxWidth().clip(ROW_SHAPE).clickable { onToggle(key) }.padding(vertical = 2.dp).testTag("focus-app-${entry.key.packageName}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = key in focus.apps, onCheckedChange = { onToggle(key) })
                    Text(entry.label, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FocusAppsSheet(focus: FocusMode, apps: List<AppEntry>, onToggle: (Pair<String, Long>) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        FocusAppsList(focus, apps, onToggle)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleDialog(onAdd: (FocusSchedule) -> Unit, onDismiss: () -> Unit) {
    var days by remember { mutableStateOf(setOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) }
    val start = rememberTimePickerState(9, 0, is24Hour = true)
    val end = rememberTimePickerState(18, 0, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Nouvelle plage", "New time range")) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        Chip(d.getDisplayName(TextStyle.NARROW, L10n.locale), selected = d in days, tag = "day-${d.name}") {
                            days = if (d in days) days - d else days + d
                        }
                    }
                }
                Text(tr("Début", "Start"), style = MaterialTheme.typography.labelLarge)
                TimeInput(start)
                Text(tr("Fin", "End"), style = MaterialTheme.typography.labelLarge)
                TimeInput(end)
                Spacer(Modifier)
            }
        },
        confirmButton = {
            Button(
                onClick = { onAdd(FocusSchedule(days, LocalTime.of(start.hour, start.minute), LocalTime.of(end.hour, end.minute))) },
                enabled = days.isNotEmpty() && (start.hour != end.hour || start.minute != end.minute),
            ) { Text(tr("Ajouter", "Add")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("Annuler", "Cancel")) } },
    )
}
