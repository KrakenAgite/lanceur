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

    /** APK d'une mise à jour de Lanceur. */
    const val MAX_DOWNLOAD_BYTES = 50_000_000L
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
 * le RSS, les Actualités et les mises à jour de Lanceur (la vue web des articles a son propre moteur, cf. `ArticleWebView`) ; aucun cookie ni identifiant n'est envoyé.
 */
class Network {
    suspend fun get(url: String): NetResult = withContext(Dispatchers.IO) { fetch(url) { NetResult.Ok(readLimited(it)) } }

    /** Mise à jour de Lanceur : écrit la réponse dans [target] (au plus [NetRules.MAX_DOWNLOAD_BYTES]). */
    suspend fun download(url: String, target: java.io.File): NetResult = withContext(Dispatchers.IO) {
        fetch(url) { input ->
            target.outputStream().use { out ->
                val buffer = ByteArray(64 * 1024)
                var total = 0L
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > NetRules.MAX_DOWNLOAD_BYTES) throw IOException("Fichier trop gros")
                    out.write(buffer, 0, read)
                }
            }
            NetResult.Ok(ByteArray(0))
        }
    }

    private fun fetch(start: String, read: (InputStream) -> NetResult): NetResult {
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
                    in 200..299 -> return connection.inputStream.use(read)
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

    /** Lit au plus `MAX_BYTES` : au-delà, erreur sans tout charger en mémoire. */
    private fun readLimited(input: InputStream): ByteArray {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > NetRules.MAX_BYTES) throw IOException("Réponse trop grosse")
        }
        return out.toByteArray()
    }
}
