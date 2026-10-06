package app.lanceur.news

import app.lanceur.builtin.Freshness
import app.lanceur.builtin.rss.Feed
import app.lanceur.net.NetResult
import app.lanceur.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/** Actualise les flux en parallèle ; un flux en erreur garde ses articles et est marqué en échec. */
class NewsSource(private val network: Network) {
    suspend fun refresh(state: NewsState, now: Long, force: Boolean): NewsState = coroutineScope {
        NewsState(
            state.feeds.map { feed ->
                async { if (!force && !Freshness.isStale(feed.fetchedAt, now)) feed else fetch(feed, now) }
            }.awaitAll(),
        )
    }

    private suspend fun fetch(feed: FeedSnapshot, now: Long): FeedSnapshot {
        val parsed = (network.get(feed.url) as? NetResult.Ok)?.let { ok -> withContext(Dispatchers.Default) { Feed.parse(ok.bytes, feed.title) } }
            ?: return feed.copy(failed = true)
        return feed.copy(
            title = parsed.title.ifBlank { feed.title },
            articles = parsed.articles.take(NewsFeed.PER_FEED),
            fetchedAt = now,
            failed = false,
        )
    }
}
