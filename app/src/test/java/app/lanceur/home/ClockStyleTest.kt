package app.lanceur.home

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClockStyleTest {
    @Test
    fun default_style_keeps_the_former_header_height() {
        assertEquals(168f, ClockStyle().headerHeight.value, 0.5f)
    }

    @Test
    fun header_grows_with_size_and_two_lines_and_shrinks_without_date() {
        val base = ClockStyle().headerHeight
        assertTrue(ClockStyle(size = ClockSize.LARGE).headerHeight > base)
        assertTrue(ClockStyle(stacked = true).headerHeight > base + 50.dp)
        assertTrue(ClockStyle(showDate = false).headerHeight < base)
    }

    @Test
    fun style_survives_encoding_and_bad_values_fall_back() {
        val style = ClockStyle(ClockFont.SERIF, ClockSize.LARGE, stacked = true, showDate = false)
        assertEquals(style, ClockStyle.decode(style.encode()))
        assertEquals(ClockStyle(), ClockStyle.decode(null))
        assertEquals(ClockStyle(), ClockStyle.decode("NOPE|HUGE"))
    }
}
