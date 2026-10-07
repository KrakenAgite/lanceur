package app.lanceur.prefs

import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.apps.LabelOrder

/** Ce que chaque écran a le droit d'afficher. */
data class AppLists(
    val allVisible: List<AppEntry>,
    val favorites: List<AppEntry>,
    val hiddenApps: List<AppEntry>,
    val privateApps: List<AppEntry>,
    /** Mode concentration en cours ; [focusUntil] : fin de la plage horaire, null si démarré à la main. */
    val focusActive: Boolean = false,
    /** Applis qu'on peut cocher pour la concentration : la liste de l'accueil, sans le filtre de la concentration. */
    val focusCandidates: List<AppEntry> = emptyList(),
    val focusUntil: java.time.ZonedDateTime? = null,
    /** Dossiers avec leurs applis visibles (un dossier vide reste affiché). */
    val folders: List<FolderApps> = emptyList(),
) {
    companion object {
        val EMPTY = AppLists(emptyList(), emptyList(), emptyList(), emptyList())
    }
}

data class FolderApps(val folder: app.lanceur.folders.Folder, val apps: List<AppEntry>)

object VisibleApps {
    fun compute(catalog: List<AppEntry>, prefs: LauncherPrefs, now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): AppLists {
        val sorted = catalog.sortedWith(LabelOrder)
        val hiddenPackages = prefs.hidden.mapTo(HashSet()) { it.packageInProfile() }
        val isHidden = { entry: AppEntry -> entry.key.packageInProfile() in hiddenPackages }
        val launchable = sorted.filter { !it.isPrivateSpace && !isHidden(it) }
        val visible = prefs.focus.visible(launchable, now)
        val visibleByKey = visible.associateBy { it.key }
        return AppLists(
            allVisible = visible,
            favorites = prefs.favorites.mapNotNull { visibleByKey[it] },
            hiddenApps = sorted.filter { !it.isPrivateSpace && isHidden(it) },
            privateApps = sorted.filter { it.isPrivateSpace },
            focusActive = prefs.focus.isActive(now),
            focusCandidates = launchable.filterNot { it.key.packageName.startsWith("app.lanceur") },
            focusUntil = prefs.focus.activeUntil(now),
            folders = prefs.folders.map { folder -> FolderApps(folder, folder.apps.mapNotNull { visibleByKey[it] }) },
        )
    }

    /**
     * Retire les clés d'applis désinstallées. Un catalogue vide (pas encore chargé, ou lecture ratée)
     * ne doit rien effacer : on renvoie alors les préférences telles quelles.
     */
    fun prune(catalog: List<AppEntry>, prefs: LauncherPrefs): LauncherPrefs {
        if (catalog.isEmpty()) return prefs
        val installed = catalog.mapTo(HashSet()) { it.key }
        val installedPackages = catalog.mapTo(HashSet()) { it.key.packageInProfile() }
        return prefs.copy(
            favorites = prefs.favorites.filter { it in installed },
            hidden = prefs.hidden.filterTo(LinkedHashSet()) { it.packageInProfile() in installedPackages },
            folders = prefs.folders.map { folder -> folder.copy(apps = folder.apps.filter { it in installed }) },
        )
    }

    /**
     * On cache un paquet dans un profil, pas une activité : une mise à jour qui renomme l'activité
     * de lancement, ou une icône déguisée (activity-alias), ne fait pas réapparaître l'appli.
     */
    fun AppKey.packageInProfile(): Pair<String, Long> = packageName to userSerial
}
