package app.lanceur.search

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CalcProviderTest {
    @Test
    fun turns_calculations_into_a_single_result() = runTest {
        assertEquals(listOf(SearchResult.Calc("12*8", "96")), CalcProvider.search("12*8"))
        assertEquals(listOf(SearchResult.Calc("1/0", "—")), CalcProvider.search("1/0"))
        assertTrue(CalcProvider.search("chrome").isEmpty())
    }
}
