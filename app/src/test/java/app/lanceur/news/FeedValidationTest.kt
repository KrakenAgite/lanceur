package app.lanceur.news

import app.lanceur.builtin.rss.FeedCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FeedValidationTest {
    @Test
    fun every_feed_is_checked_and_only_the_failed_ones_are_reported_in_order() = runTest {
        val feeds = listOf("https://a/rss" to "A", "https://b/rss" to "B", "https://c/rss" to "C")
        val invalid = FeedValidation.invalid(feeds) { url ->
            delay(if (url.contains("a")) 50 else 10) // en parallèle : l'ordre de réponse ne compte pas
            if (url.contains("b")) FeedCheck.Ok("B", 3) else FeedCheck.Failed("Flux injoignable")
        }
        assertEquals(listOf(InvalidFeed("https://a/rss", "A", "Flux injoignable"), InvalidFeed("https://c/rss", "C", "Flux injoignable")), invalid)
    }

    @Test
    fun a_check_that_crashes_counts_as_invalid_without_stopping_the_others() = runTest {
        val invalid = FeedValidation.invalid(listOf("https://x" to "X", "https://y" to "Y")) { url ->
            if (url == "https://x") error("boum") else FeedCheck.Ok("Y", 1)
        }
        assertEquals(listOf("https://x"), invalid.map { it.url })
    }
}
