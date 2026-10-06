package app.lanceur.news

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.rss.FeedCheck
import app.lanceur.builtin.rss.RssConfig
import app.lanceur.builtin.rss.RssSuggestions
import kotlinx.coroutines.launch

/** Ajout d'un flux à la page : adresse collée ou suggestion, vérifiée avant d'être ajoutée. */
@Composable
fun NewsFeedForm(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (url: String, title: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun tryAdd(candidate: String) {
        val clean = candidate.trim()
        RssConfig.validate(clean)?.let { status = "✗ $it"; return }
        checking = true
        status = "Vérification…"
        scope.launch {
            when (val result = check(clean)) {
                is FeedCheck.Ok -> { status = "✓ ${result.title} — ${result.count} articles"; onAdd(clean, result.title) }
                is FeedCheck.Failed -> status = "✗ ${result.message}"
            }
            checking = false
        }
    }
    Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Ajouter un flux", style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; status = null },
            label = { Text("Adresse du flux") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("news-url"),
        )
        Button(onClick = { tryAdd(url) }, enabled = !checking && url.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text("Vérifier et ajouter") }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        Text("Suggestions", style = MaterialTheme.typography.titleSmall)
        RssSuggestions.all.filterNot { it.url in existing }.forEach { suggestion ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !checking) { tryAdd(suggestion.url) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(suggestion.name, Modifier.weight(1f))
                Text("Ajouter", color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.padding(4.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedSheet(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NewsFeedForm(existing, check, onAdd)
    }
}
