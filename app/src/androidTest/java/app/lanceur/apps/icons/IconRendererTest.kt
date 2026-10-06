package app.lanceur.apps.icons

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import org.junit.Assert.assertEquals
import org.junit.Test

class IconRendererTest {
    private val icon = AdaptiveIconDrawable(ColorDrawable(Color.RED), ColorDrawable(Color.TRANSPARENT), ColorDrawable(Color.WHITE))

    @Test
    fun circle_leaves_corners_empty_and_fills_the_center() {
        val bitmap = IconRenderer.render(icon, IconShape.CIRCLE, null, 96)
        assertEquals(0, Color.alpha(bitmap.getPixel(1, 1)))
        assertEquals(Color.RED, bitmap.getPixel(48, 48))
    }

    @Test
    fun rounded_square_keeps_more_than_the_circle() {
        val bitmap = IconRenderer.render(icon, IconShape.ROUNDED, null, 96)
        assertEquals(Color.RED, bitmap.getPixel(10, 10))
        assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
    }

    @Test
    fun themed_icon_uses_the_monochrome_layer_on_the_theme_background() {
        val bitmap = IconRenderer.render(icon, IconShape.CIRCLE, ThemedColors(Color.BLUE, Color.GREEN), 96)
        assertEquals(Color.GREEN, bitmap.getPixel(48, 48))
    }

    @Test
    fun non_adaptive_icon_is_left_as_is() {
        val bitmap = IconRenderer.render(ColorDrawable(Color.RED), IconShape.CIRCLE, null, 32)
        assertEquals(Color.RED, bitmap.getPixel(0, 0))
    }
}
