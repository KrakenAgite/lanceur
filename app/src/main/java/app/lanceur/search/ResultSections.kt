package app.lanceur.search

import app.lanceur.i18n.tr

/** Une carte de l'écran de recherche : un intitulé (absent pour l'invitation aux autorisations) et ses résultats. */
data class ResultSection(val key: String, val title: String?, val items: List<SearchResult>)

object ResultSections {
    /** Regroupe par type ; chaque section prend la place de son premier résultat, l'ordre de pertinence est gardé. */
    fun of(results: List<SearchResult>): List<ResultSection> =
        results.groupBy(::kind).map { (kind, items) -> ResultSection(kind.name, kind.title, items) }

    private enum class Kind(private val fr: String?, private val en: String?) {
        HINT(null, null), APPS("Applis", "Apps"), CALC("Calcul", "Math"), CONVERT("Conversion", "Conversion"), CONTACTS("Contacts", "Contacts"),
        AGENDA("Agenda", "Calendar"), SETTINGS("Réglages", "Settings"), WEB("Web", "Web");

        val title: String? get() = fr?.let { tr(it, en!!) }
    }

    private fun kind(result: SearchResult): Kind = when (result) {
        is SearchResult.App -> Kind.APPS
        is SearchResult.Calc -> Kind.CALC
        is SearchResult.Convert -> Kind.CONVERT
        is SearchResult.Contact -> Kind.CONTACTS
        is SearchResult.Event -> Kind.AGENDA
        is SearchResult.Setting -> Kind.SETTINGS
        is SearchResult.Web -> Kind.WEB
        SearchResult.PermissionHint -> Kind.HINT
    }
}
