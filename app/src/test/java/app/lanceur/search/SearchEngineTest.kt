package app.lanceur.search

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchEngineTest {
    private fun provider(block: suspend (String) -> List<SearchResult>) = object : SearchProvider {
        override suspend fun search(query: String) = block(query)
    }

    @Test
    fun merges_in_provider_order_and_isolates_failures() = runTest {
        val errors = mutableListOf<Throwable>()
        val engine = SearchEngine(
            listOf(
                provider { delay(50); listOf(SearchResult.Web("lent")) },
                provider { error("boum") },
                provider { listOf(SearchResult.Setting("a", "Wi-Fi")) },
            ),
            onError = { errors += it },
        )
        val last = engine.search("x").toList().last()
        assertEquals(listOf(SearchResult.Web("lent"), SearchResult.Setting("a", "Wi-Fi")), last)
        assertEquals(1, errors.size)
    }

    @Test
    fun blank_query_gives_empty_results_without_calling_providers() = runTest {
        var called = false
        val engine = SearchEngine(listOf(provider { called = true; emptyList() }))
        assertEquals(listOf(emptyList<SearchResult>()), engine.search("   ").toList())
        assertFalse(called)
    }

    @Test
    fun query_is_trimmed_and_provider_delay_is_respected() = runTest {
        val seen = mutableListOf<String>()
        val slow = object : SearchProvider {
            override val delayMs = 80L
            override suspend fun search(query: String): List<SearchResult> {
                seen += query
                return emptyList()
            }
        }
        val start = currentTime
        SearchEngine(listOf(slow)).search("  ab ").toList()
        assertEquals(listOf("ab"), seen)
        assertTrue(currentTime - start >= 80)
    }
}
