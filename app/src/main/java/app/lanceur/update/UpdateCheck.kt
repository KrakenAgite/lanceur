package app.lanceur.update

import org.json.JSONObject

data class Release(val version: String, val url: String)

/** Dernière release publiée sur GitHub et comparaison avec la version installée (pur). */
object UpdateCheck {
    const val LATEST_URL = "https://api.github.com/repos/KrakenAgite/lanceur/releases/latest"
    const val RELEASES_PAGE = "https://github.com/KrakenAgite/lanceur/releases"
    private const val INTERVAL_MS = 12 * 3_600_000L

    fun isDue(lastCheck: Long?, now: Long): Boolean = lastCheck == null || now - lastCheck >= INTERVAL_MS

    fun parse(json: String): Release? = runCatching {
        val root = JSONObject(json)
        if (root.optBoolean("draft") || root.optBoolean("prerelease")) return null
        val tag = root.getString("tag_name").removePrefix("v")
        val url = root.optString("html_url").takeIf { it.startsWith("https://github.com/") } ?: RELEASES_PAGE
        Release(tag, url)
    }.getOrNull()

    /** « 1.10.0 » est plus récent que « 1.9.3 » ; une version illisible ne l'est jamais. */
    fun isNewer(candidate: String, installed: String): Boolean {
        val a = numbers(candidate) ?: return false
        val b = numbers(installed) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun numbers(version: String): List<Int>? =
        version.trim().split('.').map { it.toIntOrNull() ?: return null }.takeIf { it.isNotEmpty() }
}
