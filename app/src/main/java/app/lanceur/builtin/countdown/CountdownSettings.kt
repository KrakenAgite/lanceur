package app.lanceur.builtin.countdown

import app.lanceur.i18n.tr
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

/** Titre, date (par défaut dans une semaine) et heure facultative. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CountdownSettings(initial: CountdownConfig?, onSave: (String) -> Unit, now: () -> Long = System::currentTimeMillis) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    // Le sélecteur de date travaille en UTC, à minuit
    val dateState = rememberDatePickerState(
        initialSelectedDateMillis = (initial?.date ?: LocalDate.now().plusDays(7)).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    var withTime by remember { mutableStateOf(initial?.time != null) }
    val timeState = rememberTimePickerState(initialHour = initial?.time?.hour ?: 9, initialMinute = initial?.time?.minute ?: 0, is24Hour = true)
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("Compte à rebours", "Countdown"), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text(tr("Titre", "Title")) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("countdown-title"),
        )
        DatePicker(state = dateState, title = null, headline = null, showModeToggle = false)
        Row(Modifier.fillMaxWidth().clickable { withTime = !withTime }, verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = withTime, onCheckedChange = { withTime = it })
            Spacer(Modifier.width(12.dp))
            Text(tr("Heure précise", "Exact time"))
        }
        if (withTime) TimeInput(state = timeState)
        Button(
            onClick = {
                val millis = dateState.selectedDateMillis ?: return@Button
                val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                val time = if (withTime) LocalTime.of(timeState.hour, timeState.minute) else null
                onSave(CountdownConfig(title.trim().ifEmpty { tr("Compte à rebours", "Countdown") }, date, time, initial?.createdAt ?: now()).toData())
            },
            enabled = dateState.selectedDateMillis != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Enregistrer", "Save")) }
    }
}
