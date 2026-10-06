package app.lanceur.i18n

import app.lanceur.builtin.Freshness
import app.lanceur.builtin.clocks.Cities
import app.lanceur.builtin.countdown.Countdown
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.rss.RssSuggestions
import app.lanceur.builtin.weather.WeatherCode
import app.lanceur.home.PageKind
import app.lanceur.news.NewsFeed
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class EnglishTest {
    private val zone = ZoneId.of("Europe/Paris")

    @After fun backToFrench() { L10n.lang = Lang.FR }

    @Test
    fun language_follows_the_locale() {
        assertEquals(Lang.EN, L10n.langOf(Locale.UK))
        assertEquals(Lang.EN, L10n.langOf(Locale.US))
        assertEquals(Lang.FR, L10n.langOf(Locale.FRANCE))
        assertEquals(Lang.FR, L10n.langOf(Locale.GERMANY))
        assertEquals(Lang.FR, L10n.langOf(null))
    }

    @Test
    fun labels_switch_language_at_runtime() {
        assertEquals("Accueil", PageKind.HOME.label)
        L10n.lang = Lang.EN
        assertEquals("Home", PageKind.HOME.label)
        assertEquals("News", PageKind.NEWS.label)
        assertEquals("Sunny", WeatherCode.label(0))
        assertEquals("London", Cities.byId("londres")!!.name)
        assertEquals("BBC News", RssSuggestions.all.first().name)
    }

    @Test
    fun cities_are_found_by_either_name() {
        assertEquals("londres", Cities.search("London").single().id)
        assertEquals("londres", Cities.search("Londres").single().id)
    }

    @Test
    fun freshness_in_english() {
        L10n.lang = Lang.EN
        val now = 10_000_000_000L
        assertEquals("just now", Freshness.ago(now, now))
        assertEquals("25 min ago", Freshness.ago(now - 25 * 60_000, now))
        assertEquals("Updated 2 h ago", Freshness.label(now - 2 * 3_600_000, now, zone))
    }

    @Test
    fun dates_in_english() {
        L10n.lang = Lang.EN
        val now = ZonedDateTime.of(2026, 10, 6, 9, 0, 0, 0, zone)
        assertEquals("Tuesday, October 6", NewsFeed.dateLabel(now.toInstant().toEpochMilli(), zone))
        val view = Countdown.of(CountdownConfig("Holidays", LocalDate.of(2026, 10, 18), null, 0), now)
        assertEquals("D-12", view.big)
        assertEquals("Sunday, October 18, 2026", view.dateText)
    }
}
