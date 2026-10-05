package app.lanceur.builtin.rss

import app.lanceur.builtin.Freshness
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

class RssSource(private val network: Network) {
    suspend fun check(url: String): FeedCheck {
        RssConfig.validate(url)?.let { return FeedCheck.Failed(it) }
        return when (val result = network.get(url.trim())) {
            is NetResult.Failed -> FeedCheck.Failed("Flux injoignable")
            is NetResult.Ok -> withContext(Dispatchers.Default) { Feed.parse(result.bytes, hostOf(url)) }?.let { FeedCheck.Ok(it.title, it.articles.size) }
                ?: FeedCheck.Failed("Pas un flux RSS/Atom")
        }
    }

    /** Flux téléchargés en parallèle si le cache a plus de 30 min. Un flux en erreur est ignoré. Renvoie (échec, cache). */
    suspend fun refresh(config: RssConfig, cache: RssCache?, now: Long): Pair<Boolean, RssCache?> {
        if (!Freshness.isStale(cache?.fetchedAt, now)) return false to cache
        val results = coroutineScope {
            config.urls.map { url ->
                // Lecture du XML hors du fil principal
                async { (network.get(url) as? NetResult.Ok)?.let { withContext(Dispatchers.Default) { Feed.parse(it.bytes, hostOf(url)) } } }
            }.awaitAll()
        }.filterNotNull()
        if (results.isEmpty()) return true to cache
        return false to RssCache(withContext(Dispatchers.Default) { Feed.merge(results, KEPT) }, now)
    }

    private fun hostOf(url: String) = runCatching { java.net.URI(url).host.removePrefix("www.") }.getOrNull().orEmpty()

    private companion object {
        const val KEPT = 20
    }
}
