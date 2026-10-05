package app.lanceur.search

object CalcProvider : SearchProvider {
    override suspend fun search(query: String): List<SearchResult> = when (val outcome = Calculator.evaluate(query)) {
        null -> emptyList()
        is Calculator.Outcome.Value -> listOf(SearchResult.Calc(query, outcome.text))
        Calculator.Outcome.Undefined -> listOf(SearchResult.Calc(query, "—"))
    }
}
