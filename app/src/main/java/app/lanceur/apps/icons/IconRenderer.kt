package app.lanceur.apps.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Couleurs Material You des icônes thématisées : fond et dessin. */
data class ThemedColors(val background: Int, val foreground: Int)

/** Dessine une icône d'Android dans la forme choisie, ou en version thématisée. */
object IconRenderer {
    fun shapePath(shape: IconShape, size: Float): Path? = when (shape) {
        IconShape.SYSTEM -> null
        IconShape.CIRCLE -> Path().apply { addCircle(size / 2, size / 2, size / 2, Path.Direction.CW) }
        IconShape.ROUNDED -> roundRect(size, 0.22f)
        IconShape.SOFT_SQUARE -> roundRect(size, 0.1f)
        IconShape.SQUARE -> Path().apply { addRect(0f, 0f, size, size, Path.Direction.CW) }
        IconShape.TEARDROP -> corners(size, 0.5f, 0.5f, 0.12f, 0.5f)
        IconShape.LEAF -> corners(size, 0.12f, 0.5f, 0.12f, 0.5f)
        IconShape.CYLINDER -> Path().apply {
            // Côtés droits, haut et bas en demi-ellipses
            val h = size * 0.16f
            moveTo(0f, h)
            arcTo(RectF(0f, 0f, size, 2 * h), 180f, 180f)
            lineTo(size, size - h)
            arcTo(RectF(0f, size - 2 * h, size, size), 0f, 180f)
            close()
        }
        IconShape.ARCH -> corners(size, 0.5f, 0.5f, 0.1f, 0.1f)
        IconShape.SHIELD -> polygon(size, listOf(0f to 0f, 1f to 0f, 1f to 0.6f, 0.5f to 1f, 0f to 0.6f), round = 0.22f)
        IconShape.GEM -> polygon(size, listOf(0.25f to 0f, 0.75f to 0f, 1f to 0.35f, 0.5f to 1f, 0f to 0.35f), round = 0.2f)
        IconShape.DIAMOND -> regular(size, 4, rotation = 0.0, round = 0.25f)
        IconShape.TRIANGLE -> regular(size, 3, rotation = 0.0, round = 0.22f)
        IconShape.PENTAGON -> regular(size, 5, rotation = 0.0, round = 0.2f)
        IconShape.HEXAGON -> regular(size, 6, rotation = Math.PI / 6, round = 0.2f)
        IconShape.OCTAGON -> regular(size, 8, rotation = Math.PI / 8, round = 0.18f)
        IconShape.COOKIE -> polar(size) { 0.92 + 0.08 * cos(9 * it) }
        IconShape.SUNNY -> polar(size) { 0.88 + 0.12 * cos(8 * it) }
        IconShape.FLOWER -> polar(size) { 0.8 + 0.2 * abs(cos(3 * it)) }
        IconShape.CLOVER -> polar(size) { 0.72 + 0.28 * abs(cos(2 * it)).pow(0.6) }
        IconShape.STAR -> polar(size, start = -Math.PI / 2) { 0.8 + 0.2 * cos(5 * it) }
        IconShape.BLOB -> polar(size) { 0.9 + 0.06 * sin(3 * it) + 0.04 * cos(5 * it + 1) }
        IconShape.HEART -> fit(size, Path().apply {
            for (i in 0..STEPS) {
                val t = 2 * Math.PI * i / STEPS
                val x = 16 * sin(t).pow(3)
                val y = -(13 * cos(t) - 5 * cos(2 * t) - 2 * cos(3 * t) - cos(4 * t))
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        })
        IconShape.SQUIRCLE -> Path().apply {
            // Superellipse |x|^4 + |y|^4 = 1
            val r = size / 2
            for (i in 0..96) {
                val t = 2 * Math.PI * i / 96
                val c = cos(t)
                val s = sin(t)
                val x = r + r * sign(c) * abs(c).pow(0.5)
                val y = r + r * sign(s) * abs(s).pow(0.5)
                if (i == 0) moveTo(x.toFloat(), y.toFloat()) else lineTo(x.toFloat(), y.toFloat())
            }
            close()
        }
    }

    private const val STEPS = 180

    private fun roundRect(size: Float, radius: Float) =
        Path().apply { addRoundRect(RectF(0f, 0f, size, size), size * radius, size * radius, Path.Direction.CW) }

    /** Carré aux coins arrondis un à un (fractions de la taille) : haut gauche, haut droit, bas droit, bas gauche. */
    private fun corners(size: Float, tl: Float, tr: Float, br: Float, bl: Float) = Path().apply {
        val r = floatArrayOf(tl, tl, tr, tr, br, br, bl, bl).map { it * size }.toFloatArray()
        addRoundRect(RectF(0f, 0f, size, size), r, Path.Direction.CW)
    }

    /** Contour polaire `r(θ)` (1 = rayon plein), agrandi pour remplir le carré. */
    private fun polar(size: Float, start: Double = 0.0, radius: (Double) -> Double) = fit(size, Path().apply {
        for (i in 0..STEPS) {
            val t = start + 2 * Math.PI * i / STEPS
            val r = radius(t - start)
            val x = (r * cos(t)).toFloat()
            val y = (r * sin(t)).toFloat()
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    })

    /** Polygone régulier à [n] côtés, pointe en haut puis tourné de [rotation]. */
    private fun regular(size: Float, n: Int, rotation: Double, round: Float) = polygon(
        size,
        List(n) { i ->
            val t = -Math.PI / 2 + rotation + 2 * Math.PI * i / n
            cos(t).toFloat() to sin(t).toFloat()
        },
        round,
    )

    /** Polygone aux coins arrondis : chaque coin est remplacé par une courbe qui part à [round] de chaque côté. */
    private fun polygon(size: Float, points: List<Pair<Float, Float>>, round: Float) = fit(size, Path().apply {
        val n = points.size
        fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, k: Float) = (a.first + (b.first - a.first) * k) to (a.second + (b.second - a.second) * k)
        points.forEachIndexed { i, v ->
            val before = lerp(v, points[(i + n - 1) % n], round)
            val after = lerp(v, points[(i + 1) % n], round)
            if (i == 0) moveTo(before.first, before.second) else lineTo(before.first, before.second)
            quadTo(v.first, v.second, after.first, after.second)
        }
        close()
    })

    /** Met le contour à l'échelle du carré [size], centré, proportions gardées. */
    private fun fit(size: Float, path: Path): Path {
        val bounds = RectF().also { path.computeBounds(it, true) }
        path.transform(android.graphics.Matrix().apply { setRectToRect(bounds, RectF(0f, 0f, size, size), android.graphics.Matrix.ScaleToFit.CENTER) })
        return path
    }

    /**
     * Icône adaptative : calques fond + dessin (ou dessin monochrome teinté si [themed]) découpés dans la forme.
     * Une icône non adaptative est rendue telle quelle, sauf thématisée : elle garde ses couleurs.
     */
    fun render(icon: Drawable, shape: IconShape, themed: ThemedColors?, sizePx: Int): Bitmap {
        if (icon !is AdaptiveIconDrawable) return icon.toBitmap(sizePx, sizePx)
        val size = sizePx.toFloat()
        val out = createBitmap(sizePx, sizePx)
        val canvas = Canvas(out)
        val clip = shapePath(shape, size) ?: Path(icon.iconMask).apply {
            transform(android.graphics.Matrix().apply { setScale(size / 100f, size / 100f) })
        }
        // Bord lissé : la forme est peinte, puis les calques ne gardent que ce qui la recouvre
        canvas.drawPath(clip, Paint(Paint.ANTI_ALIAS_FLAG))
        canvas.saveLayer(0f, 0f, size, size, Paint().apply { xfermode = android.graphics.PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
        // Les calques font 108 dp pour 72 dp visibles : on déborde de 1/6 de chaque côté
        val inset = (size * 0.25f).toInt()
        val bounds = android.graphics.Rect(-inset, -inset, sizePx + inset, sizePx + inset)
        val mono = icon.monochrome
        if (themed != null && mono != null) {
            canvas.drawColor(themed.background)
            mono.mutate().bounds = bounds
            mono.colorFilter = PorterDuffColorFilter(themed.foreground, PorterDuff.Mode.SRC_IN)
            mono.draw(canvas)
        } else {
            icon.background?.let { it.bounds = bounds; it.draw(canvas) }
            icon.foreground?.let { it.bounds = bounds; it.draw(canvas) }
        }
        canvas.restore()
        return out
    }

    /** Dessin d'un pack : tel quel, à la bonne taille. */
    fun renderPack(drawable: Drawable, sizePx: Int): Bitmap = drawable.toBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
}
