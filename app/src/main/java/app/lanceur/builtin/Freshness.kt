package app.lanceur.builtin

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Ancienneté des données réseau (météo, RSS). */
object Freshness {
    const val MAX_AGE_MS = 30 * 60_000L
    private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.FRENCH)

    fun isStale(fetchedAt: Long?, now: Long): Boolean = fetchedAt == null || now - fetchedAt > MAX_AGE_MS

    /** Moins d'une heure : « Mis à jour à 14:05 » ; sinon « Mis à jour il y a 2 h » (données gardées hors réseau). */
    fun label(fetchedAt: Long, now: Long, zone: ZoneId): String =
        if (now - fetchedAt < 3_600_000) "Mis à jour à ${TIME.format(Instant.ofEpochMilli(fetchedAt).atZone(zone))}"
        else "Mis à jour ${ago(fetchedAt, now)}"

    fun ago(time: Long, now: Long): String {
        val minutes = (now - time).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> "à l'instant"
            minutes < 60 -> "il y a $minutes min"
            minutes < 24 * 60 -> "il y a ${minutes / 60} h"
            else -> "il y a ${minutes / (24 * 60)} j"
        }
    }
}
