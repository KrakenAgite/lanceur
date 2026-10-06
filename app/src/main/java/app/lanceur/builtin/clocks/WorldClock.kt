package app.lanceur.builtin.clocks

import app.lanceur.i18n.tr
import app.lanceur.builtin.WidgetData
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

data class ClockFace(val city: City, val time: String, val offset: String, val dayHint: String?, val isDay: Boolean)

data class WorldClocksConfig(val cityIds: List<String>) {
    fun toData(): String = WidgetData.encode(mapOf("cities" to WidgetData.list(cityIds)))

    companion object {
        const val MAX = 4

        fun fromData(data: String?) = WorldClocksConfig(WidgetData.unlist(WidgetData.decode(data)["cities"]).filter { it.isNotBlank() })
    }
}

object WorldClock {
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    /** `home` : fuseau du téléphone, référence du décalage et du jour relatif. */
    fun of(city: City, now: Instant, home: ZoneId): ClockFace {
        val there = now.atZone(city.zone)
        val here = now.atZone(home)
        val seconds = there.offset.totalSeconds - here.offset.totalSeconds
        val offset = if (seconds == 0) tr("même heure", "same time") else {
            val minutes = abs(seconds) / 60
            val sign = if (seconds > 0) "+" else "−"
            if (minutes % 60 == 0) "$sign${minutes / 60} h" else "$sign${minutes / 60} h ${String.format(Locale.ROOT, "%02d", minutes % 60)}"
        }
        val days = ChronoUnit.DAYS.between(here.toLocalDate(), there.toLocalDate())
        val hint = when {
            days > 0 -> tr("demain", "tomorrow")
            days < 0 -> tr("hier", "yesterday")
            else -> null
        }
        return ClockFace(city, TIME.format(there), offset, hint, there.hour in 7..18)
    }
}
