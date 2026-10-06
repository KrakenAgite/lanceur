package app.lanceur.builtin.weather

import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONArray
import org.json.JSONObject

data class HourForecast(val label: String, val icon: String, val temp: Int)

data class DayForecast(val label: String, val icon: String, val min: Int, val max: Int, val rain: Int?)

data class Forecast(
    val temp: Int,
    val apparent: Int,
    val icon: String,
    val condition: String,
    val wind: Int,
    val rain: Int?,
    val min: Int,
    val max: Int,
    val hours: List<HourForecast>,
    val days: List<DayForecast>,
) {
    companion object {
        /** Heures et jours à l'heure locale de la ville (`utc_offset_seconds`) ; `null` si la réponse est inutilisable. */
        fun parse(json: String, now: Instant): Forecast? = runCatching {
            val root = JSONObject(json)
            val offset = ZoneOffset.ofTotalSeconds(root.getInt("utc_offset_seconds"))
            val localNow = now.atOffset(offset).toLocalDateTime()
            val current = root.getJSONObject("current")
            val hourly = root.getJSONObject("hourly")
            val daily = root.getJSONObject("daily")
            val hourTimes = hourly.getJSONArray("time").strings().map(LocalDateTime::parse)
            val hourTemps = hourly.getJSONArray("temperature_2m")
            val hourCodes = hourly.getJSONArray("weather_code")
            val firstHour = hourTimes.indexOfFirst { it.isAfter(localNow) }.takeIf { it >= 0 } ?: hourTimes.size
            val hours = (firstHour until minOf(firstHour + 6, hourTimes.size)).map { i ->
                HourForecast(tr("${hourTimes[i].hour} h", "${hourTimes[i].hour}:00"), WeatherCode.icon(hourCodes.getInt(i)), hourTemps.getDouble(i).roundToInt())
            }
            val dayTimes = daily.getJSONArray("time").strings().map(LocalDate::parse)
            val today = dayTimes.indexOf(localNow.toLocalDate()).coerceAtLeast(0)
            val dayCodes = daily.getJSONArray("weather_code")
            val maxs = daily.getJSONArray("temperature_2m_max")
            val mins = daily.getJSONArray("temperature_2m_min")
            val rains = daily.optJSONArray("precipitation_probability_max")
            val days = (today + 1 until minOf(today + 6, dayTimes.size)).map { i ->
                val name = dayTimes[i].dayOfWeek.getDisplayName(TextStyle.SHORT, L10n.locale).replaceFirstChar { it.titlecase(L10n.locale) }
                DayForecast(name, WeatherCode.icon(dayCodes.getInt(i)), mins.getDouble(i).roundToInt(), maxs.getDouble(i).roundToInt(), rains?.optIntOrNull(i))
            }
            val code = current.getInt("weather_code")
            Forecast(
                temp = current.getDouble("temperature_2m").roundToInt(),
                apparent = current.getDouble("apparent_temperature").roundToInt(),
                icon = WeatherCode.icon(code),
                condition = WeatherCode.label(code),
                wind = current.getDouble("wind_speed_10m").roundToInt(),
                rain = if (current.has("precipitation_probability") && !current.isNull("precipitation_probability")) current.getInt("precipitation_probability") else null,
                min = mins.getDouble(today).roundToInt(),
                max = maxs.getDouble(today).roundToInt(),
                hours = hours,
                days = days,
            )
        }.getOrNull()

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        private fun JSONArray.optIntOrNull(i: Int): Int? = if (i < length() && !isNull(i)) getInt(i) else null
    }
}
