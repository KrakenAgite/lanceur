package app.lanceur.search

/** Toujours présent : la dernière ligne propose la recherche web. */
object WebProvider : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> = listOf(SearchResult.Web(query))
}
