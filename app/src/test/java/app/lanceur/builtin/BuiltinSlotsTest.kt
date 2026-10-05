package app.lanceur.builtin

import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuiltinSlotsTest {
    private val android = WidgetSlot(7, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)

    @Test
    fun next_id_is_negative_and_below_every_stored_id() {
        assertEquals(-1, BuiltinSlots.nextId(emptyList()))
        assertEquals(-1, BuiltinSlots.nextId(listOf(android)))
        val first = BuiltinSlots.create(BuiltinKind.BATTERY_BAR, listOf(android))
        assertEquals(-2, BuiltinSlots.nextId(listOf(android, first)))
    }

    @Test
    fun created_slot_uses_the_reserved_package_and_default_size() {
        val slot = BuiltinSlots.create(BuiltinKind.CALENDAR_MONTH, emptyList())
        assertEquals(WidgetSlot(-1, AppKey("app.lanceur.builtin", "CALENDAR_MONTH", 0), WidgetSize.MEDIUM), slot)
        assertTrue(BuiltinSlots.isBuiltin(slot))
        assertFalse(BuiltinSlots.isBuiltin(android))
        assertEquals(BuiltinKind.CALENDAR_MONTH, BuiltinSlots.kindOf(slot))
    }

    @Test
    fun survives_the_storage_round_trip() {
        val slot = BuiltinSlots.create(BuiltinKind.NOW_PLAYING, emptyList())
        assertEquals(slot, WidgetSlot.decode(slot.encode()))
    }

    @Test
    fun unknown_kind_from_a_future_version_is_builtin_without_kind() {
        val future = WidgetSlot(-4, AppKey("app.lanceur.builtin", "HOLOGRAM", 0), WidgetSize.SMALL)
        assertTrue(BuiltinSlots.isBuiltin(future))
        assertNull(BuiltinSlots.kindOf(future))
        assertNull(BuiltinSlots.kindOf(android))
    }
}
