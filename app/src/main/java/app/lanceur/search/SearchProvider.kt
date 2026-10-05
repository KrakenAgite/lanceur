package app.lanceur.search

interface SearchProvider {
    /** Attente avant d'interroger cette source, pour ne pas la solliciter à chaque frappe. */
    val delayMs: Long get() = 0L

    suspend fun search(query: String): List<SearchResult>
}
