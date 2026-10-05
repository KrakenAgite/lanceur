package app.lanceur.builtin.weather

import app.lanceur.builtin.Freshness
import java.time.Instant
import java.time.ZoneId

enum class WeatherOutcome { FRESH, CACHED, FAILED, NO_POSITION }

sealed interface WeatherViewState {
    data object Loading : WeatherViewState
    data object Unavailable : WeatherViewState
    data object NoPosition : WeatherViewState
    data class Ready(val placeName: String, val forecast: Forecast, val freshness: String) : WeatherViewState
}

object WeatherView {
    /** Des données gardées s'affichent toujours, même si le dernier appel a échoué. */
    fun state(config: WeatherConfig?, cache: WeatherCache?, outcome: WeatherOutcome?, now: Long, zone: ZoneId): WeatherViewState {
        val forecast = cache?.let { Forecast.parse(it.raw, Instant.ofEpochMilli(now)) }
        if (cache != null && forecast != null) {
            val name = if (config?.usePosition == true) "Ma position" else config?.place?.name.orEmpty()
            return WeatherViewState.Ready(name, forecast, Freshness.label(cache.fetchedAt, now, zone))
        }
        return when (outcome) {
            WeatherOutcome.NO_POSITION -> WeatherViewState.NoPosition
            WeatherOutcome.FAILED -> WeatherViewState.Unavailable
            else -> WeatherViewState.Loading
        }
    }
}
