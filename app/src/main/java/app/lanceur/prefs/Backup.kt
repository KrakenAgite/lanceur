package app.lanceur.prefs

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.json.JSONArray
import org.json.JSONObject

/** Mises à jour : propre à ce téléphone, ni sauvegardé ni restauré. */
data class UpdateSettings(
    val enabled: Boolean = true,
    val lastCheck: Long? = null,
    val latest: String? = null,
    val notified: String? = null,
    /** Télécharger et installer seul la nouvelle version. */
    val autoInstall: Boolean = true,
)

/** Réglages de la sauvegarde elle-même : jamais sauvegardés ni restaurés (sinon on écraserait le dossier choisi). */
data class BackupSettings(val folder: String? = null, val auto: Boolean = false, val last: Long? = null)

data class BackupFile(val createdAt: Long, val values: Map<String, Any>)

/**
 * Fichier de sauvegarde : tous les réglages de Lanceur (favoris, pages, widgets, flux, apparence, concentration…)
 * en JSON, chaque valeur avec son type pour être restaurée à l'identique.
 */
object Backup {
    const val VERSION = 1
    /** Réglages propres à ce téléphone (sauvegarde, mises à jour) : ni sauvegardés ni restaurés. */
    private val EXCLUDED_PREFIXES = listOf("backup_", "update_")

    fun isExcluded(key: String): Boolean = EXCLUDED_PREFIXES.any(key::startsWith)
    /** Sauvegardes automatiques gardées dans le dossier ; les plus anciennes sont effacées. */
    const val KEEP = 10

    private val NAME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HHmmss")
    private val NAME_REGEX = Regex("""lanceur-\d{4}-\d{2}-\d{2}-\d{6}\.json""")

    fun fileName(at: LocalDateTime): String = "lanceur-${NAME.format(at)}.json"

    fun isBackupName(name: String): Boolean = NAME_REGEX.matches(name)

    fun encode(values: Map<String, Any>, createdAt: Long): String {
        val prefs = JSONObject()
        values.toSortedMap().forEach { (key, value) ->
            val entry = when (value) {
                is String -> JSONObject().put("t", "s").put("v", value)
                is Boolean -> JSONObject().put("t", "b").put("v", value)
                is Int -> JSONObject().put("t", "i").put("v", value)
                is Long -> JSONObject().put("t", "l").put("v", value)
                is Float -> JSONObject().put("t", "f").put("v", value.toDouble())
                is Double -> JSONObject().put("t", "d").put("v", value)
                is Set<*> -> JSONObject().put("t", "ss").put("v", JSONArray(value.map { it.toString() }.sorted()))
                else -> null
            }
            entry?.let { prefs.put(key, it) }
        }
        return JSONObject().put("app", "lanceur").put("version", VERSION).put("createdAt", createdAt).put("prefs", prefs).toString(2)
    }

    /** Null si ce n'est pas une sauvegarde de Lanceur lisible par cette version. */
    fun decode(text: String): BackupFile? = runCatching {
        val root = JSONObject(text)
        if (root.optString("app") != "lanceur" || root.optInt("version") !in 1..VERSION) return null
        val prefs = root.getJSONObject("prefs")
        val values = prefs.keys().asSequence().associateWith { key ->
            val entry = prefs.getJSONObject(key)
            when (entry.getString("t")) {
                "s" -> entry.getString("v")
                "b" -> entry.getBoolean("v")
                "i" -> entry.getInt("v")
                "l" -> entry.getLong("v")
                "f" -> entry.getDouble("v").toFloat()
                "d" -> entry.getDouble("v")
                "ss" -> entry.getJSONArray("v").let { a -> (0 until a.length()).mapTo(LinkedHashSet()) { a.getString(it) } }
                else -> error("type inconnu")
            }
        }
        BackupFile(root.optLong("createdAt"), values)
    }.getOrNull()
}
