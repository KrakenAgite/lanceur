package app.lanceur.builtin

/**
 * Données d'un widget intégré : une paire `clé=valeur` par ligne. `\`, `=` et le retour à la ligne sont échappés,
 * donc le premier `=` d'une ligne sépare toujours la clé de la valeur. Une ligne sans `=` est ignorée.
 */
object WidgetData {
    /** Séparateur d'éléments dans une valeur-liste : caractère de contrôle qu'on ne tape jamais. */
    private const val UNIT = '\u001F'

    fun encode(values: Map<String, String>): String =
        values.entries.joinToString("\n") { (key, value) -> escape(key) + "=" + escape(value) }

    fun decode(text: String?): Map<String, String> {
        if (text.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        text.split('\n').forEach { line ->
            val separator = line.indexOf('=')
            if (separator >= 0) out[unescape(line.substring(0, separator))] = unescape(line.substring(separator + 1))
        }
        return out
    }

    fun list(values: List<String>): String = values.joinToString(UNIT.toString())

    fun unlist(value: String?): List<String> = if (value.isNullOrEmpty()) emptyList() else value.split(UNIT)

    private fun escape(text: String): String = buildString {
        text.forEach { c ->
            when (c) {
                '\\' -> append("\\\\")
                '=' -> append("\\e")
                '\n' -> append("\\n")
                else -> append(c)
            }
        }
    }

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val c = text[i]
            if (c == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n' -> append('\n')
                    'e' -> append('=')
                    else -> append(next)
                }
                i += 2
            } else {
                append(c)
                i++
            }
        }
    }
}
