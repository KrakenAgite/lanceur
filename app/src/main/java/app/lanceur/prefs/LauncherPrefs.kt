package app.lanceur.prefs

import app.lanceur.apps.AppKey

enum class AlphabetSide { RIGHT, LEFT }

data class LauncherPrefs(
    val favorites: List<AppKey> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    val alphabetSide: AlphabetSide = AlphabetSide.RIGHT,
    val permissionHintDismissed: Boolean = false,
)
