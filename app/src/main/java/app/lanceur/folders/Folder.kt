package app.lanceur.folders

import app.lanceur.apps.AppKey

/** Icônes proposées pour un dossier (le dessin correspondant est dans `FolderIcons`). */
enum class FolderIcon { FOLDER, WORK, GAMES, MUSIC, PHOTO, CHAT, SHOPPING, TRAVEL, SPORT, MONEY, TOOLS, BOOK, MOVIE, HOME, HEALTH, FOOD, SCHOOL, STAR }

/** Dossier de l'accueil : ses applis gardent l'ordre dans lequel on les a ajoutées. */
data class Folder(val id: Int, val name: String, val icon: FolderIcon, val apps: List<AppKey> = emptyList()) {
    /** Une ligne : `id`, icône, nom puis les clés, séparés par des tabulations. */
    fun encode(): String = (listOf(id.toString(), icon.name, clean(name)) + apps.map { it.encode() }).joinToString("\t")

    companion object {
        fun decode(line: String): Folder? {
            val parts = line.split('\t')
            if (parts.size < 3) return null
            val id = parts[0].toIntOrNull() ?: return null
            val icon = FolderIcon.entries.firstOrNull { it.name == parts[1] } ?: FolderIcon.FOLDER
            return Folder(id, parts[2], icon, parts.drop(3).mapNotNull(AppKey::decode).distinct())
        }

        fun encodeAll(folders: List<Folder>): String = folders.joinToString("\n") { it.encode() }

        fun decodeAll(value: String?): List<Folder> =
            value.orEmpty().split('\n').mapNotNull(::decode).distinctBy { it.id }

        /** Les tabulations et retours à la ligne serviraient de séparateurs : on les remplace par des espaces. */
        fun clean(name: String): String = name.replace(Regex("[\\t\\n\\r]+"), " ").trim()

        fun nextId(folders: List<Folder>): Int = (folders.maxOfOrNull { it.id } ?: 0) + 1
    }
}
