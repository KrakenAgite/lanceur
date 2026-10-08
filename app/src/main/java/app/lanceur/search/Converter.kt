package app.lanceur.search

import app.lanceur.builtin.clocks.Cities
import app.lanceur.builtin.clocks.City
import app.lanceur.i18n.tr
import app.lanceur.text.TextNormalizer
import java.math.BigDecimal
import java.math.MathContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max

/**
 * Conversions sans réseau : unités (« 5 miles en km », « 70°F ») et fuseaux horaires (« 15h à Tokyo »,
 * « heure à New York »). [Conversion.value] est ce qui est copié.
 */
object Converter {
    data class Conversion(val value: String, val detail: String)

    private enum class Kind { LENGTH, MASS, VOLUME, TEMPERATURE, SPEED, AREA, DATA, TIME, ENERGY }

    /**
     * [factor] : valeur de 1 unité dans l'unité de base de sa catégorie (sauf températures). [en] : symbole anglais
     * quand il diffère, lu à chaque affichage pour suivre la langue choisie.
     */
    private class Unit(val kind: Kind, private val fr: String, val factor: Double, vararg val names: String) {
        var en: String? = null
            private set

        val symbol: String get() = en?.let { tr(fr, it) } ?: fr
        val id: String get() = names.first()

        fun en(symbol: String) = apply { en = symbol }
    }

    private val units = listOf(
        Unit(Kind.LENGTH, "mm", 0.001, "mm", "millimetre"),
        Unit(Kind.LENGTH, "cm", 0.01, "cm", "centimetre"),
        Unit(Kind.LENGTH, "m", 1.0, "m", "metre", "meter"),
        Unit(Kind.LENGTH, "km", 1000.0, "km", "kilometre", "kilometer", "kms"),
        Unit(Kind.LENGTH, "in", 0.0254, "in", "inch", "inches", "pouce", "\""),
        Unit(Kind.LENGTH, "ft", 0.3048, "ft", "foot", "feet", "pied", "'"),
        Unit(Kind.LENGTH, "yd", 0.9144, "yd", "yard"),
        Unit(Kind.LENGTH, "mi", 1609.344, "mi", "mile"),
        Unit(Kind.LENGTH, "nmi", 1852.0, "nmi", "nm"),
        Unit(Kind.MASS, "mg", 0.000001, "mg", "milligramme", "milligram"),
        Unit(Kind.MASS, "g", 0.001, "g", "gramme", "gram", "gr"),
        Unit(Kind.MASS, "kg", 1.0, "kg", "kilo", "kilogramme", "kilogram", "kgs"),
        Unit(Kind.MASS, "t", 1000.0, "t", "tonne", "ton"),
        Unit(Kind.MASS, "oz", 0.028349523125, "oz", "once", "ounce"),
        Unit(Kind.MASS, "lb", 0.45359237, "lb", "lbs", "livre", "pound"),
        Unit(Kind.MASS, "st", 6.35029318, "st", "stone"),
        Unit(Kind.VOLUME, "ml", 0.001, "ml", "millilitre", "milliliter"),
        Unit(Kind.VOLUME, "cl", 0.01, "cl", "centilitre", "centiliter"),
        Unit(Kind.VOLUME, "dl", 0.1, "dl", "decilitre", "deciliter"),
        Unit(Kind.VOLUME, "L", 1.0, "l", "litre", "liter"),
        Unit(Kind.VOLUME, "m³", 1000.0, "m3", "m³"),
        Unit(Kind.VOLUME, "gal", 3.785411784, "gal", "gallon"),
        Unit(Kind.VOLUME, "pt", 0.473176473, "pt", "pint", "pinte"),
        Unit(Kind.VOLUME, "fl oz", 0.0295735295625, "floz", "fl.oz"),
        Unit(Kind.VOLUME, "tasse", 0.2365882365, "cup", "tasse").en("cup"),
        Unit(Kind.VOLUME, "c. à soupe", 0.01478676478125, "tbsp", "cas").en("tbsp"),
        Unit(Kind.VOLUME, "c. à café", 0.00492892159375, "tsp", "cac").en("tsp"),
        Unit(Kind.TEMPERATURE, "°C", 0.0, "c", "°c", "celsius", "degre", "degres"),
        Unit(Kind.TEMPERATURE, "°F", 0.0, "f", "°f", "fahrenheit"),
        Unit(Kind.TEMPERATURE, "K", 0.0, "k", "kelvin"),
        Unit(Kind.SPEED, "km/h", 1 / 3.6, "km/h", "kmh", "kph"),
        Unit(Kind.SPEED, "mph", 0.44704, "mph", "mi/h"),
        Unit(Kind.SPEED, "m/s", 1.0, "m/s"),
        Unit(Kind.SPEED, "nœuds", 1852.0 / 3600, "kn", "kt", "noeud", "nœud", "knot").en("knots"),
        Unit(Kind.AREA, "m²", 1.0, "m2", "m²"),
        Unit(Kind.AREA, "km²", 1_000_000.0, "km2", "km²"),
        Unit(Kind.AREA, "ft²", 0.09290304, "ft2", "ft²", "sqft"),
        Unit(Kind.AREA, "ha", 10_000.0, "ha", "hectare"),
        Unit(Kind.AREA, "acres", 4046.8564224, "acre", "ac").en("acres"),
        Unit(Kind.DATA, "o", 1.0, "o", "octet", "byte").en("B"),
        Unit(Kind.DATA, "Ko", 1e3, "ko", "kb", "kilooctet").en("KB"),
        Unit(Kind.DATA, "Mo", 1e6, "mo", "mb", "megaoctet").en("MB"),
        Unit(Kind.DATA, "Go", 1e9, "go", "gb", "gigaoctet").en("GB"),
        Unit(Kind.DATA, "To", 1e12, "to", "tb", "teraoctet").en("TB"),
        Unit(Kind.TIME, "s", 1.0, "s", "sec", "seconde", "second"),
        Unit(Kind.TIME, "min", 60.0, "min", "minute"),
        Unit(Kind.TIME, "h", 3600.0, "h", "heure", "hour", "hr"),
        Unit(Kind.TIME, "jours", 86_400.0, "j", "d", "jour", "day").en("days"),
        Unit(Kind.TIME, "semaines", 604_800.0, "sem", "semaine", "week", "wk").en("weeks"),
        Unit(Kind.ENERGY, "kcal", 4184.0, "kcal", "cal", "calorie"),
        Unit(Kind.ENERGY, "kJ", 1000.0, "kj", "kilojoule"),
    )

    private val byName: Map<String, Unit> = buildMap { units.forEach { u -> u.names.forEach { put(it, u) } } }

    /** Sans unité demandée : l'équivalent usuel (métrique ↔ impérial). */
    private val defaults = mapOf(
        "mm" to "in", "cm" to "in", "m" to "ft", "km" to "mi", "in" to "cm", "ft" to "m", "yd" to "m", "mi" to "km", "nmi" to "km",
        "g" to "oz", "kg" to "lb", "oz" to "g", "lb" to "kg", "st" to "kg", "t" to "lb",
        "ml" to "floz", "cl" to "floz", "l" to "gal", "gal" to "l", "pt" to "ml", "floz" to "ml", "cup" to "ml", "tbsp" to "ml", "tsp" to "ml",
        "c" to "f", "f" to "c", "k" to "c",
        "km/h" to "mph", "mph" to "km/h", "m/s" to "km/h", "kn" to "km/h",
        "m2" to "ft2", "ft2" to "m2", "ha" to "acre", "acre" to "ha", "km2" to "ha",
        "kcal" to "kj", "kj" to "kcal",
    )

    private val UNIT_QUERY = Regex("""^(-?\d+(?:[.,]\d+)?)\s*([^\s\d][^\s]*(?:\s?oz)?)(?:\s+(?:en|in|to|vers|->|→|=)\s+([^\s]+(?:\s?oz)?))?$""")

    fun convert(query: String, now: ZonedDateTime = ZonedDateTime.now()): Conversion? =
        convertUnits(query) ?: convertTime(query, now)

    private fun unit(name: String): Unit? {
        val n = name.lowercase().replace("é", "e").replace("è", "e").removeSuffix(".")
        return byName[n] ?: n.takeIf { it.length > 2 && it.endsWith("s") }?.let { byName[it.dropLast(1)] }
    }

    private fun convertUnits(query: String): Conversion? {
        val match = UNIT_QUERY.matchEntire(query.trim().lowercase().replace("fl oz", "floz")) ?: return null
        val amount = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val from = unit(match.groupValues[2]) ?: return null
        val toName = match.groupValues[3].ifEmpty { defaults[from.id] ?: return null }
        val to = unit(toName) ?: return null
        if (to.kind != from.kind || to === from) return null
        val result = if (from.kind == Kind.TEMPERATURE) temperature(amount, from.id, to.id) else amount * from.factor / to.factor
        val value = "${format(result)} ${to.symbol}"
        return Conversion(value, "${format(amount)} ${from.symbol} → ${to.symbol}")
    }

    private fun temperature(value: Double, from: String, to: String): Double {
        val celsius = when (from) {
            "f" -> (value - 32) * 5 / 9
            "k" -> value - 273.15
            else -> value
        }
        return when (to) {
            "f" -> celsius * 9 / 5 + 32
            "k" -> celsius + 273.15
            else -> celsius
        }
    }

    /** « 15h à Tokyo », « 9:30 tokyo », « 15h30 in London » (heure d'ici), ou « heure à Tokyo » (maintenant). */
    private val AT_TIME = Regex("""^(\d{1,2})\s*(?:h|:)\s*(\d{2})?\s*(?:(?:a|à|in|at|en)\s+)?(.+)$""")
    private val NOW_IN = Regex("""^(?:heure|time|l'heure)\s+(?:a|à|in|at|en)\s+(.+)$|^(.+)\s+time$""")
    private val TIME = DateTimeFormatter.ofPattern("HH:mm")

    private fun city(name: String): City? {
        val folded = TextNormalizer.fold(name.trim())
        return Cities.all.firstOrNull { city -> listOfNotNull(city.fr, city.en).any { TextNormalizer.fold(it) == folded } }
    }

    private fun convertTime(query: String, now: ZonedDateTime): Conversion? {
        val q = query.trim().lowercase()
        AT_TIME.matchEntire(q)?.let { m ->
            val hour = m.groupValues[1].toInt()
            val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
            if (hour > 23 || minute > 59) return null
            val city = city(m.groupValues[3]) ?: return null
            val here = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
            val there = here.withZoneSameInstant(city.zone)
            val day = dayShift(here, there)
            return Conversion(
                "${there.format(TIME)}$day",
                tr("${here.format(TIME)} ici → ${city.name}", "${here.format(TIME)} here → ${city.name}") + offset(here, city.zone),
            )
        }
        NOW_IN.matchEntire(q)?.let { m ->
            val city = city(m.groupValues[1].ifEmpty { m.groupValues[2] }) ?: return null
            val there = now.withZoneSameInstant(city.zone)
            return Conversion("${there.format(TIME)}${dayShift(now, there)}", tr("Maintenant à ${city.name}", "Now in ${city.name}") + offset(now, city.zone))
        }
        return null
    }

    private fun dayShift(here: ZonedDateTime, there: ZonedDateTime): String = when (there.toLocalDate().compareTo(here.toLocalDate())) {
        0 -> ""
        in 1..Int.MAX_VALUE -> tr(" (demain)", " (tomorrow)")
        else -> tr(" (la veille)", " (the day before)")
    }

    /** Écart avec l'heure d'ici, en heures (« +7 h », « −5 h 30 »). */
    private fun offset(here: ZonedDateTime, zone: ZoneId): String {
        val minutes = (zone.rules.getOffset(here.toInstant()).totalSeconds - here.offset.totalSeconds) / 60
        if (minutes == 0) return tr(" (même heure)", " (same time)")
        val sign = if (minutes > 0) "+" else "−"
        val h = abs(minutes) / 60
        val m = abs(minutes) % 60
        return " ($sign$h h" + (if (m > 0) " %02d".format(m) else "") + ")"
    }

    /** 4 chiffres significatifs (toute la partie entière au-delà), virgule décimale. */
    fun format(value: Double): String {
        if (value == 0.0) return "0"
        val integerDigits = floor(log10(abs(value))).toInt() + 1
        return BigDecimal(value).round(MathContext(max(4, integerDigits))).stripTrailingZeros().toPlainString().replace('.', ',')
    }
}
