package app.lanceur.apps.icons

import app.lanceur.apps.AppKey

/**
 * Correspondances d'un pack d'icônes (`appfilter.xml`, format Nova/ADW) : activité → nom du dessin dans le pack.
 * Une activité absente prend le dessin d'une autre activité du même paquet, sinon l'icône du système.
 */
class AppFilter(private val drawables: Map<Pair<String, String>, String>) {
    private val byPackage = drawables.entries.groupBy({ it.key.first }, { it.value })

    fun drawableFor(key: AppKey): String? = drawables[key.packageName to key.className] ?: byPackage[key.packageName]?.firstOrNull()

    val size: Int get() = drawables.size

    companion object {
        /** `ComponentInfo{paquet/activité}` ; une activité en `.Nom` est relative au paquet. */
        fun component(text: String): Pair<String, String>? {
            val inner = text.trim().removePrefix("ComponentInfo{").takeIf { it != text.trim() }?.removeSuffix("}") ?: return null
            val parts = inner.split('/').map { it.trim() }
            if (parts.size != 2 || parts.any { it.isEmpty() }) return null
            val (pkg, cls) = parts
            return pkg to (if (cls.startsWith(".")) pkg + cls else cls)
        }
    }
}
