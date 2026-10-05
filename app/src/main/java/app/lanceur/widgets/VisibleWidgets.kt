package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.prefs.VisibleApps

sealed interface WidgetCard {
    val slot: WidgetSlot

    data class Live(override val slot: WidgetSlot) : WidgetCard

    /** Android ne connaît plus ce widget (appli désinstallée, fournisseur supprimé) : l'utilisateur peut le retirer. */
    data class Unavailable(override val slot: WidgetSlot) : WidgetCard
}

object VisibleWidgets {
    /**
     * `available` : identifiants qu'Android sait encore afficher. Le widget d'un paquet caché dans son profil
     * n'apparaît pas du tout : aucune vue n'est créée pour lui.
     */
    fun compute(slots: List<WidgetSlot>, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        slots.filter { it.provider.packageInProfile() !in hiddenPackages }
            .map { if (it.appWidgetId in available) WidgetCard.Live(it) else WidgetCard.Unavailable(it) }
    }
}
