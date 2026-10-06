package app.lanceur.net

import app.lanceur.i18n.tr
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface NetResult {
    class Ok(val bytes: ByteArray) : NetResult {
        fun text(): String = bytes.toString(Charsets.UTF_8)
    }

    data class Failed(val reason: String) : NetResult
}

/** Décisions pures du point de sortie : HTTPS seulement, y compris après redirection. */
object NetRules {
    const val MAX_BYTES = 1_000_000

    /** Pages web du mode lecture, plus lourdes qu'un flux. */
    const val MAX_PAGE_BYTES = 3_000_000
    const val MAX_REDIRECTS = 3
    const val TIMEOUT_MS = 10_000

    fun allowed(url: String): Boolean = runCatching { URI(url.trim()).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)

    /** Adresse suivante d'une redirection, ou `null` si elle est absente ou sort du HTTPS. */
    fun next(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val resolved = runCatching { URI(current).resolve(location.trim()).toString() }.getOrNull() ?: return null
        return resolved.takeIf(::allowed)
    }
}

/**
 * Seul endroit de Lanceur qui ouvre une connexion (vérifié par `NetworkGuardTest`). Utilisé uniquement par la météo,
 * le RSS et le mode lecture des Actualités ; aucun cookie ni identifiant n'est envoyé.
 */
class Network {
    suspend fun get(url: String, maxBytes: Int = NetRules.MAX_BYTES): NetResult = withContext(Dispatchers.IO) { fetch(url, maxBytes) }

    private fun fetch(start: String, maxBytes: Int): NetResult {
        var current = start
        repeat(NetRules.MAX_REDIRECTS + 1) {
            if (!NetRules.allowed(current)) return NetResult.Failed(tr("Adresse HTTPS requise", "HTTPS address required"))
            val connection = try {
                URL(current).openConnection() as HttpURLConnection
            } catch (e: Exception) {
                return NetResult.Failed(tr("Adresse invalide", "Invalid address"))
            }
            connection.apply {
                connectTimeout = NetRules.TIMEOUT_MS
                readTimeout = NetRules.TIMEOUT_MS
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("User-Agent", "Lanceur")
            }
            try {
                when (val code = connection.responseCode) {
                    in 200..299 -> return NetResult.Ok(connection.inputStream.use { readLimited(it, maxBytes) })
                    301, 302, 303, 307, 308 ->
                        current = NetRules.next(current, connection.getHeaderField("Location")) ?: return NetResult.Failed(tr("Redirection refusée", "Redirect refused"))
                    else -> return NetResult.Failed(tr("Erreur ", "Error ") + code)
                }
            } catch (e: IOException) {
                return NetResult.Failed(e.message ?: tr("Réseau indisponible", "Network unavailable"))
            } catch (e: RuntimeException) {
                // Hôte ou port étrange, refus du système… : jamais une raison de faire planter le launcher
                return NetResult.Failed(tr("Adresse invalide", "Invalid address"))
            } finally {
                connection.disconnect()
            }
        }
        return NetResult.Failed(tr("Trop de redirections", "Too many redirects"))
    }

    /** Lit au plus [maxBytes] : au-delà, erreur sans tout charger en mémoire. */
    private fun readLimited(input: InputStream, maxBytes: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > maxBytes) throw IOException("Réponse trop grosse")
        }
        return out.toByteArray()
    }
}
