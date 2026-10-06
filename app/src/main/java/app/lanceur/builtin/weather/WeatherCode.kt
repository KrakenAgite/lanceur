package app.lanceur.builtin.weather

import app.lanceur.i18n.tr

/** Codes météo WMO d'Open-Meteo. */
object WeatherCode {
    fun icon(code: Int): String = when (code) {
        0 -> "☀"
        1, 2 -> "⛅"
        3 -> "☁"
        45, 48 -> "🌫"
        in 51..57 -> "🌦"
        in 61..67, in 80..82 -> "🌧"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⛈"
        else -> "🌡"
    }

    fun label(code: Int): String = when (code) {
        0 -> tr("Ensoleillé", "Sunny")
        1, 2 -> tr("Éclaircies", "Partly cloudy")
        3 -> tr("Couvert", "Overcast")
        45, 48 -> tr("Brouillard", "Fog")
        in 51..57 -> tr("Bruine", "Drizzle")
        in 61..67, in 80..82 -> tr("Pluie", "Rain")
        in 71..77, 85, 86 -> tr("Neige", "Snow")
        in 95..99 -> tr("Orage", "Thunderstorm")
        else -> tr("Météo", "Weather")
    }
}
