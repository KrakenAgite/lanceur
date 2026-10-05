package app.lanceur.search

class PermissionHintProvider(private val shouldShow: () -> Boolean) : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> =
        if (shouldShow()) listOf(SearchResult.PermissionHint) else emptyList()
}
