package app.lanceur.prefs

import app.lanceur.apps.AppEntry
import app.lanceur.apps.LabelOrder

/** Ce que chaque écran a le droit d'afficher. */
data class AppLists(
    val allVisible: List<AppEntry>,
    val favorites: List<AppEntry>,
    val hiddenApps: List<AppEntry>,
    val privateApps: List<AppEntry>,
) {
    companion object {
        val EMPTY = AppLists(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

object VisibleApps {
    fun compute(catalog: List<AppEntry>, prefs: LauncherPrefs): AppLists {
        val sorted = catalog.sortedWith(LabelOrder)
        val visible = sorted.filter { !it.isPrivateSpace && it.key !in prefs.hidden }
        val visibleByKey = visible.associateBy { it.key }
        return AppLists(
            allVisible = visible,
            favorites = prefs.favorites.mapNotNull { visibleByKey[it] },
            hiddenApps = sorted.filter { !it.isPrivateSpace && it.key in prefs.hidden },
            privateApps = sorted.filter { it.isPrivateSpace },
        )
    }

    /**
     * Retire les clés d'applis désinstallées. Un catalogue vide (pas encore chargé, ou lecture ratée)
     * ne doit rien effacer : on renvoie alors les préférences telles quelles.
     */
    fun prune(catalog: List<AppEntry>, prefs: LauncherPrefs): LauncherPrefs {
        if (catalog.isEmpty()) return prefs
        val installed = catalog.mapTo(HashSet()) { it.key }
        return prefs.copy(
            favorites = prefs.favorites.filter { it in installed },
            hidden = prefs.hidden.filterTo(LinkedHashSet()) { it in installed },
        )
    }
}
