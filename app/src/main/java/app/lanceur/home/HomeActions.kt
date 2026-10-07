package app.lanceur.home

import app.lanceur.apps.AppEntry
import app.lanceur.ui.AppMenuAction

class HomeActions(
    val launch: (AppEntry) -> Unit = {},
    val menu: (AppEntry, AppMenuAction) -> Unit = { _, _ -> },
    val changeMode: (ListMode) -> Unit = {},
    val openSearch: () -> Unit = {},
    val openNotifications: () -> Unit = {},
    val openVault: () -> Unit = {},
    val openSettings: () -> Unit = {},
    val openClock: () -> Unit = {},
    val openCalendar: () -> Unit = {},
    val lockScreen: () -> Unit = {},
    val stopFocus: () -> Unit = {},
    /** Appui long sur l'icône d'un dossier. */
    val editFolder: (app.lanceur.folders.Folder) -> Unit = {},
)
