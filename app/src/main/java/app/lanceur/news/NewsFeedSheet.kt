package app.lanceur.news

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.rss.FeedCheck
import app.lanceur.builtin.rss.RssConfig
import app.lanceur.i18n.tr
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import kotlinx.coroutines.launch

private val ROW_SHAPE = RoundedCornerShape(20.dp)

/**
 * Ajout de flux à la page : adresse collée, ou catalogue par langue et par thème.
 * Chaque flux est vérifié avant d'être ajouté ; la feuille reste ouverte pour en ajouter plusieurs.
 */
@Composable
fun NewsFeedForm(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (url: String, title: String) -> Unit) {
    var url by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var region by remember { mutableStateOf(FeedCatalog.defaultRegion()) }
    var theme by remember { mutableStateOf<FeedTheme?>(null) }
    val added = remember { mutableStateMapOf<String, Boolean>() }
    val errors = remember { mutableStateMapOf<String, String>() }
    var checking by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    /** Vérifie puis ajoute ; [title] null : on garde le titre annoncé par le flux. */
    fun tryAdd(candidate: String, title: String?, onDone: (String?) -> Unit) {
        val clean = candidate.trim()
        RssConfig.validate(clean)?.let { onDone("✗ $it"); return }
        checking = clean
        scope.launch {
            when (val result = check(clean)) {
                is FeedCheck.Ok -> {
                    onAdd(clean, title ?: result.title)
                    added[clean] = true
                    onDone(null)
                }
                is FeedCheck.Failed -> onDone("✗ ${result.message}")
            }
            checking = null
        }
    }

    val isAdded = { u: String -> u in existing || added[u] == true }
    val feeds = if (query.isNotBlank()) FeedCatalog.search(query) else FeedCatalog.feeds(region, theme)

    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(tr("Ajouter des flux", "Add feeds"), style = MaterialTheme.typography.headlineSmall)
        OutlinedTextField(
            value = url,
            onValueChange = { url = it; status = null },
            label = { Text(tr("Adresse du flux", "Feed address")) },
            singleLine = true,
            shape = ROW_SHAPE,
            modifier = Modifier.fillMaxWidth().testTag("news-url"),
        )
        Button(
            onClick = {
                status = tr("Vérification…", "Checking…")
                tryAdd(url, null) { error -> status = error ?: tr("✓ Ajouté", "✓ Added").also { url = "" } }
            },
            enabled = checking == null && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(tr("Vérifier et ajouter", "Check and add")) }
        status?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        Spacer(Modifier.height(4.dp))
        CardLabel(tr("Catalogue", "Catalog"))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(tr("Chercher : foot, BBC, économie…", "Search: football, BBC, business…")) },
            singleLine = true,
            shape = ROW_SHAPE,
            modifier = Modifier.fillMaxWidth().testTag("catalog-search"),
        )
        if (query.isBlank()) {
            BubbleRow {
                FeedRegion.entries.forEach { r ->
                    Chip(r.label, selected = r == region, tag = "region-${r.name}") { region = r; theme = null }
                }
            }
            BubbleRow {
                Chip(tr("Tout", "All"), selected = theme == null, tag = "theme-ALL") { theme = null }
                FeedCatalog.themes(region).forEach { t ->
                    Chip(t.label, selected = t == theme, tag = "theme-${t.name}") { theme = t }
                }
            }
        }
        if (feeds.isEmpty()) {
            Text(tr("Aucun flux trouvé", "No feed found"), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            feeds.forEach { feed ->
                CatalogRow(
                    feed = feed,
                    showRegion = query.isNotBlank(),
                    showTheme = query.isNotBlank() || theme == null,
                    added = isAdded(feed.url),
                    loading = checking == feed.url,
                    error = errors[feed.url],
                    enabled = checking == null,
                ) {
                    errors.remove(feed.url)
                    tryAdd(feed.url, feed.label) { error -> if (error != null) errors[feed.url] = error }
                }
            }
        }
    }
}

@Composable
private fun BubbleRow(content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

/** Même bulle que les filtres de la page Actualités : pleine quand elle est choisie, translucide sinon. */
@Composable
private fun Chip(label: String, selected: Boolean, tag: String, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier
            .testTag(tag)
            .clip(CircleShape)
            .background(if (selected) colors.primary else cardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) colors.onPrimary else colors.onSurface,
            maxLines = 1,
        )
    }
}

@Composable
private fun CatalogRow(
    feed: CatalogFeed,
    showRegion: Boolean,
    showTheme: Boolean,
    added: Boolean,
    loading: Boolean,
    error: String?,
    enabled: Boolean,
    onAdd: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(cardBackground())
            .clickable(enabled = enabled && !added, onClick = onAdd)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(feed.source, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                if (showRegion) feed.region.flag else null,
                feed.name.ifEmpty { null },
                if (showTheme && feed.name != feed.theme.label) feed.theme.label else null,
            ).joinToString(" · ")
            if (details.isNotEmpty()) {
                Text(details, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.error) }
        }
        Box(
            Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(if (added) colors.secondaryContainer else colors.primary)
                .semantics { contentDescription = (if (added) tr("Déjà ajouté : ", "Already added: ") else tr("Ajouter ", "Add ")) + feed.label }
                .testTag("catalog-add"),
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.size(18.dp), color = colors.onPrimary, strokeWidth = 2.dp)
                added -> Text("✓", color = colors.onSecondaryContainer, fontWeight = FontWeight.Bold)
                else -> Text("+", color = colors.onPrimary, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsFeedSheet(existing: Set<String>, check: suspend (String) -> FeedCheck, onAdd: (String, String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        NewsFeedForm(existing, check, onAdd)
    }
}
