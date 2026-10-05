package app.lanceur.builtin.clocks

import app.lanceur.text.TextNormalizer
import java.time.Instant
import java.time.ZoneId

data class City(val id: String, val name: String, val zone: ZoneId)

/** Grandes villes, dont l'outre-mer français. L'identifiant est enregistré : ne jamais le changer. */
object Cities {
    private fun c(id: String, name: String, zone: String) = City(id, name, ZoneId.of(zone))

    val all: List<City> = listOf(
        c("paris", "Paris", "Europe/Paris"),
        c("londres", "Londres", "Europe/London"),
        c("lisbonne", "Lisbonne", "Europe/Lisbon"),
        c("madrid", "Madrid", "Europe/Madrid"),
        c("bruxelles", "Bruxelles", "Europe/Brussels"),
        c("amsterdam", "Amsterdam", "Europe/Amsterdam"),
        c("berlin", "Berlin", "Europe/Berlin"),
        c("rome", "Rome", "Europe/Rome"),
        c("zurich", "Zurich", "Europe/Zurich"),
        c("vienne", "Vienne", "Europe/Vienna"),
        c("stockholm", "Stockholm", "Europe/Stockholm"),
        c("oslo", "Oslo", "Europe/Oslo"),
        c("copenhague", "Copenhague", "Europe/Copenhagen"),
        c("helsinki", "Helsinki", "Europe/Helsinki"),
        c("varsovie", "Varsovie", "Europe/Warsaw"),
        c("prague", "Prague", "Europe/Prague"),
        c("athenes", "Athènes", "Europe/Athens"),
        c("istanbul", "Istanbul", "Europe/Istanbul"),
        c("kiev", "Kiev", "Europe/Kiev"),
        c("moscou", "Moscou", "Europe/Moscow"),
        c("reykjavik", "Reykjavik", "Atlantic/Reykjavik"),
        c("casablanca", "Casablanca", "Africa/Casablanca"),
        c("alger", "Alger", "Africa/Algiers"),
        c("tunis", "Tunis", "Africa/Tunis"),
        c("dakar", "Dakar", "Africa/Dakar"),
        c("abidjan", "Abidjan", "Africa/Abidjan"),
        c("lagos", "Lagos", "Africa/Lagos"),
        c("le-caire", "Le Caire", "Africa/Cairo"),
        c("nairobi", "Nairobi", "Africa/Nairobi"),
        c("johannesburg", "Johannesburg", "Africa/Johannesburg"),
        c("saint-denis", "Saint-Denis (La Réunion)", "Indian/Reunion"),
        c("dubai", "Dubaï", "Asia/Dubai"),
        c("riyad", "Riyad", "Asia/Riyadh"),
        c("teheran", "Téhéran", "Asia/Tehran"),
        c("karachi", "Karachi", "Asia/Karachi"),
        c("new-delhi", "New Delhi", "Asia/Kolkata"),
        c("mumbai", "Mumbai", "Asia/Kolkata"),
        c("katmandou", "Katmandou", "Asia/Kathmandu"),
        c("dacca", "Dacca", "Asia/Dhaka"),
        c("bangkok", "Bangkok", "Asia/Bangkok"),
        c("hanoi", "Hanoï", "Asia/Ho_Chi_Minh"),
        c("jakarta", "Jakarta", "Asia/Jakarta"),
        c("singapour", "Singapour", "Asia/Singapore"),
        c("hong-kong", "Hong Kong", "Asia/Hong_Kong"),
        c("pekin", "Pékin", "Asia/Shanghai"),
        c("shanghai", "Shanghai", "Asia/Shanghai"),
        c("taipei", "Taipei", "Asia/Taipei"),
        c("manille", "Manille", "Asia/Manila"),
        c("seoul", "Séoul", "Asia/Seoul"),
        c("tokyo", "Tokyo", "Asia/Tokyo"),
        c("perth", "Perth", "Australia/Perth"),
        c("brisbane", "Brisbane", "Australia/Brisbane"),
        c("sydney", "Sydney", "Australia/Sydney"),
        c("melbourne", "Melbourne", "Australia/Melbourne"),
        c("noumea", "Nouméa", "Pacific/Noumea"),
        c("auckland", "Auckland", "Pacific/Auckland"),
        c("papeete", "Papeete", "Pacific/Tahiti"),
        c("honolulu", "Honolulu", "Pacific/Honolulu"),
        c("anchorage", "Anchorage", "America/Anchorage"),
        c("vancouver", "Vancouver", "America/Vancouver"),
        c("los-angeles", "Los Angeles", "America/Los_Angeles"),
        c("san-francisco", "San Francisco", "America/Los_Angeles"),
        c("phoenix", "Phoenix", "America/Phoenix"),
        c("denver", "Denver", "America/Denver"),
        c("mexico", "Mexico", "America/Mexico_City"),
        c("chicago", "Chicago", "America/Chicago"),
        c("houston", "Houston", "America/Chicago"),
        c("toronto", "Toronto", "America/Toronto"),
        c("montreal", "Montréal", "America/Toronto"),
        c("new-york", "New York", "America/New_York"),
        c("miami", "Miami", "America/New_York"),
        c("la-havane", "La Havane", "America/Havana"),
        c("bogota", "Bogota", "America/Bogota"),
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
        return if (q.isEmpty()) all else all.filter { TextNormalizer.fold(it.name).contains(q) }
    }

    /** Ville du téléphone : même fuseau, sinon même décalage en ce moment, sinon Paris. */
    fun home(zone: ZoneId = ZoneId.systemDefault(), now: Instant = Instant.now()): City =
        all.firstOrNull { it.zone == zone }
            ?: all.firstOrNull { it.zone.rules.getOffset(now) == zone.rules.getOffset(now) }
            ?: all.first()
}
