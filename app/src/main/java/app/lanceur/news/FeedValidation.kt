package app.lanceur.news

import app.lanceur.builtin.rss.FeedCheck
import app.lanceur.i18n.tr
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

data class InvalidFeed(val url: String, val title: String, val reason: String)

/** « Vérifier mes flux » : chaque flux est testé, tous en même temps ; on garde la liste de ceux qui échouent. */
object FeedValidation {
    suspend fun invalid(feeds: List<Pair<String, String>>, check: suspend (String) -> FeedCheck): List<InvalidFeed> = coroutineScope {
        feeds.map { (url, title) ->
            async {
                when (val result = runCatching { check(url) }.getOrElse { FeedCheck.Failed(tr("Erreur", "Error")) }) {
                    is FeedCheck.Ok -> null
                    is FeedCheck.Failed -> InvalidFeed(url, title, result.message)
                }
            }
        }.awaitAll().filterNotNull()
    }
}
