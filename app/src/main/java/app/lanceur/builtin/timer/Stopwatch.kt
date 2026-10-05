package app.lanceur.builtin.timer

import app.lanceur.builtin.WidgetData
import java.util.Locale

/** Chrono sur l'horloge murale : `startedAt` survit au redémarrage de Lanceur ou du téléphone. */
data class StopwatchState(val startedAt: Long? = null, val accumulated: Long = 0, val laps: List<Long> = emptyList()) {
    val running: Boolean get() = startedAt != null

    /** Horloge reculée par l'utilisateur : jamais de temps négatif. */
    fun elapsed(now: Long): Long = accumulated + (startedAt?.let { (now - it).coerceAtLeast(0) } ?: 0)

    fun start(now: Long) = if (running) this else copy(startedAt = now)

    fun pause(now: Long) = if (!running) this else copy(startedAt = null, accumulated = elapsed(now))

    fun reset() = StopwatchState()

    fun lap(now: Long) = copy(laps = (laps + elapsed(now)).takeLast(3))

    fun toData(): String = WidgetData.encode(
        buildMap {
            startedAt?.let { put("started", it.toString()) }
            put("accumulated", accumulated.toString())
            put("laps", WidgetData.list(laps.map { it.toString() }))
        },
    )

    companion object {
        fun fromData(data: String?): StopwatchState {
            val values = WidgetData.decode(data)
            return StopwatchState(
                startedAt = values["started"]?.toLongOrNull(),
                accumulated = values["accumulated"]?.toLongOrNull() ?: 0,
                laps = WidgetData.unlist(values["laps"]).mapNotNull { it.toLongOrNull() },
            )
        }

        /** « 00:42,3 », ou « 1:02:03,4 » au-delà d'une heure. */
        fun format(ms: Long): String {
            val total = ms.coerceAtLeast(0)
            val tenths = total / 100 % 10
            val seconds = total / 1000
            val h = seconds / 3600
            val m = seconds % 3600 / 60
            val s = seconds % 60
            return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d,%d", h, m, s, tenths)
            else String.format(Locale.ROOT, "%02d:%02d,%d", m, s, tenths)
        }
    }
}

object TimerPresets {
    val minutes = listOf(1, 3, 5, 10, 15, 30)

    fun seconds(minutes: Int) = minutes * 60

    fun label(minutes: Int) = "$minutes min"
}
