package app.lanceur.news

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Article
import app.lanceur.ui.CardLabel
import app.lanceur.ui.CardShape
import app.lanceur.ui.cardBackground

class NewsActions(
    val filter: (String?) -> Unit = {},
    val add: () -> Unit = {},
    val remove: (String) -> Unit = {},
    val open: (String) -> Unit = {},
    val refresh: () -> Unit = {},
)

private val CARD_PADDING = 18.dp

/** Page Actualités : bulles de filtre, puis les articles en cartes (image du flux sous le titre). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsPage(
    chips: List<NewsChip>,
    filter: String?,
    articles: List<Article>,
    footer: String?,
    unavailable: Boolean,
    refreshing: Boolean,
    now: Long,
    actions: NewsActions,
    image: @Composable (String, Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to colors.surface.copy(alpha = 0.15f), 1f to colors.surface.copy(alpha = 0.65f)))
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            CardLabel("Actualités", Modifier.weight(1f))
            IconButton(onClick = actions.refresh) { Icon(Icons.Default.Refresh, contentDescription = "Actualiser") }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Bubble("Tout", selected = filter == null, failed = false, onClick = { actions.filter(null) }) }
            items(chips, key = { it.url }) { chip ->
                Bubble(chip.label, selected = filter == chip.url, failed = chip.failed, onClick = { actions.filter(chip.url) }, onRemove = { actions.remove(chip.url) })
            }
            item { Bubble("+", selected = false, failed = false, onClick = actions.add, modifier = Modifier.testTag("news-add")) }
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                chips.isEmpty() -> Message("Ajoute tes premiers flux", "Le Monde, Numerama, Korben… ou l'adresse de ton choix", onClick = actions.add)
                unavailable && articles.isEmpty() -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text("Flux indisponibles", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = actions.refresh) { Text("Réessayer") }
                }
                else -> LazyColumn(Modifier.fillMaxSize().testTag("news-list"), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(articles, key = { it.link }) { article -> ArticleCard(article, now, actions.open, image) }
                    footer?.let { text ->
                        item { Text(text, Modifier.fillMaxWidth().padding(8.dp), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(label: String, selected: Boolean, failed: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box(modifier) {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.secondaryContainer else cardBackground())
                .border(1.dp, if (selected) colors.secondaryContainer else colors.outlineVariant, CircleShape)
                .combinedClickable(onClick = onClick, onLongClick = onRemove?.let { { menu = true } })
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Text(
                if (failed) "$label ⚠" else label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) colors.onSecondaryContainer else colors.onSurface,
            )
        }
        if (onRemove != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Retirer ce flux") }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun ArticleCard(article: Article, now: Long, open: (String) -> Unit, image: @Composable (String, Modifier) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).clickable { open(article.link) }.padding(CARD_PADDING)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(article.source, Modifier.weight(1f, fill = false))
            article.published?.let {
                Text("  ·  ${Freshness.ago(it, now)}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(article.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
        article.image?.let { url ->
            Spacer(Modifier.height(12.dp))
            // Coins concentriques : 28 dp de la carte moins sa marge
            image(url, Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(28.dp - CARD_PADDING)))
        }
    }
}

@Composable
private fun Message(title: String, subtitle: String, onClick: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).clickable(onClick = onClick).padding(24.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
