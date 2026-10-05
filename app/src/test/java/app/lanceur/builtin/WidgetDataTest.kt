package app.lanceur.builtin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetDataTest {
    @Test
    fun round_trip_keeps_special_characters() {
        val values = mapOf("text" to "a=b\nc\\d\\n", "vide" to "", "clé=bizarre" to "ok")
        assertEquals(values, WidgetData.decode(WidgetData.encode(values)))
    }

    @Test
    fun unreadable_lines_are_ignored() {
        assertEquals(mapOf("ok" to "1"), WidgetData.decode("ok=1\nsans séparateur"))
        assertTrue(WidgetData.decode(null).isEmpty())
        assertTrue(WidgetData.decode("").isEmpty())
    }

    @Test
    fun lists_round_trip() {
        val items = listOf("pain", "lait=2", "a\nb")
        assertEquals(items, WidgetData.unlist(WidgetData.decode(WidgetData.encode(mapOf("l" to WidgetData.list(items))))["l"]))
        assertTrue(WidgetData.unlist(null).isEmpty())
        assertTrue(WidgetData.unlist("").isEmpty())
    }
}
