package app.lanceur.news

import app.lanceur.net.NetResult
import app.lanceur.net.NetRules
import app.lanceur.net.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface ReaderState {
    data object Loading : ReaderState
    data class Ready(val article: ReadableArticle) : ReaderState

    /** Page payante, protégée ou qui n'est pas un article : on propose le site. */
    data class NotReadable(val reason: String?) : ReaderState
}

/** Télécharge la page d'un article (par `Network` : HTTPS, sans cookie) et en garde le texte. */
class ArticleSource(private val network: Network) {
    private var last: Pair<String, ReadableArticle>? = null

    suspend fun read(url: String): ReaderState {
        last?.takeIf { it.first == url }?.let { return ReaderState.Ready(it.second) }
        return when (val result = network.get(url, NetRules.MAX_PAGE_BYTES)) {
            is NetResult.Failed -> ReaderState.NotReadable(result.reason)
            is NetResult.Ok -> {
                val article = withContext(Dispatchers.Default) { runCatching { ArticleExtractor.extract(result.bytes, url) }.getOrNull() }
                if (article != null && ArticleExtractor.isReadable(article)) {
                    last = url to article
                    ReaderState.Ready(article)
                } else {
                    ReaderState.NotReadable(null)
                }
            }
        }
    }
}
