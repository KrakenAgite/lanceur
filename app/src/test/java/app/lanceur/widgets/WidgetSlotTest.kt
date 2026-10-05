package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSlotTest {
    private val slot = WidgetSlot(7, AppKey("app.meteo", "app.meteo.Widget", 10), WidgetSize.LARGE)

    @Test
    fun encode_then_decode_gives_the_same_slot() {
        assertEquals("7|app.meteo/app.meteo.Widget#10|LARGE", slot.encode())
        assertEquals(slot, WidgetSlot.decode(slot.encode()))
    }

    @Test
    fun malformed_values_are_rejected() {
        listOf("", "x|a/b#0|SMALL", "1|pas-une-cle|SMALL", "1|a/b#0|ENORME", "1|a/b#0", "1|a/b#0|SMALL|en-trop").forEach {
            assertNull(it, WidgetSlot.decode(it))
        }
    }

    @Test
    fun starting_size_follows_the_widget_minimum_height() {
        assertEquals(WidgetSize.SMALL, WidgetSize.fromMinHeightDp(40))
        assertEquals(WidgetSize.SMALL, WidgetSize.fromMinHeightDp(120))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.fromMinHeightDp(121))
        assertEquals(WidgetSize.MEDIUM, WidgetSize.fromMinHeightDp(220))
        assertEquals(WidgetSize.LARGE, WidgetSize.fromMinHeightDp(221))
    }
}
