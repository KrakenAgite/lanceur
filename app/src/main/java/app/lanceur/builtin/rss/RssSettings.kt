package app.lanceur.builtin.rss

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/** Jusqu'à 3 flux ; chacun est vérifié avant d'enregistrer. */
@Composable
fun RssSettings(initial: RssConfig?, check: suspend (String) -> FeedCheck, onSave: (String) -> Unit) {
    val urls = remember { mutableStateListOf(*Array(RssConfig.MAX) { initial?.urls?.getOrNull(it).orEmpty() }) }
    var statuses by remember { mutableStateOf(emptyMap<Int, String>()) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Flux RSS", style = MaterialTheme.typography.headlineSmall)
        urls.indices.forEach { i ->
            OutlinedTextField(
                value = urls[i],
                onValueChange = { urls[i] = it; statuses = statuses - i },
                label = { Text("Adresse du flux ${i + 1}") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("feed-url-$i"),
            )
            statuses[i]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        RssSuggestions.all.forEach { s ->
            val index = urls.indexOf(s.url)
            Row(
                Modifier.fillMaxWidth().clickable {
                    if (index >= 0) urls[index] = ""
                    else urls.indexOfFirst { it.isBlank() }.takeIf { it >= 0 }?.let { urls[it] = s.url }
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = index >= 0, onCheckedChange = null)
                Text(s.name, Modifier.padding(start = 8.dp))
            }
        }
        Button(
            onClick = {
                checking = true
                scope.launch {
                    val filled = urls.withIndex().filter { it.value.isNotBlank() }
                    val results = filled.associate { (i, url) -> i to (RssConfig.validate(url)?.let { FeedCheck.Failed(it) } ?: check(url.trim())) }
                    statuses = results.mapValues { (_, r) ->
                        when (r) {
                            is FeedCheck.Ok -> "✓ ${r.title} — ${r.count} articles"
                            is FeedCheck.Failed -> "✗ ${r.message}"
                        }
                    }
                    checking = false
                    if (results.values.all { it is FeedCheck.Ok }) onSave(RssData.encode(RssConfig(filled.map { it.value.trim() }), cache = null))
                }
            },
            enabled = !checking && urls.any { it.isNotBlank() },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (checking) "Vérification…" else "Vérifier et enregistrer") }
    }
}
