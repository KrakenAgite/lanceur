package app.lanceur.builtin.weather

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * Ville (recherche Open-Meteo) ou position approximative. Choisir « Ma position » sans autorisation la demande ;
 * tant qu'elle n'est pas accordée, le mode reste « Ville ».
 */
@Composable
fun WeatherSettings(
    initial: WeatherConfig?,
    search: suspend (String) -> List<Place>,
    locationGranted: Boolean,
    requestLocation: () -> Unit,
    onSave: (String) -> Unit,
) {
    var usePosition by remember { mutableStateOf(initial?.usePosition == true && locationGranted) }
    var wantsPosition by remember { mutableStateOf(false) }
    var place by remember { mutableStateOf(initial?.place) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf(emptyList<Place>()) }
    var searched by remember { mutableStateOf(false) }
    LaunchedEffect(locationGranted, wantsPosition) { if (wantsPosition && locationGranted) usePosition = true }
    LaunchedEffect(query) {
        if (query.trim().length < 2) return@LaunchedEffect
        delay(400)
        results = search(query)
        searched = true
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Météo", style = MaterialTheme.typography.headlineSmall)
        Row(Modifier.fillMaxWidth().clickable { usePosition = false; wantsPosition = false }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = !usePosition, onClick = null)
            Text("Ville", Modifier.padding(start = 8.dp))
        }
        Row(
            Modifier.fillMaxWidth().clickable {
                if (locationGranted) usePosition = true else { wantsPosition = true; requestLocation() }
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = usePosition, onClick = null)
            Column(Modifier.padding(start = 8.dp)) {
                Text("Ma position")
                Text("Position approximative, envoyée arrondie au kilomètre", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!usePosition) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(place?.label ?: "Chercher une ville") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("place-search"),
            )
            if (searched && results.isEmpty()) Text("Aucune ville trouvée", color = MaterialTheme.colorScheme.onSurfaceVariant)
            results.forEach { p ->
                Row(Modifier.fillMaxWidth().clickable { place = p; query = ""; results = emptyList(); searched = false }.padding(vertical = 8.dp)) {
                    Text(p.label)
                }
            }
        }
        Button(
            onClick = { onSave(WeatherData.encode(WeatherConfig(usePosition, if (usePosition) null else place), cache = null)) },
            enabled = usePosition || place != null,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }
    }
}
