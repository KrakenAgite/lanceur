package app.lanceur.widgets

import app.lanceur.builtin.BuiltinSlots
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
     * n'apparaît pas du tout : aucune vue n'est créée pour lui. Un widget intégré n'appartient à aucune appli : jamais
     * caché, indisponible seulement si son type est inconnu.
     */
    fun compute(slots: List<WidgetSlot>, hidden: Set<AppKey>, available: Set<Int>): List<WidgetCard> = with(VisibleApps) {
        val hiddenPackages = hidden.mapTo(HashSet()) { it.packageInProfile() }
        slots.filter { BuiltinSlots.isBuiltin(it) || it.provider.packageInProfile() !in hiddenPackages }
            .map { slot ->
                val live = if (BuiltinSlots.isBuiltin(slot)) BuiltinSlots.kindOf(slot) != null else slot.appWidgetId in available
                if (live) WidgetCard.Live(slot) else WidgetCard.Unavailable(slot)
            }
    }
}
