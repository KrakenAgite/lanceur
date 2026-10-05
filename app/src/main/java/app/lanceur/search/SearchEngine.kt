package app.lanceur.search

import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Interroge toutes les sources en parallèle et émet, à chaque réponse, la fusion des résultats
 * dans l'ordre des fournisseurs. Une source en erreur est ignorée.
 */
class SearchEngine(
    private val providers: List<SearchProvider>,
    private val onError: (Throwable) -> Unit = {},
) {
    fun search(query: String): Flow<List<SearchResult>> = channelFlow {
        val q = query.trim()
        if (q.isEmpty()) {
            send(emptyList())
            return@channelFlow
        }
        val slots = arrayOfNulls<List<SearchResult>>(providers.size)
        val lock = Mutex()
        providers.forEachIndexed { index, provider ->
            launch {
                if (provider.delayMs > 0) delay(provider.delayMs)
                val found = try {
                    provider.search(q)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    onError(e)
                    emptyList()
                }
                lock.withLock {
                    slots[index] = found
                    send(slots.filterNotNull().flatten())
                }
            }
        }
    }
}
