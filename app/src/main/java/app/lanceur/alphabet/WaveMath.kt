package app.lanceur.alphabet

import kotlin.math.exp

/** Vague de l'alphabet : une gaussienne centrée sur le doigt, en « nombre de lettres ». */
object WaveMath {
    const val SIGMA = 1.5f
    const val MAX_EXTRA_SCALE = 1.2f
    const val MAX_SHIFT_DP = 28f

    fun factor(distance: Float): Float = exp(-(distance * distance) / (2f * SIGMA * SIGMA))

    fun scale(distance: Float): Float = 1f + MAX_EXTRA_SCALE * factor(distance)

    fun shiftDp(distance: Float): Float = MAX_SHIFT_DP * factor(distance)

    /** Lettre non vide la plus proche de `index` (la plus haute à égalité), ou -1 s'il n'y en a aucune. */
    fun nearestEnabled(index: Int, enabled: List<Boolean>): Int {
        if (enabled.isEmpty()) return -1
        val start = index.coerceIn(0, enabled.lastIndex)
        for (d in 0..enabled.size) {
            val up = start - d
            if (up >= 0 && enabled[up]) return up
            val down = start + d
            if (down <= enabled.lastIndex && enabled[down]) return down
        }
        return -1
    }
}
