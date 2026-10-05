package app.lanceur.summary

import app.lanceur.text.TextNormalizer
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** `color` : couleur de l'agenda (ARGB), si le système la fournit. */
data class SummaryEvent(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int? = null,
)

/** `text` : ligne complète ; `timeLabel`, `title`, `tomorrow` : les morceaux affichés par la chronologie. */
data class SummaryLine(
    val event: SummaryEvent,
    val text: String,
    val timeLabel: String,
    val title: String,
    val tomorrow: Boolean,
    /** Anniversaire « toute la journée » : affiché avec 🎁 et un titre allégé. */
    val birthday: Boolean = false,
)

data class DaySummaryState(
    val dateLabel: String,
    val events: List<SummaryLine>,
    val alarmLabel: String?,
    val calendarGranted: Boolean,
)

object DaySummary {
    const val MAX_EVENTS = 3

    private val DATE = DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.FRENCH)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)
    private val OTHER_DAY = DateTimeFormatter.ofPattern("EEE d MMM HH:mm", Locale.FRENCH)

    fun build(now: ZonedDateTime, events: List<SummaryEvent>, nextAlarmMillis: Long?, calendarGranted: Boolean): DaySummaryState {
        val today = now.toLocalDate()
        val tomorrow = today.plusDays(1)
        val nowMillis = now.toInstant().toEpochMilli()
        val lines = if (!calendarGranted) {
            emptyList()
        } else {
            events.mapNotNull { event ->
                val startDay: LocalDate
                val finished: Boolean
                if (event.allDay) {
                    // Les événements « toute la journée » sont enregistrés en UTC, de minuit à minuit (fin exclue)
                    startDay = utcDate(event.begin)
                    finished = !utcDate(event.end).isAfter(today)
                } else {
                    startDay = Instant.ofEpochMilli(event.begin).atZone(now.zone).toLocalDate()
                    finished = event.end <= nowMillis
                }
                val day = maxOf(startDay, today)
                if (finished || day.isAfter(tomorrow)) null else day to event
            }
                .sortedWith(compareBy<Pair<LocalDate, SummaryEvent>>({ it.first }, { if (it.second.allDay) 0 else 1 }, { it.second.begin }))
                .take(MAX_EVENTS)
                .map { (day, event) -> line(day == tomorrow, event, now.zone) }
        }
        return DaySummaryState(
            dateLabel = DATE.format(now).replaceFirstChar { it.titlecase(Locale.FRENCH) },
            events = lines,
            alarmLabel = nextAlarmMillis?.let { alarmLabel(it, now) },
            calendarGranted = calendarGranted,
        )
    }

    fun alarmLabel(millis: Long, now: ZonedDateTime): String {
        val at = Instant.ofEpochMilli(millis).atZone(now.zone)
        return when (at.toLocalDate()) {
            now.toLocalDate() -> "Aujourd'hui ${TIME.format(at)}"
            now.toLocalDate().plusDays(1) -> "Demain ${TIME.format(at)}"
            else -> OTHER_DAY.format(at)
        }
    }

    private fun line(tomorrow: Boolean, event: SummaryEvent, zone: ZoneId): SummaryLine {
        val time = if (event.allDay) null else TIME.format(Instant.ofEpochMilli(event.begin).atZone(zone))
        val title = event.title.ifBlank { "(Sans titre)" }
        val birthday = event.allDay && BIRTHDAY_WORDS.any { TextNormalizer.fold(title).contains(it) }
        return SummaryLine(
            event = event,
            text = listOfNotNull(if (tomorrow) "Demain" else null, time, title).joinToString(" "),
            timeLabel = if (birthday) "🎁" else time ?: "Journée",
            title = if (birthday) shortBirthdayTitle(title) else title,
            tomorrow = tomorrow,
            birthday = birthday,
        )
    }

    private val BIRTHDAY_WORDS = listOf("ANNIVERSAIRE", "BIRTHDAY")
    private val BIRTHDAY_PATTERNS = listOf(
        Regex("\\s*[-–]\\s*anniversaire\\s*$", RegexOption.IGNORE_CASE), // « Jean - Anniversaire » (Google)
        Regex("^\\s*anniversaire\\s+(de\\s+|d['’]\\s*)", RegexOption.IGNORE_CASE), // « Anniversaire de Léa »
        Regex("(['’]s)?\\s*birthday\\s*$", RegexOption.IGNORE_CASE), // « Paul's birthday »
    )

    /** Ne garde que le nom de la personne ; si rien ne reste, le titre d'origine. */
    private fun shortBirthdayTitle(title: String): String =
        BIRTHDAY_PATTERNS.fold(title) { acc, pattern -> pattern.replace(acc, "") }.trim().ifBlank { title }

    private fun utcDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
}
