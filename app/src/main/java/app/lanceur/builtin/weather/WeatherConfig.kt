package app.lanceur.builtin.weather

import app.lanceur.builtin.WidgetData

/** `usePosition` : position approximative ; sinon `place`, la ville choisie. */
data class WeatherConfig(val usePosition: Boolean, val place: Place?)

/** Dernière réponse d'Open-Meteo, gardée pour l'affichage hors réseau. */
data class WeatherCache(val raw: String, val fetchedAt: Long)

object WeatherData {
    fun encode(config: WeatherConfig, cache: WeatherCache?): String = WidgetData.encode(
        buildMap {
            put("mode", if (config.usePosition) "position" else "city")
            config.place?.let { p ->
                put("name", p.name)
                put("region", p.region)
                put("lat", p.latitude.toString())
                put("lon", p.longitude.toString())
            }
            cache?.let {
                put("cache", it.raw)
                put("fetchedAt", it.fetchedAt.toString())
            }
        },
    )

    fun config(data: String?): WeatherConfig? {
        val v = WidgetData.decode(data)
        val mode = v["mode"] ?: return null
        val lat = v["lat"]?.toDoubleOrNull()
        val lon = v["lon"]?.toDoubleOrNull()
        val place = if (lat != null && lon != null) Place(v["name"].orEmpty(), v["region"].orEmpty(), lat, lon) else null
        return WeatherConfig(usePosition = mode == "position", place = place)
    }

    fun cache(data: String?): WeatherCache? {
        val v = WidgetData.decode(data)
        val raw = v["cache"] ?: return null
        return WeatherCache(raw, v["fetchedAt"]?.toLongOrNull() ?: return null)
    }
}
