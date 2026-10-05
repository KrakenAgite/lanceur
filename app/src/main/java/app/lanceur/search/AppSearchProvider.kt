package app.lanceur.search

import app.lanceur.apps.AppEntry
import app.lanceur.apps.LabelOrder
import app.lanceur.text.TextNormalizer

/** `apps` doit renvoyer uniquement les applis visibles (jamais les cachées ni celles de l'Espace privé). */
class AppSearchProvider(private val apps: () -> List<AppEntry>, private val limit: Int = 8) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> =
        rank(apps(), query, limit).map { SearchResult.App(it) }

    companion object {
        private val WORD_SEPARATOR = Regex("[^A-Z0-9]+")

        /** Le nom commence par la requête, puis un mot du nom, puis le nom la contient. */
        fun rank(apps: List<AppEntry>, query: String, limit: Int): List<AppEntry> {
            val q = TextNormalizer.fold(query.trim())
            if (q.isEmpty()) return emptyList()
            return apps.mapNotNull { app ->
                val label = TextNormalizer.fold(app.label)
                val score = when {
                    label.startsWith(q) -> 0
                    label.split(WORD_SEPARATOR).any { it.startsWith(q) } -> 1
                    label.contains(q) -> 2
                    else -> return@mapNotNull null
                }
                score to app
            }
                .sortedWith(compareBy<Pair<Int, AppEntry>> { it.first }.thenBy(LabelOrder) { it.second })
                .take(limit)
                .map { it.second }
        }
    }
}
