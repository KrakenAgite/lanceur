package app.lanceur.builtin.rss

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.Freshness
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun RssCard(state: RssViewState, size: WidgetSize, now: Long, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 20.dp, vertical = 14.dp)) {
        CardLabel(tr("Actualités", "News"))
        Spacer(Modifier.height(6.dp))
        when (state) {
            RssViewState.Loading -> Text(tr("Chargement des flux…", "Loading feeds…"), color = colors.onSurfaceVariant)
            RssViewState.Unavailable -> Text(tr("Flux indisponibles", "Feeds unavailable"), color = colors.onSurfaceVariant)
            is RssViewState.Ready -> {
                val count = when (size) {
                    WidgetSize.SMALL -> 3
                    WidgetSize.MEDIUM -> 6
                    WidgetSize.LARGE -> 10
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.articles.isEmpty()) Text(tr("Aucun article", "No articles"), color = colors.onSurfaceVariant)
                    state.articles.take(count).forEach { a ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onOpen(a.link) }.padding(vertical = 6.dp)) {
                            Text(a.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(a.source.ifBlank { null }, a.published?.let { Freshness.ago(it, now) }).joinToString(" · "),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (state.failed) Text(tr("Hors ligne · ", "Offline · ") + Freshness.ago(state.fetchedAt, now), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}
