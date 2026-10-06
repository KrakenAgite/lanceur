package app.lanceur.prefs

import app.lanceur.apps.AppKey
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
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
    /** Données des widgets intégrés (note, tâches, réglages…), par identifiant. */
    val widgetData: Map<Int, String> = emptyMap(),
    val newsEnabled: Boolean = false,
    /** Ordre de gauche à droite des pages ; `widgetPageEnabled` et `newsEnabled` disent lesquelles sont affichées. */
    val pageOrder: List<PageKind> = PageLayout.DEFAULT_ORDER,
    /** État de la page Actualités (`NewsState` encodé). */
    val news: String? = null,
)
