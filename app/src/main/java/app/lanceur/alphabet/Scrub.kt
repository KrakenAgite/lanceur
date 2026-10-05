package app.lanceur.alphabet

import app.lanceur.prefs.AlphabetSide

/** Position du doigt, en coordonnées locales à la barre. */
data class ScrubInput(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
    val side: AlphabetSide,
    val inwardThresholdPx: Float,
)

sealed interface ScrubPhase {
    data object Idle : ScrubPhase

    /** Doigt sur la barre ; `fingerUnits` = position du doigt en lettres (0 = centre de la première). */
    data class OnBar(val index: Int, val fingerUnits: Float) : ScrubPhase

    /** Doigt parti vers la liste : la lettre `index` reste affichée, la ligne visée se trouve par position. */
    data class OnList(val index: Int) : ScrubPhase
}

val ScrubPhase.letterIndex: Int
    get() = when (this) {
        is ScrubPhase.OnBar -> index
        is ScrubPhase.OnList -> index
        ScrubPhase.Idle -> -1
    }

object Scrub {
    fun next(current: ScrubPhase, input: ScrubInput, enabled: List<Boolean>): ScrubPhase {
        if (input.height <= 0f || enabled.isEmpty()) return ScrubPhase.Idle
        val inward = if (input.side == AlphabetSide.RIGHT) -input.x else input.x - input.width
        val last = current.letterIndex
        if (inward > input.inwardThresholdPx && last >= 0) return ScrubPhase.OnList(last)
        val count = enabled.size
        // y * count / height (et non y / height * count) : résultat exact pour les positions au centre d'une lettre
        val units = (input.y * count / input.height).coerceIn(0f, count.toFloat())
        val index = WaveMath.nearestEnabled(units.toInt().coerceAtMost(count - 1), enabled)
        return if (index < 0) ScrubPhase.Idle else ScrubPhase.OnBar(index, units - 0.5f)
    }
}
