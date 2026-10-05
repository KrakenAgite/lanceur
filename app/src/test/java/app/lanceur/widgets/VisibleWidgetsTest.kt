package app.lanceur.widgets

import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Test

class VisibleWidgetsTest {
    private val meteo = WidgetSlot(1, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.MEDIUM)
    private val musique = WidgetSlot(2, AppKey("app.musique", "app.musique.Player", 0), WidgetSize.SMALL)
    private val agenda = WidgetSlot(3, AppKey("app.agenda", "app.agenda.Month", 0), WidgetSize.LARGE)

    @Test
    fun available_widgets_are_live_in_their_order() {
        val cards = VisibleWidgets.compute(listOf(agenda, meteo), hidden = emptySet(), available = setOf(1, 3))
        assertEquals(listOf(WidgetCard.Live(agenda), WidgetCard.Live(meteo)), cards)
    }

    @Test
    fun unknown_widgets_are_marked_unavailable() {
        val cards = VisibleWidgets.compute(listOf(meteo, musique), hidden = emptySet(), available = setOf(1))
        assertEquals(listOf(WidgetCard.Live(meteo), WidgetCard.Unavailable(musique)), cards)
    }

    @Test
    fun hidden_package_stays_hidden_even_with_another_class() {
        // L'appli est cachée sous son activité principale ; son widget a une autre classe dans le même paquet
        val hiddenApp = AppKey("app.musique", "app.musique.MainActivity", 0)
        val cards = VisibleWidgets.compute(listOf(meteo, musique), hidden = setOf(hiddenApp), available = setOf(1, 2))
        assertEquals(listOf(WidgetCard.Live(meteo)), cards)
    }

    @Test
    fun hiding_is_per_profile() {
        val workMusique = musique.copy(appWidgetId = 4, provider = musique.provider.copy(userSerial = 10))
        val hiddenApp = AppKey("app.musique", "app.musique.MainActivity", 0)
        val cards = VisibleWidgets.compute(listOf(musique, workMusique), hidden = setOf(hiddenApp), available = setOf(2, 4))
        assertEquals(listOf(WidgetCard.Live(workMusique)), cards)
    }

    @Test
    fun builtin_widgets_are_always_live_and_unknown_kinds_unavailable() {
        val battery = app.lanceur.builtin.BuiltinSlots.create(app.lanceur.builtin.BuiltinKind.BATTERY_BAR, emptyList())
        val future = WidgetSlot(-9, AppKey("app.lanceur.builtin", "HOLOGRAM", 0), WidgetSize.SMALL)
        val hiddenEverything = setOf(AppKey("app.lanceur.builtin", "x", 0), AppKey("app.meteo", "app.meteo.Main", 0))
        val cards = VisibleWidgets.compute(listOf(battery, meteo, future), hidden = hiddenEverything, available = emptySet())
        assertEquals(listOf(WidgetCard.Live(battery), WidgetCard.Unavailable(future)), cards)
    }
}
