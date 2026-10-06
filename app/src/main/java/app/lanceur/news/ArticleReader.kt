package app.lanceur.news

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Article
import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
import app.lanceur.ui.CardLabel
import app.lanceur.ui.CardShape
import app.lanceur.ui.cardBackground
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val IMAGE_SHAPE = RoundedCornerShape(20.dp)
private val SIDE = 22.dp

/**
 * Mode lecture : l'article du flux, téléchargé et nettoyé, dans le thème du lanceur.
 * [image] affiche une image à sa taille naturelle (largeur de l'écran) ; null tant qu'elle charge.
 */
@Composable
fun ArticleReader(
    article: Article,
    state: ReaderState,
    now: Long,
    image: @Composable (url: String, modifier: Modifier) -> Unit,
    onBack: () -> Unit,
    onOpenSite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(colors.surface).systemBarsPadding().testTag("reader")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = onBack, modifier = Modifier.testTag("reader-back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr("Retour", "Back"))
            }
            CardLabel(
                (state as? ReaderState.Ready)?.article?.site ?: article.source,
                Modifier.weight(1f).padding(horizontal = 12.dp),
            )
            FilledTonalIconButton(
                onClick = onOpenSite,
                modifier = Modifier.testTag("reader-open").semantics { contentDescription = tr("Ouvrir sur le site", "Open on the website") },
            ) { Text("↗", style = MaterialTheme.typography.titleMedium) }
        }
        LazyColumn(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            when (state) {
                ReaderState.Loading -> item { Skeleton() }
                is ReaderState.NotReadable -> item { NotReadable(article, state.reason, now, image, onOpenSite) }
                is ReaderState.Ready -> {
                    val a = state.article
                    item { Header(a.title.ifBlank { article.title }, a.byline, a.published ?: article.published, now) }
                    (a.image ?: article.image)?.let { url -> item { image(url, Modifier.column().padding(bottom = 20.dp).clip(IMAGE_SHAPE)) } }
                    items(a.blocks) { Block(it, image) }
                    item {
                        FilledTonalButton(onClick = onOpenSite, modifier = Modifier.column().padding(top = 16.dp, bottom = 32.dp)) {
                            Text(tr("Lire sur le site", "Read on the website"))
                        }
                    }
                }
            }
        }
    }
}

/** Colonne de lecture : marges confortables, pas plus large qu'une page de livre sur tablette. */
private fun Modifier.column(): Modifier = this.widthIn(max = 680.dp).fillMaxWidth().padding(horizontal = SIDE)

private val DATE get() = DateTimeFormatter.ofPattern(tr("d MMMM yyyy 'à' HH:mm", "MMMM d, yyyy 'at' HH:mm"), L10n.locale)

@Composable
private fun Header(title: String, byline: String?, published: Long?, now: Long) {
    Column(Modifier.column().padding(top = 8.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = 1.25.em)
        val meta = listOfNotNull(
            byline,
            published?.let { DATE.format(Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())) + " · " + Freshness.ago(it, now) },
        ).joinToString(" · ")
        if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Block(block: ArticleBlock, image: @Composable (String, Modifier) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val body = MaterialTheme.typography.bodyLarge
    when (block) {
        is ArticleBlock.Heading -> Text(
            block.text,
            Modifier.column().padding(top = 14.dp, bottom = 10.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        is ArticleBlock.Paragraph -> Text(block.text, Modifier.column().padding(bottom = 16.dp), style = body, lineHeight = 1.6.em)
        is ArticleBlock.Quote -> Row(Modifier.column().padding(vertical = 6.dp).padding(bottom = 12.dp).height(IntrinsicSize.Min)) {
            Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(colors.primary))
            Text(
                block.text,
                Modifier.padding(start = 14.dp),
                style = body,
                fontStyle = FontStyle.Italic,
                lineHeight = 1.55.em,
                color = colors.onSurfaceVariant,
            )
        }
        is ArticleBlock.Bullet -> Row(Modifier.column().padding(bottom = 8.dp)) {
            Text("•", style = body, color = colors.primary, modifier = Modifier.padding(end = 10.dp))
            Text(block.text, style = body, lineHeight = 1.5.em)
        }
        is ArticleBlock.Image -> Column(Modifier.column().padding(vertical = 8.dp).padding(bottom = 12.dp)) {
            image(block.url, Modifier.fillMaxWidth().clip(IMAGE_SHAPE))
            block.caption?.let {
                Text(it, Modifier.padding(top = 6.dp), style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
    }
}

/** Pendant le chargement : la silhouette de l'article, en formes grises. */
@Composable
private fun Skeleton() {
    val bone = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    Column(Modifier.column().padding(top = 8.dp).testTag("reader-loading"), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(30.dp).clip(RoundedCornerShape(8.dp)).background(bone))
        Box(Modifier.fillMaxWidth(0.7f).height(30.dp).clip(RoundedCornerShape(8.dp)).background(bone))
        Box(Modifier.fillMaxWidth(0.4f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(bone))
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(IMAGE_SHAPE).background(bone))
        repeat(6) { i ->
            Box(Modifier.fillMaxWidth(if (i % 3 == 2) 0.6f else 1f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(bone))
        }
    }
}

@Composable
private fun NotReadable(article: Article, reason: String?, now: Long, image: @Composable (String, Modifier) -> Unit, onOpenSite: () -> Unit) {
    Column(Modifier.column()) {
        Header(article.title, null, article.published, now)
        article.image?.let { image(it, Modifier.fillMaxWidth().padding(bottom = 20.dp).clip(IMAGE_SHAPE)) }
        Column(
            Modifier.fillMaxWidth().clip(CardShape).background(cardBackground()).padding(24.dp).testTag("reader-unavailable"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(tr("Lecture impossible ici", "Can't read it here"), style = MaterialTheme.typography.titleMedium)
            Text(
                reason ?: tr(
                    "Ce site réserve l'article à ses abonnés ou bloque le mode lecture.",
                    "This site keeps the article for subscribers or blocks reader mode.",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Button(onClick = onOpenSite) { Text(tr("Ouvrir sur le site", "Open on the website")) }
        }
    }
}

/** Image à sa taille naturelle : rien tant qu'elle charge, ni si elle échoue (pas de trou dans le texte). */
@Composable
fun ReaderImage(url: String, loader: ImageLoader, modifier: Modifier = Modifier) {
    val width = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
    val bitmap by produceState<ImageBitmap?>(null, url) { value = loader.load(url, width) }
    bitmap?.let {
        Image(
            it,
            contentDescription = null,
            modifier = modifier.aspectRatio((it.width.toFloat() / it.height).coerceIn(0.5f, 3f)),
            contentScale = ContentScale.Crop,
        )
    }
}
