package app.lanceur.search

/** Une carte de l'écran de recherche : un intitulé (absent pour l'invitation aux autorisations) et ses résultats. */
data class ResultSection(val key: String, val title: String?, val items: List<SearchResult>)

object ResultSections {
    /** Regroupe par type ; chaque section prend la place de son premier résultat, l'ordre de pertinence est gardé. */
    fun of(results: List<SearchResult>): List<ResultSection> =
        results.groupBy(::kind).map { (kind, items) -> ResultSection(kind.name, kind.title, items) }

    private enum class Kind(val title: String?) {
        HINT(null), APPS("Applis"), CALC("Calcul"), CONTACTS("Contacts"), AGENDA("Agenda"), SETTINGS("Réglages"), WEB("Web")
    }

    private fun kind(result: SearchResult): Kind = when (result) {
        is SearchResult.App -> Kind.APPS
        is SearchResult.Calc -> Kind.CALC
        is SearchResult.Contact -> Kind.CONTACTS
        is SearchResult.Event -> Kind.AGENDA
        is SearchResult.Setting -> Kind.SETTINGS
        is SearchResult.Web -> Kind.WEB
        SearchResult.PermissionHint -> Kind.HINT
    }
}
