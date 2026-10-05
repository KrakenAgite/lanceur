package app.lanceur.builtin.weather

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WeatherTest {
    /** Réponse Open-Meteo réduite, au format réel (heures locales, `utc_offset_seconds`). */
    private val response: String = run {
        val hours = (0 until 48).map { h -> "\"2026-10-0${5 + h / 24}T%02d:00\"".format(h % 24) }
        val temps = (0 until 48).map { 10 + it % 10 }
        val codes = (0 until 48).map { if (it % 2 == 0) 0 else 61 }
        """
        {"latitude":47.22,"longitude":-1.56,"utc_offset_seconds":7200,"timezone":"Europe/Paris",
         "current":{"time":"2026-10-05T14:00","temperature_2m":16.4,"apparent_temperature":15.1,"weather_code":2,
                    "wind_speed_10m":12.3,"precipitation_probability":20},
         "hourly":{"time":[${hours.joinToString()}],"temperature_2m":[${temps.joinToString()}],
                   "weather_code":[${codes.joinToString()}],"precipitation_probability":[${(0 until 48).joinToString()}]},
         "daily":{"time":["2026-10-05","2026-10-06","2026-10-07","2026-10-08","2026-10-09","2026-10-10"],
                  "weather_code":[2,61,3,95,71,0],"temperature_2m_max":[17.4,15,14,13,9,18],
                  "temperature_2m_min":[9.2,8,7,6,1,10],"precipitation_probability_max":[20,80,30,90,60,0]}}
        """.trimIndent()
    }
    private val now = Instant.parse("2026-10-05T12:30:00Z") // 14:30 à Nantes

    @Test
    fun current_conditions_and_today() {
        val f = Forecast.parse(response, now)!!
        assertEquals(16, f.temp)
        assertEquals(15, f.apparent)
        assertEquals("⛅", f.icon)
        assertEquals("Éclaircies", f.condition)
        assertEquals(12, f.wind)
        assertEquals(20, f.rain)
        assertEquals(9, f.min)
        assertEquals(17, f.max)
    }

    @Test
    fun next_six_hours_and_five_days() {
        val f = Forecast.parse(response, now)!!
        assertEquals(listOf("15 h", "16 h", "17 h", "18 h", "19 h", "20 h"), f.hours.map { it.label })
        assertEquals(15, f.hours.first().temp)
        assertEquals("🌧", f.hours.first().icon)
        assertEquals(listOf("Mar.", "Mer.", "Jeu.", "Ven.", "Sam."), f.days.map { it.label })
        assertEquals(DayForecast("Jeu.", "⛈", 6, 13, 90), f.days[2])
    }

    @Test
    fun broken_responses_give_null() {
        assertNull(Forecast.parse("{}", now))
        assertNull(Forecast.parse("pas du json", now))
    }

    @Test
    fun weather_codes() {
        assertEquals("☀", WeatherCode.icon(0))
        assertEquals("🌫", WeatherCode.icon(45))
        assertEquals("🌦", WeatherCode.icon(53))
        assertEquals("🌧", WeatherCode.icon(81))
        assertEquals("❄", WeatherCode.icon(75))
        assertEquals("⛈", WeatherCode.icon(99))
        assertEquals("🌡", WeatherCode.icon(42))
        assertEquals("Orage", WeatherCode.label(95))
    }

    @Test
    fun geocoding_and_urls() {
        val places = Geocoding.parse(
            """{"results":[{"name":"Nantes","latitude":47.21725,"longitude":-1.55336,"admin1":"Pays de la Loire","country":"France"},
                           {"name":"Nant","latitude":44.02,"longitude":3.3,"country":"France"}]}""",
        )
        assertEquals(listOf("Nantes, Pays de la Loire", "Nant, France"), places.map { it.label })
        assertTrue(Geocoding.parse("""{"generationtime_ms":0.2}""").isEmpty())
        assertTrue(WeatherQuery.forecastUrl(47.21725, -1.55336).startsWith("https://api.open-meteo.com/v1/forecast?latitude=47.22&longitude=-1.55&"))
        assertEquals("https://geocoding-api.open-meteo.com/v1/search?name=Saint-%C3%89tienne&count=8&language=fr", WeatherQuery.geocodeUrl("Saint-Étienne"))
    }

    @Test
    fun data_round_trip_and_view_states() {
        val config = WeatherConfig(usePosition = false, place = Place("Nantes", "Pays de la Loire", 47.21, -1.55))
        val cache = WeatherCache(response, fetchedAt = now.toEpochMilli())
        assertEquals(config, WeatherData.config(WeatherData.encode(config, cache)))
        assertEquals(cache, WeatherData.cache(WeatherData.encode(config, cache)))
        assertNull(WeatherData.cache(WeatherData.encode(config, null)))
        val zone = ZoneId.of("Europe/Paris")
        val ready = WeatherView.state(config, cache, WeatherOutcome.CACHED, now.toEpochMilli(), zone)
        assertTrue(ready is WeatherViewState.Ready && ready.placeName == "Nantes")
        assertEquals(WeatherViewState.Loading, WeatherView.state(config, null, null, 0, zone))
        assertEquals(WeatherViewState.Unavailable, WeatherView.state(config, null, WeatherOutcome.FAILED, 0, zone))
        assertEquals(WeatherViewState.NoPosition, WeatherView.state(config.copy(usePosition = true), null, WeatherOutcome.NO_POSITION, 0, zone))
    }
}
