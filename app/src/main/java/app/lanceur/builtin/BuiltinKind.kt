package app.lanceur.builtin

import app.lanceur.i18n.tr
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSize.LARGE
import app.lanceur.widgets.WidgetSize.MEDIUM
import app.lanceur.widgets.WidgetSize.SMALL

/** Widgets dessinés par Lanceur. Le nom de chaque entrée est enregistré dans les réglages : ne jamais le renommer. */
enum class BuiltinKind(
    private val fr: String,
    private val en: String,
    val emoji: String,
    private val heights: Map<WidgetSize, Int>,
    val defaultSize: WidgetSize,
    /** Réglé par une feuille, à l'ajout et par ⚙ en mode édition. */
    val configurable: Boolean = false,
) {
    NOW_PLAYING("Lecture en cours", "Now playing", "🎵", mapOf(SMALL to 120, MEDIUM to 200, LARGE to 300), MEDIUM),
    CALENDAR_MONTH("Agenda · mois", "Calendar · month", "📅", mapOf(SMALL to 300, MEDIUM to 400, LARGE to 520), MEDIUM),
    CALENDAR_WEEK("Agenda · semaine", "Calendar · week", "🗓️", mapOf(SMALL to 140, MEDIUM to 200, LARGE to 280), MEDIUM),
    BATTERY_RING("Batterie · anneau", "Battery · ring", "🔋", mapOf(SMALL to 140), SMALL),
    BATTERY_BAR("Batterie · barre", "Battery · bar", "🔋", mapOf(SMALL to 72), SMALL),
    NOTE("Note rapide", "Quick note", "📝", mapOf(SMALL to 140, MEDIUM to 220, LARGE to 340), MEDIUM),
    TODO("To-do", "To-do", "✅", mapOf(SMALL to 160, MEDIUM to 260, LARGE to 380), MEDIUM),
    COUNTDOWN("Compte à rebours", "Countdown", "⏳", mapOf(SMALL to 120, MEDIUM to 200), SMALL, configurable = true),
    WORLD_CLOCKS("Horloges du monde", "World clocks", "🌍", mapOf(SMALL to 120, MEDIUM to 200), MEDIUM, configurable = true),
    TIMER("Minuteur / chrono", "Timer / stopwatch", "⏱", mapOf(MEDIUM to 200), MEDIUM),
    FAVORITE_CONTACTS("Contacts favoris", "Favorite contacts", "⭐", mapOf(SMALL to 120, MEDIUM to 220), SMALL),
    SHORTCUTS("Raccourcis rapides", "Quick shortcuts", "⚡", mapOf(SMALL to 120), SMALL),
    STORAGE("Stockage et mémoire", "Storage & memory", "💾", mapOf(SMALL to 140), SMALL),
    WEATHER("Météo", "Weather", "🌤", mapOf(SMALL to 120, MEDIUM to 220, LARGE to 340), MEDIUM, configurable = true),
    RSS("Flux RSS", "RSS feeds", "📰", mapOf(SMALL to 160, MEDIUM to 280, LARGE to 420), MEDIUM, configurable = true),
    ;

    val label: String get() = tr(fr, en)

    /** Tailles proposées en mode édition, dans l'ordre S, M, L. */
    val sizes: List<WidgetSize> get() = WidgetSize.entries.filter { it in heights }

    /** Taille enregistrée mais non proposée (réglage d'une autre version) : on affiche la taille par défaut. */
    fun effectiveSize(size: WidgetSize): WidgetSize = if (size in heights) size else defaultSize

    fun heightDp(size: WidgetSize): Int = heights.getValue(effectiveSize(size))
}
