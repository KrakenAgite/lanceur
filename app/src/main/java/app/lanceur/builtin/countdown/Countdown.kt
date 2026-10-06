package app.lanceur.builtin.countdown

import app.lanceur.i18n.L10n
import app.lanceur.i18n.tr
import app.lanceur.builtin.WidgetData
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** `createdAt` : moment du réglage (ms), départ de la barre de progression. */
data class CountdownConfig(val title: String, val date: LocalDate, val time: LocalTime?, val createdAt: Long) {
    fun toData(): String = WidgetData.encode(
        buildMap {
            put("title", title)
            put("date", date.toString())
            time?.let { put("time", it.toString()) }
            put("created", createdAt.toString())
        },
    )

    companion object {
        /** `null` sans date lisible : le widget propose alors de le régler. */
        fun fromData(data: String?): CountdownConfig? {
            val values = WidgetData.decode(data)
            val date = values["date"]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return null
            return CountdownConfig(
                title = values["title"].orEmpty(),
                date = date,
                time = values["time"]?.let { runCatching { LocalTime.parse(it) }.getOrNull() },
                createdAt = values["created"]?.toLongOrNull() ?: 0L,
            )
        }
    }
}

data class CountdownView(val big: String, val dateText: String, val progress: Float)

object Countdown {
    private val DATE get() = DateTimeFormatter.ofPattern(tr("EEEE d MMMM yyyy", "EEEE, MMMM d, yyyy"), L10n.locale)
    private val DATE_TIME get() = DateTimeFormatter.ofPattern(tr("EEEE d MMMM yyyy 'à' HH:mm", "EEEE, MMMM d, yyyy 'at' HH:mm"), L10n.locale)

    fun of(config: CountdownConfig, now: ZonedDateTime): CountdownView {
        val target = config.time?.let { config.date.atTime(it).atZone(now.zone) }
        val days = ChronoUnit.DAYS.between(now.toLocalDate(), config.date)
        val big = when {
            target != null && now.isBefore(target) -> {
                val minutes = Duration.between(now, target).toMinutes()
                val d = minutes / (24 * 60)
                val h = minutes % (24 * 60) / 60
                when {
                    d > 0 -> tr("$d j $h h", "${d}d ${h}h")
                    h > 0 -> "$h h ${minutes % 60} min"
                    else -> "${maxOf(1, minutes)} min"
                }
            }
            days > 0 -> tr("J-$days", "D-$days")
            days == 0L -> tr("C'est aujourd'hui 🎉", "It's today 🎉")
            days == -1L -> tr("Il y a 1 jour", "1 day ago")
            else -> tr("Il y a ${-days} jours", "${-days} days ago")
        }
        val end = (target ?: config.date.atStartOfDay(now.zone)).toInstant().toEpochMilli()
        val nowMs = now.toInstant().toEpochMilli()
        val progress = if (end <= config.createdAt) 1f
        else ((nowMs - config.createdAt).toFloat() / (end - config.createdAt)).coerceIn(0f, 1f)
        val dateText = (if (target != null) DATE_TIME.format(target) else DATE.format(config.date))
            .replaceFirstChar { it.titlecase(Locale.FRENCH) }
        return CountdownView(big, dateText, progress)
    }
}
