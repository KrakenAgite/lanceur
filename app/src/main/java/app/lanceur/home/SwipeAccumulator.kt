package app.lanceur.home

enum class Swipe { UP, DOWN }

/** Additionne les déplacements verticaux d'un geste et se déclenche au plus une fois, au-delà du seuil. */
class SwipeAccumulator(private val thresholdPx: Float) {
    private var total = 0f
    private var fired = false

    fun add(dy: Float): Swipe? {
        if (fired) return null
        total += dy
        val result = when {
            total <= -thresholdPx -> Swipe.UP
            total >= thresholdPx -> Swipe.DOWN
            else -> null
        }
        if (result != null) fired = true
        return result
    }

    fun reset() {
        total = 0f
        fired = false
    }
}
