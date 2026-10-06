package app.lanceur.news

import app.lanceur.i18n.tr
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    /** Mode lecture ; sans lui, l'article s'ouvre dans le navigateur. */
    val read: ((Article) -> Unit)? = null,
    val refresh: () -> Unit = {},
)

/** Mêmes marges et espacements que la page de widgets ; coins d'image concentriques à ceux des cartes. */
private val CARD_PADDING = 20.dp
private val GAP = 14.dp
private val IMAGE_SHAPE = RoundedCornerShape(28.dp - CARD_PADDING / 2)

/**
 * Page Actualités, au thème du résumé du jour : en-tête « ACTUALITÉS » et date, bulles de filtre translucides, article
 * « à la une » mis en avant, puis les autres en cartes compactes (image du flux sous le titre).
 */
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
    dateLabel: String = "",
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            // Même dégradé que la page de widgets : le haut laisse voir le fond d'écran, le bas reste lisible
            .background(Brush.verticalGradient(0f to colors.surface.copy(alpha = 0.35f), 0.3f to colors.surface.copy(alpha = 0.45f), 1f to colors.surface.copy(alpha = 0.7f)))
            .systemBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Ombre douce : le titre et la date restent lisibles sur un fond d'écran clair ou chargé
            val dark = isSystemInDarkTheme()
            val shadow = Shadow(color = (if (dark) Color.Black else Color.White).copy(alpha = 0.5f), offset = Offset(0f, 2f), blurRadius = 10f)
            Column(Modifier.weight(1f)) {
                Text(
                    tr("ACTUALITÉS", "NEWS"),
                    style = MaterialTheme.typography.labelLarge.copy(shadow = shadow),
                    color = colors.primaryFixed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
                if (dateLabel.isNotEmpty()) {
                    Text(
                        dateLabel,
                        style = MaterialTheme.typography.headlineMedium.copy(shadow = shadow),
                        color = if (dark) Color.White else Color.Black,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            FilledTonalIconButton(onClick = actions.refresh) { Icon(Icons.Default.Refresh, contentDescription = tr("Actualiser", "Refresh")) }
        }
        if (chips.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { Bubble(tr("Tout", "All"), selected = filter == null, failed = false, onClick = { actions.filter(null) }) }
                items(chips, key = { it.url }) { chip ->
                    Bubble(chip.label, selected = filter == chip.url, failed = chip.failed, onClick = { actions.filter(chip.url) }, onRemove = { actions.remove(chip.url) })
                }
                item { AddBubble(actions.add) }
            }
        } else {
            Spacer(Modifier.height(12.dp))
        }
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = actions.refresh, modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                chips.isEmpty() -> EmptyNewsCard(actions.add)
                unavailable && articles.isEmpty() -> Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Column(
                        Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(tr("Flux indisponibles", "Feeds unavailable"), style = MaterialTheme.typography.titleMedium)
                        Text(tr("Vérifie ta connexion", "Check your connection"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = actions.refresh) { Text(tr("Réessayer", "Retry")) }
                    }
                }
                else -> LazyColumn(
                    Modifier.fillMaxSize().testTag("news-list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(GAP),
                ) {
                    itemsIndexed(articles, key = { _, a -> a.link }) { index, article ->
                        ArticleCard(article, featured = index == 0, now = now, open = { actions.read?.invoke(article) ?: actions.open(article.link) }, image = image)
                    }
                    footer?.let { text ->
                        item {
                            Text(
                                text,
                                Modifier.fillMaxWidth().padding(top = 4.dp),
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.onSurfaceVariant,
                                textAlign = TextAlign.Center,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Bulle translucide comme les cartes ; choisie : remplie en couleur primaire. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(label: String, selected: Boolean, failed: Boolean, onClick: () -> Unit, onRemove: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .clip(CircleShape)
                .background(if (selected) colors.primary else cardBackground())
                .combinedClickable(onClick = onClick, onLongClick = onRemove?.let { { menu = true } })
                .padding(horizontal = 18.dp, vertical = 10.dp),
        ) {
            Text(
                if (failed) "$label ⚠" else label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) colors.onPrimary else colors.onSurface,
            )
        }
        if (onRemove != null) {
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(tr("Retirer ce flux", "Remove this feed")) }, onClick = { menu = false; onRemove() })
            }
        }
    }
}

@Composable
private fun AddBubble(onClick: () -> Unit) {
    Box(
        Modifier
            .testTag("news-add")
            .clip(CircleShape)
            .background(cardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Add, contentDescription = tr("Ajouter un flux", "Add a feed"), modifier = Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/** Le premier article est « à la une » : titre plus grand, image plus haute. */
@Composable
private fun ArticleCard(article: Article, featured: Boolean, now: Long, open: () -> Unit, image: @Composable (String, Modifier) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(cardBackground())
            .clickable(onClick = open)
            .padding(CARD_PADDING),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardLabel(article.source, Modifier.weight(1f, fill = false))
            article.published?.let {
                Text(" · ${Freshness.ago(it, now)}", style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant, maxLines = 1)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            article.title,
            style = if (featured) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            maxLines = if (featured) 4 else 3,
            overflow = TextOverflow.Ellipsis,
        )
        article.image?.let { url ->
            Spacer(Modifier.height(14.dp))
            image(url, Modifier.fillMaxWidth().aspectRatio(if (featured) 4f / 3f else 16f / 9f).clip(IMAGE_SHAPE))
        }
    }
}

/** Même allure que « Ajoute ton premier widget » : cadre en pointillés, toucher pour ajouter. */
@Composable
private fun EmptyNewsCard(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(CardShape)
                .background(cardBackground())
                .drawBehind {
                    drawRoundRect(
                        color = colors.outline,
                        style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))),
                        cornerRadius = CornerRadius(28.dp.toPx()),
                    )
                }
                .clickable(onClick = onAdd)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(tr("Ajoute tes premiers flux", "Add your first feeds"), style = MaterialTheme.typography.titleMedium)
            Text(tr("Le Monde, Numerama, Korben… ou l'adresse de ton choix", "BBC, The Verge, Le Monde… or any address you like"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Apparition en fondu d'une image chargée (utilisé par `NewsImage`). */
@Composable
internal fun fadeIn(loaded: Boolean): Float {
    val alpha by animateFloatAsState(if (loaded) 1f else 0f, animationSpec = tween(250), label = "image")
    return alpha
}
