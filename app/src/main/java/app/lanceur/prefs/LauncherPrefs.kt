package app.lanceur.prefs

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSlot

enum class AlphabetSide { RIGHT, LEFT }

data class LauncherPrefs(
    val favorites: List<AppKey> = emptyList(),
    val hidden: Set<AppKey> = emptySet(),
    val alphabetSide: AlphabetSide = AlphabetSide.RIGHT,
    val permissionHintDismissed: Boolean = false,
    val widgetPageEnabled: Boolean = true,
    /** Dans l'ordre d'affichage sur la page de widgets. */
    val widgets: List<WidgetSlot> = emptyList(),
)
