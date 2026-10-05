package app.lanceur.builtin.rss

import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

data class Article(val title: String, val link: String, val source: String, val published: Long?)

data class FeedResult(val title: String, val articles: List<Article>)

sealed interface FeedCheck {
    data class Ok(val title: String, val count: Int) : FeedCheck
    data class Failed(val message: String) : FeedCheck
}

/** RSS 2.0 (et RDF) et Atom, sans réseau. Les DTD et entités externes sont refusées. */
object Feed {
    fun parse(bytes: ByteArray, fallbackTitle: String): FeedResult? = runCatching {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            isExpandEntityReferences = false
            // Selon le parseur (Android ou JVM), certaines options n'existent pas : on applique celles qui existent
            listOf(
                "http://apache.org/xml/features/disallow-doctype-decl" to true,
                "http://xml.org/sax/features/external-general-entities" to false,
                "http://xml.org/sax/features/external-parameter-entities" to false,
            ).forEach { (feature, value) -> runCatching { setFeature(feature, value) } }
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
        when (root.localName ?: root.nodeName) {
            "rss", "RDF" -> rss(root, fallbackTitle)
            "feed" -> atom(root, fallbackTitle)
            else -> null
        }
    }.getOrNull()

    private fun rss(root: Element, fallback: String): FeedResult {
        val channel = root.children("channel").firstOrNull() ?: root
        val title = clean(channel.childText("title")).ifBlank { fallback }
        val items = (channel.children("item") + root.children("item")).mapNotNull { item ->
            val link = item.childText("link").trim()
            if (link.isEmpty()) null
            else Article(clean(item.childText("title")).ifBlank { link }, link, title, date(item.childText("pubDate")) ?: date(item.childText("date")))
        }
        return FeedResult(title, items)
    }

    private fun atom(root: Element, fallback: String): FeedResult {
        val title = clean(root.childText("title")).ifBlank { fallback }
        val entries = root.children("entry").mapNotNull { entry ->
            val links = entry.children("link")
            val link = (links.firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } } ?: links.firstOrNull())
                ?.getAttribute("href")?.trim().orEmpty()
            if (link.isEmpty()) null
            else Article(clean(entry.childText("title")).ifBlank { link }, link, title, date(entry.childText("published")) ?: date(entry.childText("updated")))
        }
        return FeedResult(title, entries)
    }

    /** Plus récents d'abord (sans date à la fin), un seul article par lien. */
    fun merge(results: List<FeedResult>, limit: Int): List<Article> =
        results.flatMap { it.articles }
            .distinctBy { it.link }
            .sortedWith(compareBy<Article> { it.published == null }.thenByDescending { it.published ?: 0 })
            .take(limit)

    /** Balises retirées, entités décodées, espaces resserrés. */
    fun clean(text: String): String {
        val noTags = text.replace(Regex("<[^>]*>"), " ")
        val decoded = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(noTags) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) } ?: m.value
                e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) } ?: m.value
                else -> ENTITIES[e] ?: m.value
            }
        }
        return decoded.replace(Regex("\\s+"), " ").trim()
    }

    private val ENTITIES = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "rsquo" to "’", "lsquo" to "‘", "hellip" to "…", "laquo" to "«", "raquo" to "»")

    private fun date(text: String): Long? {
        val t = text.trim()
        if (t.isEmpty()) return null
        return runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { Instant.parse(t).toEpochMilli() }.getOrNull()
    }

    private fun Element.children(name: String): List<Element> {
        val nodes = childNodes
        return (0 until nodes.length).mapNotNull { nodes.item(it) as? Element }.filter { (it.localName ?: it.nodeName) == name }
    }

    private fun Element.childText(name: String): String = children(name).firstOrNull()?.textContent.orEmpty()
}
