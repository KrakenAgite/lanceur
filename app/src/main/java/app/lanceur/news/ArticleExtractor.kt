package app.lanceur.news

import java.net.URI
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Morceaux du texte d'un article, dans l'ordre de la page. */
sealed interface ArticleBlock {
    data class Heading(val text: String) : ArticleBlock
    data class Paragraph(val text: String) : ArticleBlock
    data class Quote(val text: String) : ArticleBlock
    data class Bullet(val text: String) : ArticleBlock
    data class Image(val url: String, val caption: String?) : ArticleBlock
}

data class ReadableArticle(
    val title: String,
    val site: String?,
    val byline: String?,
    val published: Long?,
    val image: String?,
    val blocks: List<ArticleBlock>,
)

/**
 * Mode lecture : garde le texte principal d'une page d'article (titres, paragraphes, citations, listes, images
 * HTTPS) et jette le reste (menus, pubs, partage, commentaires…). Ne fait que découper du HTML déjà téléchargé.
 */
object ArticleExtractor {
    /** En dessous, c'est un chapeau d'article payant ou une page qui n'est pas un article. */
    private const val MIN_TEXT = 400

    private val CLUTTER_TAGS = "script, style, noscript, template, nav, header, footer, aside, form, iframe, button, svg, select, input, dialog"
    private val CLUTTER_NAMES = Regex(
        "share|social|comment|related|newsletter|promo|advert|\\bads?\\b|cookie|sidebar|menu|breadcrumb|subscribe|paywall|banner|popup|outbrain|taboola",
        RegexOption.IGNORE_CASE,
    )

    fun isReadable(article: ReadableArticle): Boolean =
        article.blocks.sumOf { block ->
            when (block) {
                is ArticleBlock.Paragraph -> block.text.length
                is ArticleBlock.Quote -> block.text.length
                is ArticleBlock.Bullet -> block.text.length
                else -> 0
            }
        } >= MIN_TEXT

    /** Null seulement si [url] n'est pas une adresse web. */
    fun extract(html: String, url: String): ReadableArticle? = if (web(url)) extract(Jsoup.parse(html, url), url) else null

    /** Octets bruts : l'encodage est lu dans la page (`<meta charset>`), UTF-8 sinon. */
    fun extract(bytes: ByteArray, url: String): ReadableArticle? =
        if (web(url)) extract(Jsoup.parse(bytes.inputStream(), null, url), url) else null

    private fun web(url: String): Boolean = runCatching { URI(url).scheme?.lowercase() }.getOrNull() in setOf("https", "http")

    private fun extract(doc: Document, url: String): ReadableArticle {
        val title = meta(doc, "og:title") ?: meta(doc, "twitter:title") ?: doc.selectFirst("h1")?.text()?.trim()?.ifEmpty { null } ?: doc.title().trim()
        val site = meta(doc, "og:site_name")
        val byline = (meta(doc, "author") ?: meta(doc, "article:author"))?.takeUnless { it.startsWith("http") }
        val published = (meta(doc, "article:published_time") ?: doc.selectFirst("[itemprop=datePublished]")?.let { it.attr("content").ifEmpty { it.attr("datetime") } }
            ?: doc.selectFirst("time[datetime]")?.attr("datetime"))?.let(::date)
        val image = (meta(doc, "og:image") ?: meta(doc, "twitter:image"))?.let { resolve(url, it) }?.takeIf(::secure)

        clean(doc)
        val root = mainElement(doc)
        val blocks = mutableListOf<ArticleBlock>()
        if (root != null) walk(root, title, blocks)
        // L'image d'en-tête n'est pas répétée, ni un même texte (mention répétée, intertitre en double)
        val seen = HashSet<ArticleBlock>()
        val body = blocks.filterNot { it is ArticleBlock.Image && it.url == image }.filter { seen.add(it) }
        return ReadableArticle(title, site, byline, published, image, body)
    }

    private fun meta(doc: Document, key: String): String? =
        doc.selectFirst("meta[property=$key], meta[name=$key]")?.attr("content")?.trim()?.ifEmpty { null }

    private fun clean(doc: Document) {
        doc.select(CLUTTER_TAGS).remove()
        doc.select("[hidden], [aria-hidden=true]").remove()
        doc.getAllElements()
            .filter { el -> el.tagName() !in setOf("html", "body", "article", "main") && CLUTTER_NAMES.containsMatchIn(el.className() + " " + el.id()) }
            // « Page avec barre latérale » qui enveloppe tout l'article (BBC) : on la garde
            .filterNot { el -> el.selectFirst("article, main") != null || textOf(el) > 1500 }
            .forEach { it.remove() }
    }

    /** `<article>` le plus fourni, sinon le bloc qui contient le plus de texte en paragraphes. */
    private fun mainElement(doc: Document): Element? {
        doc.select("article").maxByOrNull { textOf(it) }?.takeIf { textOf(it) > 0 }?.let { return it }
        val scores = HashMap<Element, Int>()
        doc.select("p").forEach { p ->
            val length = p.text().length
            if (length < 25) return@forEach
            p.parent()?.let { scores[it] = (scores[it] ?: 0) + length }
            p.parent()?.parent()?.let { scores[it] = (scores[it] ?: 0) + length / 2 }
        }
        return scores.maxByOrNull { it.value }?.key ?: doc.body()
    }

    private fun textOf(el: Element): Int = el.select("p").sumOf { it.text().length }

    private fun walk(el: Element, title: String, out: MutableList<ArticleBlock>) {
        when (el.tagName()) {
            "h1" -> Unit // le titre est affiché à part
            "h2", "h3", "h4", "h5", "h6" -> el.text().trim().takeIf { it.isNotEmpty() && it != title }?.let { out += ArticleBlock.Heading(it) }
            "p" -> {
                el.select("img").forEach { img -> image(img, null)?.let { out += it } }
                el.text().trim().takeIf { it.length > 3 && !mostlyLinks(el) }?.let { out += ArticleBlock.Paragraph(it) }
            }
            "blockquote" -> el.text().trim().takeIf { it.isNotEmpty() }?.let { out += ArticleBlock.Quote(it) }
            "li" -> el.text().trim().takeIf { it.isNotEmpty() && !mostlyLinks(el) }?.let { out += ArticleBlock.Bullet(it) }
            "figure" -> {
                val caption = el.selectFirst("figcaption")?.text()?.trim()?.ifEmpty { null }
                el.selectFirst("img")?.let { image(it, caption) }?.let { out += it }
            }
            "img" -> image(el, null)?.let { out += it }
            else -> el.children().forEach { walk(it, title, out) }
        }
    }

    /** Mots-clés, « à lire aussi », boutons de partage : des liens, presque pas de texte. */
    private fun mostlyLinks(el: Element): Boolean {
        val text = el.text().length
        val links = el.select("a").sumOf { it.text().length }
        return text > 0 && links >= text * 0.8
    }

    private fun image(img: Element, caption: String?): ArticleBlock.Image? {
        val width = img.attr("width").toIntOrNull()
        val height = img.attr("height").toIntOrNull()
        if ((width != null && width < 100) || (height != null && height < 60)) return null // pictos, pixels espions
        val src = listOf("src", "data-src", "data-lazy-src").map { img.absUrl(it) }.firstOrNull { it.isNotEmpty() && !it.startsWith("data:") } ?: return null
        if (!secure(src) || "placeholder" in src.lowercase()) return null
        return ArticleBlock.Image(src, caption)
    }

    private fun secure(url: String) = url.startsWith("https://", ignoreCase = true)

    private fun resolve(base: String, link: String): String? = runCatching { URI(base).resolve(link.trim()).toString() }.getOrNull()

    private fun date(text: String): Long? = runCatching { OffsetDateTime.parse(text.trim()).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { Instant.parse(text.trim()).toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDate.parse(text.trim().take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() }.getOrNull()
}
