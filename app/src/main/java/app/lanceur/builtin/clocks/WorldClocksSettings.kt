package app.lanceur.builtin.clocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** 1 à 4 villes ; à l'ajout, la ville du téléphone est cochée. */
@Composable
fun WorldClocksSettings(initial: WorldClocksConfig?, home: City, onSave: (String) -> Unit) {
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf(initial?.cityIds?.takeIf { it.isNotEmpty() } ?: listOf(home.id)) }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Horloges du monde", style = MaterialTheme.typography.headlineSmall)
        Text("${selected.size} / ${WorldClocksConfig.MAX} villes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Chercher une ville") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("city-search"),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            items(Cities.search(query), key = { it.id }) { city ->
                val checked = city.id in selected
                val enabled = checked || selected.size < WorldClocksConfig.MAX
                Row(
                    Modifier.fillMaxWidth().clickable(enabled = enabled) { selected = if (checked) selected - city.id else selected + city.id },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
                    Text(city.name, modifier = Modifier.padding(start = 12.dp))
                }
            }
        }
        Button(
            onClick = { onSave(WorldClocksConfig(selected).toData()) },
            enabled = selected.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Enregistrer") }
    }
}
