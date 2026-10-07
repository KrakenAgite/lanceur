package app.lanceur.alphabet

import app.lanceur.prefs.AlphabetSide

/**
 * Colonne de l'alphabet avec les dossiers au-dessus, parcourue d'un seul geste : le doigt passe des lettres aux
 * dossiers (et inversement) sans se lever. Coordonnées locales à la colonne entière.
 */
data class BarGeometry(
    val width: Float,
    val folderCount: Int,
    val folderTop: Float,
    val folderPitch: Float,
    val alphabetTop: Float,
    val alphabetHeight: Float,
    val side: AlphabetSide,
    val inwardThresholdPx: Float,
) {
    val folderBottom: Float get() = folderTop + folderCount * folderPitch
}

sealed interface BarPhase {
    data object Idle : BarPhase
    data class OnFolder(val index: Int) : BarPhase

    /** `fingerUnits` : position du doigt en lettres, pour la vague (voir [ScrubPhase.OnBar]). */
    data class OnLetter(val index: Int, val fingerUnits: Float) : BarPhase

    /** Doigt parti vers la liste : [from] (dossier ou lettre) reste affiché. */
    data class OnList(val from: BarPhase) : BarPhase
}

object BarScrub {
    /** Un geste ne commence que sur une icône de dossier ou sur l'alphabet, pas dans le vide entre les deux. */
    fun startsOnBar(y: Float, g: BarGeometry): Boolean =
        (y >= g.folderTop && y < g.folderBottom) || y >= g.alphabetTop

    fun next(current: BarPhase, x: Float, y: Float, g: BarGeometry, enabled: List<Boolean>): BarPhase {
        val inward = if (g.side == AlphabetSide.RIGHT) -x else x - g.width
        if (inward > g.inwardThresholdPx && current != BarPhase.Idle) {
            return current as? BarPhase.OnList ?: BarPhase.OnList(current)
        }
        // Au-dessus de l'alphabet : les dossiers ; entre le dernier dossier et le « A », on reste sur le dernier
        if (g.folderCount > 0 && y < g.alphabetTop && g.folderPitch > 0f) {
            val index = ((y - g.folderTop) / g.folderPitch).toInt().coerceIn(0, g.folderCount - 1)
            return BarPhase.OnFolder(index)
        }
        val input = ScrubInput(x, y - g.alphabetTop, g.width, g.alphabetHeight, g.side, g.inwardThresholdPx)
        return when (val letter = Scrub.next(ScrubPhase.Idle, input, enabled)) {
            is ScrubPhase.OnBar -> BarPhase.OnLetter(letter.index, letter.fingerUnits)
            else -> BarPhase.Idle
        }
    }

    /** Ce que l'alphabet doit dessiner (vague et lettre active). */
    fun letterPhase(phase: BarPhase): ScrubPhase = when (phase) {
        is BarPhase.OnLetter -> ScrubPhase.OnBar(phase.index, phase.fingerUnits)
        is BarPhase.OnList -> (phase.from as? BarPhase.OnLetter)?.let { ScrubPhase.OnList(it.index) } ?: ScrubPhase.Idle
        else -> ScrubPhase.Idle
    }

    fun folderIndex(phase: BarPhase): Int? = when (phase) {
        is BarPhase.OnFolder -> phase.index
        is BarPhase.OnList -> (phase.from as? BarPhase.OnFolder)?.index
        else -> null
    }
}
