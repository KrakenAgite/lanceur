package app.lanceur.focus

import app.lanceur.apps.AppEntry
import app.lanceur.builtin.WidgetData
import app.lanceur.i18n.tr
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.TextStyle
import java.util.Locale

/** [HIDE] : les applis cochées disparaissent ; [SHOW_ONLY] : seules les applis cochées restent. */
enum class FocusFilter(private val fr: String, private val en: String) {
    HIDE("Applis à masquer", "Apps to hide"),
    SHOW_ONLY("Applis à afficher", "Apps to show"),
    ;

    val label: String get() = tr(fr, en)
}

/** Plage horaire ; [end] avant [start] : elle passe minuit (22:00–07:00) et appartient au jour de [start]. */
data class FocusSchedule(val days: Set<DayOfWeek>, val start: LocalTime, val end: LocalTime) {
    private val overnight get() = !end.isAfter(start)

    /** Fin de la plage en cours à [now], ou null si elle n'est pas en cours. */
    fun endIfActive(now: ZonedDateTime): ZonedDateTime? {
        val time = now.toLocalTime()
        val today = now.dayOfWeek in days
        val yesterday = now.dayOfWeek.minus(1) in days
        return when {
            !overnight && today && !time.isBefore(start) && time.isBefore(end) -> now.with(end)
            overnight && today && !time.isBefore(start) -> now.plusDays(1).with(end)
            overnight && yesterday && time.isBefore(end) -> now.with(end)
            else -> null
        }?.withSecond(0)?.withNano(0)
    }

    fun label(locale: Locale): String {
        val dayNames = DayOfWeek.entries.filter { it in days }.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, locale) }
        return "$dayNames · $start–$end"
    }
}

/**
 * Mode concentration : démarré à la main ([manual]) ou par une plage horaire ; [pausedUntil] : arrêté pendant
 * une plage, il ne reprend qu'à la suivante. [apps] : paquets cochés, par profil.
 */
data class FocusMode(
    val manual: Boolean = false,
    val filter: FocusFilter = FocusFilter.HIDE,
    val apps: Set<Pair<String, Long>> = emptySet(),
    val schedules: List<FocusSchedule> = emptyList(),
    val pausedUntil: Long? = null,
) {
    private fun scheduleEnd(now: ZonedDateTime): ZonedDateTime? {
        val paused = pausedUntil != null && now.toInstant().toEpochMilli() < pausedUntil
        return if (paused) null else schedules.mapNotNull { it.endIfActive(now) }.maxOrNull()
    }

    fun isActive(now: ZonedDateTime): Boolean = manual || scheduleEnd(now) != null

    /** Heure de fin quand c'est une plage qui l'a déclenché ; null pour un démarrage à la main (sans fin). */
    fun activeUntil(now: ZonedDateTime): ZonedDateTime? = if (manual) null else scheduleEnd(now)

    fun start(): FocusMode = copy(manual = true, pausedUntil = null)

    /** Arrête tout de suite ; la plage en cours ne reprend pas avant sa fin. */
    fun stop(now: ZonedDateTime): FocusMode =
        copy(manual = false, pausedUntil = scheduleEnd(now)?.toInstant()?.toEpochMilli() ?: pausedUntil)

    fun visible(entries: List<AppEntry>, now: ZonedDateTime): List<AppEntry> {
        if (!isActive(now)) return entries
        val checked = { e: AppEntry -> (e.key.packageName to e.key.userSerial) in apps }
        return when (filter) {
            FocusFilter.HIDE -> entries.filterNot(checked)
            FocusFilter.SHOW_ONLY -> entries.filter(checked)
        }
    }

    fun encode(): String = WidgetData.encode(
        buildMap {
            put("manual", if (manual) "1" else "0")
            put("filter", filter.name)
            put("apps", WidgetData.list(apps.map { "${it.first}#${it.second}" }))
            put("schedules", WidgetData.list(schedules.map { s -> s.days.joinToString(",") { it.name } + "|" + s.start + "|" + s.end }))
            pausedUntil?.let { put("paused", it.toString()) }
        },
    )

    companion object {
        /** Le temps de réfléchir avant de sortir du mode concentration. */
        const val EXIT_DELAY_SECONDS = 10

        fun decode(text: String?): FocusMode {
            val v = WidgetData.decode(text)
            if (v["filter"] == null) return FocusMode()
            return FocusMode(
                manual = v["manual"] == "1",
                filter = FocusFilter.entries.firstOrNull { it.name == v["filter"] } ?: FocusFilter.HIDE,
                apps = WidgetData.unlist(v["apps"]).mapNotNullTo(LinkedHashSet()) { entry ->
                    val pkg = entry.substringBeforeLast('#')
                    val serial = entry.substringAfterLast('#').toLongOrNull()
                    if (pkg.isBlank() || serial == null) null else pkg to serial
                },
                schedules = WidgetData.unlist(v["schedules"]).mapNotNull { entry ->
                    val parts = entry.split('|')
                    runCatching {
                        FocusSchedule(
                            parts[0].split(',').filter { it.isNotBlank() }.mapTo(LinkedHashSet()) { DayOfWeek.valueOf(it) },
                            LocalTime.parse(parts[1]),
                            LocalTime.parse(parts[2]),
                        )
                    }.getOrNull()
                },
                pausedUntil = v["paused"]?.toLongOrNull(),
            )
        }
    }
}
