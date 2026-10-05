package app.lanceur.home

sealed interface ListMode {
    data object Favorites : ListMode
    data class Letter(val letter: Char) : ListMode
}

enum class Screen { HOME, SEARCH, VAULT, SETTINGS, WIDGET_PICKER }
