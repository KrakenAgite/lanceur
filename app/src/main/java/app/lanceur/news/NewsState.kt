package app.lanceur.news

import app.lanceur.builtin.WidgetData
import app.lanceur.builtin.rss.Article

/** Un flux de la page : son nom, ses derniers articles et l'état du dernier essai. */
data class FeedSnapshot(
    val url: String,
    val title: String,
    val articles: List<Article> = emptyList(),
    val fetchedAt: Long? = null,
    val failed: Boolean = false,
)

data class NewsState(val feeds: List<FeedSnapshot> = emptyList()) {
    fun add(url: String, title: String) = if (feeds.any { it.url == url }) this else copy(feeds = feeds + FeedSnapshot(url, title))

    fun remove(url: String) = copy(feeds = feeds.filterNot { it.url == url })

    /** Champs d'un article séparés par U+001E (jamais dans un titre : `Feed.clean` retire les caractères de contrôle). */
    fun encode(): String = WidgetData.encode(
        buildMap {
            put("feeds", WidgetData.list(feeds.map { it.url }))
            feeds.forEachIndexed { i, f ->
                put("title.$i", f.title)
                f.fetchedAt?.let { put("fetched.$i", it.toString()) }
                if (f.failed) put("failed.$i", "1")
                put("articles.$i", WidgetData.list(f.articles.map { a -> listOf(a.published?.toString().orEmpty(), a.image.orEmpty(), a.link, a.title).joinToString(FIELD) }))
            }
        },
    )

    companion object {
        private const val FIELD = "\u001E"

        fun decode(data: String?): NewsState {
            val v = WidgetData.decode(data)
            val urls = WidgetData.unlist(v["feeds"]).filter { it.isNotBlank() }
            return NewsState(
                urls.mapIndexed { i, url ->
                    FeedSnapshot(
                        url = url,
                        title = v["title.$i"].orEmpty(),
                        articles = WidgetData.unlist(v["articles.$i"]).mapNotNull { e ->
                            val p = e.split(FIELD)
                            if (p.size == 4) Article(p[3], p[2], "", p[0].toLongOrNull(), p[1].ifEmpty { null }) else null
                        },
                        fetchedAt = v["fetched.$i"]?.toLongOrNull(),
                        failed = v["failed.$i"] == "1",
                    )
                },
            )
        }

        /** Résultat d'une actualisation appliqué à l'état courant : un flux ajouté ou retiré entre-temps est respecté. */
        fun merge(current: NewsState, refreshed: NewsState): NewsState {
            val byUrl = refreshed.feeds.associateBy { it.url }
            return NewsState(current.feeds.map { byUrl[it.url] ?: it })
        }
    }
}
