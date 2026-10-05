package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetIdsTest {
    @Test
    fun ids_reserved_but_not_saved_are_orphans() {
        val saved = listOf(WidgetSlot(2, AppKey("a", "a.W", 0), WidgetSize.SMALL))
        assertEquals(listOf(1, 3), WidgetIds.orphans(intArrayOf(1, 2, 3), saved))
        assertEquals(emptyList<Int>(), WidgetIds.orphans(intArrayOf(2), saved))
    }

    @Test
    fun builtin_ids_are_never_orphans() {
        val builtin = app.lanceur.builtin.BuiltinSlots.create(app.lanceur.builtin.BuiltinKind.BATTERY_RING, emptyList())
        assertEquals(listOf(5), WidgetIds.orphans(intArrayOf(5), listOf(builtin)))
    }
}
