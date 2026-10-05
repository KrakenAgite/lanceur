package app.lanceur.builtin

import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSize.LARGE
import app.lanceur.widgets.WidgetSize.MEDIUM
import app.lanceur.widgets.WidgetSize.SMALL

/** Widgets dessinés par Lanceur. Le nom de chaque entrée est enregistré dans les réglages : ne jamais le renommer. */
enum class BuiltinKind(
    val label: String,
    val emoji: String,
    private val heights: Map<WidgetSize, Int>,
    val defaultSize: WidgetSize,
    /** Réglé par une feuille, à l'ajout et par ⚙ en mode édition. */
    val configurable: Boolean = false,
) {
    NOW_PLAYING("Lecture en cours", "🎵", mapOf(SMALL to 120, MEDIUM to 200, LARGE to 300), MEDIUM),
    CALENDAR_MONTH("Agenda · mois", "📅", mapOf(SMALL to 300, MEDIUM to 400, LARGE to 520), MEDIUM),
    CALENDAR_WEEK("Agenda · semaine", "🗓️", mapOf(SMALL to 140, MEDIUM to 200, LARGE to 280), MEDIUM),
    BATTERY_RING("Batterie · anneau", "🔋", mapOf(SMALL to 140), SMALL),
    BATTERY_BAR("Batterie · barre", "🔋", mapOf(SMALL to 72), SMALL),
    NOTE("Note rapide", "📝", mapOf(SMALL to 140, MEDIUM to 220, LARGE to 340), MEDIUM),
    ;

    /** Tailles proposées en mode édition, dans l'ordre S, M, L. */
    val sizes: List<WidgetSize> get() = WidgetSize.entries.filter { it in heights }

    /** Taille enregistrée mais non proposée (réglage d'une autre version) : on affiche la taille par défaut. */
    fun effectiveSize(size: WidgetSize): WidgetSize = if (size in heights) size else defaultSize

    fun heightDp(size: WidgetSize): Int = heights.getValue(effectiveSize(size))
}
