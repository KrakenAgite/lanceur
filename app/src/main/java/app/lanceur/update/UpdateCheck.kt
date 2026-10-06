package app.lanceur.update

import org.json.JSONObject

/** [apkUrl] et [sha256] : de quoi se mettre à jour seul ; null si la release n'a pas d'APK vérifiable. */
data class Release(val version: String, val url: String, val apkUrl: String? = null, val sha256: String? = null)

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
        val assets = root.optJSONArray("assets")
        val apk = assets?.let { a -> (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull { it.optString("name").endsWith(".apk") } }
        val apkUrl = apk?.optString("browser_download_url")?.takeIf { it.startsWith("https://") }
        val sha = apk?.optString("digest")?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")?.lowercase()
        // Sans empreinte publiée, pas d'installation automatique : on ne saurait pas vérifier le fichier
        if (apkUrl != null && sha != null) Release(tag, url, apkUrl, sha) else Release(tag, url)
    }.getOrNull()

    /** Le fichier téléchargé a-t-il l'empreinte publiée ? */
    fun matches(input: java.io.InputStream, sha256: String): Boolean {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) } == sha256.lowercase()
    }

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
