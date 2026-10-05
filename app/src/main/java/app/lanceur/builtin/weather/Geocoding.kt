package app.lanceur.builtin.weather

import java.net.URLEncoder
import java.util.Locale
import org.json.JSONObject

data class Place(val name: String, val region: String, val latitude: Double, val longitude: Double) {
    val label: String get() = if (region.isBlank()) name else "$name, $region"
}

object Geocoding {
    /** Résultats de la recherche de ville d'Open-Meteo ; aucun résultat ou réponse illisible : liste vide. */
    fun parse(json: String): List<Place> = runCatching {
        val results = JSONObject(json).optJSONArray("results") ?: return emptyList()
        (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            Place(
                name = r.getString("name"),
                region = r.optString("admin1").ifBlank { r.optString("country") },
                latitude = r.getDouble("latitude"),
                longitude = r.getDouble("longitude"),
            )
        }
    }.getOrDefault(emptyList())
}

object WeatherQuery {
    /** Coordonnées arrondies à 0,01° (environ 1 km) : c'est tout ce que reçoit le service météo. */
    fun forecastUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${coord(latitude)}&longitude=${coord(longitude)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,precipitation_probability" +
            "&hourly=temperature_2m,weather_code,precipitation_probability" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=6"

    fun geocodeUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${encode(query.trim())}&count=8&language=fr"

    fun searchUrl(placeName: String): String = "https://www.google.com/search?q=${encode("météo $placeName")}"

    private fun coord(value: Double) = String.format(Locale.ROOT, "%.2f", value)

    private fun encode(text: String) = URLEncoder.encode(text, "UTF-8").replace("+", "%20")
}
