package app.lanceur.widgets

import app.lanceur.i18n.tr
import app.lanceur.builtin.BuiltinSlots
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.blockTouchesBelow

@Composable
fun WidgetPicker(
    groups: List<PickerGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    preview: @Composable (ProviderEntry) -> Unit,
    onPick: (ProviderEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouchesBelow()
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 16.dp),
    ) {
        Text(tr("Ajouter un widget", "Add a widget"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        TextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth().testTag("picker-filter"),
            placeholder = { Text(tr("Filtrer par appli ou widget", "Filter by app or widget")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent),
        )
        LazyColumn(Modifier.weight(1f).fillMaxWidth()) {
            if (groups.isEmpty()) item { HintText(tr("Aucun widget trouvé", "No widget found")) }
            groups.forEach { group ->
                item(key = "g:${group.packageName}:${group.entries.first().provider.userSerial}") {
                    SectionTitle(if (group.isWork) "${group.appLabel} (pro)" else group.appLabel)
                }
                items(group.entries, key = { "e:" + it.provider.encode() }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onPick(entry) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(96.dp, 64.dp), contentAlignment = Alignment.Center) { preview(entry) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(entry.widgetLabel, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                tr("Taille de départ : ", "Starting size: ") + startingSize(entry).shortLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Un widget intégré part de sa taille par défaut ; un widget Android, de celle que sa hauteur minimale suggère. */
private fun startingSize(entry: ProviderEntry): WidgetSize =
    BuiltinSlots.kindOf(entry.provider)?.defaultSize ?: WidgetSize.fromMinHeightDp(entry.minHeightDp)
