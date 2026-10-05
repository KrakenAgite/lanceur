package app.lanceur.builtin.weather

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
        0 -> "Ensoleillé"
        1, 2 -> "Éclaircies"
        3 -> "Couvert"
        45, 48 -> "Brouillard"
        in 51..57 -> "Bruine"
        in 61..67, in 80..82 -> "Pluie"
        in 71..77, 85, 86 -> "Neige"
        in 95..99 -> "Orage"
        else -> "Météo"
    }
}
