package app.lanceur.apps

/** Une appli telle que le launcher l'affiche. */
data class AppEntry(val key: AppKey, val label: String, val isPrivateSpace: Boolean)
