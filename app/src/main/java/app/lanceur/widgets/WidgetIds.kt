package app.lanceur.widgets

object WidgetIds {
    /** Identifiants réservés auprès d'Android mais absents des réglages (ajout interrompu) : à libérer. */
    fun orphans(hostIds: IntArray, slots: List<WidgetSlot>): List<Int> {
        val kept = slots.mapTo(HashSet()) { it.appWidgetId }
        return hostIds.filter { it !in kept }
    }
}
