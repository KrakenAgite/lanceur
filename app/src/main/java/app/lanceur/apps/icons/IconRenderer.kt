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
        IconShape.ROUNDED -> Path().apply { addRoundRect(RectF(0f, 0f, size, size), size * 0.22f, size * 0.22f, Path.Direction.CW) }
        IconShape.TEARDROP -> Path().apply {
            val r = size / 2
            val small = size * 0.12f
            addRoundRect(RectF(0f, 0f, size, size), floatArrayOf(r, r, r, r, small, small, r, r), Path.Direction.CW)
        }
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
