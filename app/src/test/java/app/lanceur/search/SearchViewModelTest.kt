package app.lanceur.search

import app.lanceur.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private fun provider(block: suspend (String) -> List<SearchResult>) = object : SearchProvider {
        override suspend fun search(query: String) = block(query)
    }

    @Test
    fun stale_results_are_dropped() = runTest(main.dispatcher) {
        val vm = SearchViewModel(
            SearchEngine(listOf(provider { q -> delay(if (q == "a") 500 else 10); listOf(SearchResult.Web(q)) })),
        )
        val seen = mutableListOf<List<SearchResult>>()
        backgroundScope.launch { vm.results.collect { seen += it } }

        vm.setQuery("a")
        advanceTimeBy(100)
        vm.setQuery("ab")
        advanceUntilIdle()

        assertEquals(listOf(SearchResult.Web("ab")), vm.results.value)
        assertTrue(seen.none { SearchResult.Web("a") in it })
    }

    @Test
    fun blank_query_clears_results() = runTest(main.dispatcher) {
        val vm = SearchViewModel(SearchEngine(listOf(provider { listOf(SearchResult.Web(it)) })))
        vm.setQuery("ab")
        advanceUntilIdle()
        vm.setQuery("   ")
        advanceUntilIdle()
        assertEquals(emptyList<SearchResult>(), vm.results.value)
    }

    @Test
    fun refresh_runs_the_query_again() = runTest(main.dispatcher) {
        var calls = 0
        val vm = SearchViewModel(SearchEngine(listOf(provider { calls++; listOf(SearchResult.Web(it)) })))
        vm.setQuery("x")
        advanceUntilIdle()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2, calls)
    }
}
