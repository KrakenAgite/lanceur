package app.lanceur.builtin.clocks

import app.lanceur.i18n.tr
import app.lanceur.text.TextNormalizer
import java.time.Instant
import java.time.ZoneId

/** [fr] et [en] : noms affichés ; [en] seulement quand il diffère. */
data class City(val id: String, val fr: String, val zone: ZoneId, val en: String? = null) {
    val name: String get() = en?.let { tr(fr, it) } ?: fr
}

/** Grandes villes, dont l'outre-mer français. L'identifiant est enregistré : ne jamais le changer. */
object Cities {
    private fun c(id: String, name: String, zone: String, en: String? = null) = City(id, name, ZoneId.of(zone), en)

    val all: List<City> = listOf(
        c("paris", "Paris", "Europe/Paris"),
        c("londres", "Londres", "Europe/London", en = "London"),
        c("lisbonne", "Lisbonne", "Europe/Lisbon", en = "Lisbon"),
        c("madrid", "Madrid", "Europe/Madrid"),
        c("bruxelles", "Bruxelles", "Europe/Brussels", en = "Brussels"),
        c("amsterdam", "Amsterdam", "Europe/Amsterdam"),
        c("berlin", "Berlin", "Europe/Berlin"),
        c("rome", "Rome", "Europe/Rome"),
        c("zurich", "Zurich", "Europe/Zurich"),
        c("vienne", "Vienne", "Europe/Vienna", en = "Vienna"),
        c("stockholm", "Stockholm", "Europe/Stockholm"),
        c("oslo", "Oslo", "Europe/Oslo"),
        c("copenhague", "Copenhague", "Europe/Copenhagen", en = "Copenhagen"),
        c("helsinki", "Helsinki", "Europe/Helsinki"),
        c("varsovie", "Varsovie", "Europe/Warsaw", en = "Warsaw"),
        c("prague", "Prague", "Europe/Prague"),
        c("athenes", "Athènes", "Europe/Athens", en = "Athens"),
        c("istanbul", "Istanbul", "Europe/Istanbul"),
        c("kiev", "Kiev", "Europe/Kiev", en = "Kyiv"),
        c("moscou", "Moscou", "Europe/Moscow", en = "Moscow"),
        c("reykjavik", "Reykjavik", "Atlantic/Reykjavik"),
        c("casablanca", "Casablanca", "Africa/Casablanca"),
        c("alger", "Alger", "Africa/Algiers", en = "Algiers"),
        c("tunis", "Tunis", "Africa/Tunis"),
        c("dakar", "Dakar", "Africa/Dakar"),
        c("abidjan", "Abidjan", "Africa/Abidjan"),
        c("lagos", "Lagos", "Africa/Lagos"),
        c("le-caire", "Le Caire", "Africa/Cairo", en = "Cairo"),
        c("nairobi", "Nairobi", "Africa/Nairobi"),
        c("johannesburg", "Johannesburg", "Africa/Johannesburg"),
        c("saint-denis", "Saint-Denis (La Réunion)", "Indian/Reunion", en = "Saint-Denis (Réunion)"),
        c("dubai", "Dubaï", "Asia/Dubai", en = "Dubai"),
        c("riyad", "Riyad", "Asia/Riyadh", en = "Riyadh"),
        c("teheran", "Téhéran", "Asia/Tehran", en = "Tehran"),
        c("karachi", "Karachi", "Asia/Karachi"),
        c("new-delhi", "New Delhi", "Asia/Kolkata"),
        c("mumbai", "Mumbai", "Asia/Kolkata"),
        c("katmandou", "Katmandou", "Asia/Kathmandu", en = "Kathmandu"),
        c("dacca", "Dacca", "Asia/Dhaka", en = "Dhaka"),
        c("bangkok", "Bangkok", "Asia/Bangkok"),
        c("hanoi", "Hanoï", "Asia/Ho_Chi_Minh", en = "Hanoi"),
        c("jakarta", "Jakarta", "Asia/Jakarta"),
        c("singapour", "Singapour", "Asia/Singapore", en = "Singapore"),
        c("hong-kong", "Hong Kong", "Asia/Hong_Kong"),
        c("pekin", "Pékin", "Asia/Shanghai", en = "Beijing"),
        c("shanghai", "Shanghai", "Asia/Shanghai"),
        c("taipei", "Taipei", "Asia/Taipei"),
        c("manille", "Manille", "Asia/Manila", en = "Manila"),
        c("seoul", "Séoul", "Asia/Seoul", en = "Seoul"),
        c("tokyo", "Tokyo", "Asia/Tokyo"),
        c("perth", "Perth", "Australia/Perth"),
        c("brisbane", "Brisbane", "Australia/Brisbane"),
        c("sydney", "Sydney", "Australia/Sydney"),
        c("melbourne", "Melbourne", "Australia/Melbourne"),
        c("noumea", "Nouméa", "Pacific/Noumea", en = "Noumea"),
        c("auckland", "Auckland", "Pacific/Auckland"),
        c("papeete", "Papeete", "Pacific/Tahiti"),
        c("honolulu", "Honolulu", "Pacific/Honolulu"),
        c("anchorage", "Anchorage", "America/Anchorage"),
        c("vancouver", "Vancouver", "America/Vancouver"),
        c("los-angeles", "Los Angeles", "America/Los_Angeles"),
        c("san-francisco", "San Francisco", "America/Los_Angeles"),
        c("phoenix", "Phoenix", "America/Phoenix"),
        c("denver", "Denver", "America/Denver"),
        c("mexico", "Mexico", "America/Mexico_City", en = "Mexico City"),
        c("chicago", "Chicago", "America/Chicago"),
        c("houston", "Houston", "America/Chicago"),
        c("toronto", "Toronto", "America/Toronto"),
        c("montreal", "Montréal", "America/Toronto", en = "Montreal"),
        c("new-york", "New York", "America/New_York"),
        c("miami", "Miami", "America/New_York"),
        c("la-havane", "La Havane", "America/Havana", en = "Havana"),
        c("bogota", "Bogota", "America/Bogota", en = "Bogotá"),
        c("lima", "Lima", "America/Lima"),
        c("fort-de-france", "Fort-de-France", "America/Martinique"),
        c("pointe-a-pitre", "Pointe-à-Pitre", "America/Guadeloupe"),
        c("cayenne", "Cayenne", "America/Cayenne"),
        c("santiago", "Santiago", "America/Santiago"),
        c("buenos-aires", "Buenos Aires", "America/Argentina/Buenos_Aires"),
        c("sao-paulo", "São Paulo", "America/Sao_Paulo"),
        c("rio", "Rio de Janeiro", "America/Sao_Paulo"),
    )

    private val byIdMap = all.associateBy { it.id }

    fun byId(id: String): City? = byIdMap[id]

    fun search(query: String): List<City> {
        val q = TextNormalizer.fold(query.trim())
        return if (q.isEmpty()) all else all.filter { city -> listOfNotNull(city.fr, city.en).any { TextNormalizer.fold(it).contains(q) } }
    }

    /** Ville du téléphone : même fuseau, sinon même décalage en ce moment, sinon Paris. */
    fun home(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): City =
        all.firstOrNull { it.zone == zone }
            ?: all.firstOrNull { it.zone.rules.getOffset(now) == zone.rules.getOffset(now) }
            ?: all.first()
}
