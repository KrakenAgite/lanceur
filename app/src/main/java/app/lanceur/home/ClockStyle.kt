package app.lanceur.home

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.i18n.tr

enum class ClockFont(private val fr: String, private val en: String, val family: FontFamily, val weight: FontWeight) {
    STANDARD("Standard", "Standard", FontFamily.SansSerif, FontWeight.Medium),
    THIN("Fine", "Thin", FontFamily.SansSerif, FontWeight.ExtraLight),
    BOLD("Grasse", "Bold", FontFamily.SansSerif, FontWeight.Bold),
    SERIF("Classique", "Classic", FontFamily.Serif, FontWeight.Normal),
    MONO("Machine", "Typewriter", FontFamily.Monospace, FontWeight.Normal),
    CURSIVE("Manuscrite", "Handwritten", FontFamily.Cursive, FontWeight.Normal),
    ;

    val label: String get() = tr(fr, en)
}

enum class ClockSize(private val fr: String, private val en: String, val points: Int) {
    SMALL("Petite", "Small", 44),
    MEDIUM("Moyenne", "Medium", 57),
    LARGE("Grande", "Large", 76),
    ;

    val label: String get() = tr(fr, en)
}

/** Style de l'heure de l'accueil. [stacked] : heures au-dessus des minutes. */
data class ClockStyle(
    val font: ClockFont = ClockFont.STANDARD,
    val size: ClockSize = ClockSize.MEDIUM,
    val stacked: Boolean = false,
    val showDate: Boolean = true,
) {
    fun timeStyle(base: TextStyle): TextStyle = base.copy(
        fontFamily = font.family,
        fontWeight = font.weight,
        fontSize = size.points.sp,
        lineHeight = (size.points * 1.12f).sp,
    )

    /**
     * Hauteur de l'en-tête de l'accueil (marges, heure, date) : les dossiers et l'alphabet commencent dessous.
     * Le style par défaut garde les 168 dp d'avant.
     */
    val headerHeight: Dp
        get() = (64f + (if (stacked) 2 else 1) * size.points * 1.12f + (if (showDate) 28f else 0f) + 12f).dp

    fun encode(): String = listOf(font.name, size.name, stacked, showDate).joinToString("|")

    companion object {
        fun decode(value: String?): ClockStyle {
            val parts = value?.split('|') ?: return ClockStyle()
            return ClockStyle(
                font = ClockFont.entries.firstOrNull { it.name == parts.getOrNull(0) } ?: ClockFont.STANDARD,
                size = ClockSize.entries.firstOrNull { it.name == parts.getOrNull(1) } ?: ClockSize.MEDIUM,
                stacked = parts.getOrNull(2) == "true",
                showDate = parts.getOrNull(3) != "false",
            )
        }
    }
}
