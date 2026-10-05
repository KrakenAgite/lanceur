package app.lanceur.widgets

/** Glisser-déposer quand les éléments n'ont pas tous la même hauteur. */
object HeightReorder {
    data class Result(val from: Int, val to: Int, val offset: Float)

    /**
     * L'élément `index` a été tiré de `offset` pixels (positif vers le bas). Il prend la place d'un voisin dès qu'il
     * en dépasse la moitié ; `offset` restant = décalage à afficher par rapport à sa nouvelle place.
     */
    fun step(heights: List<Float>, index: Int, offset: Float): Result {
        var to = index
        var rest = offset
        // Le sens est décidé une fois : en descendant, les voisins franchis sont index+1, index+2… (et inversement)
        if (offset > 0) {
            while (to < heights.lastIndex && rest > heights[to + 1] / 2) {
                rest -= heights[to + 1]
                to++
            }
        } else if (offset < 0) {
            while (to > 0 && -rest > heights[to - 1] / 2) {
                rest += heights[to - 1]
                to--
            }
        }
        return Result(index, to, rest)
    }
}
