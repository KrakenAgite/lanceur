package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.builtin.BuiltinKind
import app.lanceur.builtin.BuiltinSlots
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetLayoutTest {
    private val android = WidgetSlot(3, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.LARGE)
    private fun builtin(kind: BuiltinKind, size: WidgetSize) = BuiltinSlots.create(kind, emptyList()).copy(size = size)

    @Test
    fun android_widgets_keep_the_shared_heights_and_all_sizes() {
        assertEquals(340, WidgetLayout.heightDp(android))
        assertEquals(WidgetSize.entries, WidgetLayout.sizes(android))
    }

    @Test
    fun builtin_widgets_have_their_own_heights() {
        assertEquals(400, WidgetLayout.heightDp(builtin(BuiltinKind.CALENDAR_MONTH, WidgetSize.MEDIUM)))
        assertEquals(120, WidgetLayout.heightDp(builtin(BuiltinKind.NOW_PLAYING, WidgetSize.SMALL)))
        assertEquals(280, WidgetLayout.heightDp(builtin(BuiltinKind.CALENDAR_WEEK, WidgetSize.LARGE)))
    }

    @Test
    fun battery_widgets_offer_a_single_size() {
        assertEquals(listOf(WidgetSize.SMALL), WidgetLayout.sizes(builtin(BuiltinKind.BATTERY_RING, WidgetSize.SMALL)))
        assertEquals(72, WidgetLayout.heightDp(builtin(BuiltinKind.BATTERY_BAR, WidgetSize.SMALL)))
    }

    @Test
    fun size_not_offered_falls_back_to_default() {
        val tooBig = builtin(BuiltinKind.BATTERY_RING, WidgetSize.LARGE)
        assertEquals(WidgetSize.SMALL, WidgetLayout.displaySize(tooBig))
        assertEquals(140, WidgetLayout.heightDp(tooBig))
    }
}
