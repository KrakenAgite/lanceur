package app.lanceur.search

import app.lanceur.apps.AppEntry

/** Résultats sans dépendance Android : l'écran et `ResultActions` les transforment en intents. */
sealed interface SearchResult {
    data class App(val entry: AppEntry) : SearchResult
    data class Calc(val expression: String, val value: String) : SearchResult

    /** [detail] : ce qui a été converti (« 5 mi → km ») ; [value] : le résultat, copié au toucher. */
    data class Convert(val detail: String, val value: String) : SearchResult
    data class Contact(val lookupUri: String, val name: String, val phone: String?) : SearchResult
    data class Event(
        val eventId: Long,
        val title: String,
        val begin: Long,
        val end: Long,
        val allDay: Boolean,
        val location: String?,
    ) : SearchResult
    data class Setting(val action: String, val label: String) : SearchResult
    data class Web(val query: String) : SearchResult
    data object PermissionHint : SearchResult
}
