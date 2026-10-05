package app.lanceur.builtin.rss

import app.lanceur.builtin.WidgetData
import app.lanceur.net.NetRules

data class RssConfig(val urls: List<String>) {
    companion object {
        const val MAX = 3

        /** Message d'erreur, ou `null` si l'adresse est acceptable. */
        fun validate(url: String): String? = if (NetRules.allowed(url)) null else "Adresse HTTPS requise"
    }
}

data class RssSuggestion(val name: String, val url: String)

object RssSuggestions {
    val all = listOf(
        RssSuggestion("Le Monde", "https://www.lemonde.fr/rss/une.xml"),
        RssSuggestion("France Info", "https://www.francetvinfo.fr/titres.rss"),
        RssSuggestion("Libération", "https://www.liberation.fr/arc/outboundfeeds/rss-all/?outputType=xml"),
        RssSuggestion("Numerama", "https://www.numerama.com/feed/"),
        RssSuggestion("Les Numériques", "https://www.lesnumeriques.com/rss.xml"),
        RssSuggestion("Korben", "https://korben.info/feed"),
    )
}

data class RssCache(val articles: List<Article>, val fetchedAt: Long)

object RssData {
    fun encode(config: RssConfig, cache: RssCache?): String = WidgetData.encode(
        buildMap {
            put("urls", WidgetData.list(config.urls))
            cache?.let { c ->
                put("fetchedAt", c.fetchedAt.toString())
                // Chaque article : date|source|lien|titre (le titre peut contenir « | »)
                put("articles", WidgetData.list(c.articles.map { "${it.published ?: ""}|${it.source.replace("|", " ")}|${it.link}|${it.title}" }))
            }
        },
    )

    fun config(data: String?): RssConfig? {
        val v = WidgetData.decode(data)
        val urls = WidgetData.unlist(v["urls"]).filter { it.isNotBlank() }
        return if (v.containsKey("urls")) RssConfig(urls) else null
    }

    fun cache(data: String?): RssCache? {
        val v = WidgetData.decode(data)
        val fetchedAt = v["fetchedAt"]?.toLongOrNull() ?: return null
        val articles = WidgetData.unlist(v["articles"]).mapNotNull { e ->
            val p = e.split('|', limit = 4)
            if (p.size == 4) Article(p[3], p[2], p[1], p[0].toLongOrNull()) else null
        }
        return RssCache(articles, fetchedAt)
    }
}

sealed interface RssViewState {
    data object Loading : RssViewState
    data object Unavailable : RssViewState
    data class Ready(val articles: List<Article>, val fetchedAt: Long, val failed: Boolean) : RssViewState
}

object RssView {
    fun state(cache: RssCache?, failed: Boolean): RssViewState = when {
        cache != null -> RssViewState.Ready(cache.articles, cache.fetchedAt, failed)
        failed -> RssViewState.Unavailable
        else -> RssViewState.Loading
    }
}
