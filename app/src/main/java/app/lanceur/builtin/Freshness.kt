package app.lanceur.builtin

import app.lanceur.i18n.tr
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
        if (now - fetchedAt < 3_600_000) tr("Mis à jour à ", "Updated at ") + TIME.format(Instant.ofEpochMilli(fetchedAt).atZone(zone))
        else tr("Mis à jour ", "Updated ") + ago(fetchedAt, now)

    fun ago(time: Long, now: Long): String {
        val minutes = (now - time).coerceAtLeast(0) / 60_000
        return when {
            minutes < 1 -> tr("à l'instant", "just now")
            minutes < 60 -> tr("il y a $minutes min", "$minutes min ago")
            minutes < 24 * 60 -> tr("il y a ${minutes / 60} h", "${minutes / 60} h ago")
            else -> tr("il y a ${minutes / (24 * 60)} j", "${minutes / (24 * 60)} d ago")
        }
    }
}
