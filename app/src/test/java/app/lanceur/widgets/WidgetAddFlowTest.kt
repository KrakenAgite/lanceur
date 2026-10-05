package app.lanceur.widgets

import app.lanceur.apps.AppKey
import app.lanceur.apps.ProfileKind
import app.lanceur.widgets.WidgetAddFlow.Effect
import app.lanceur.widgets.WidgetAddFlow.Event
import app.lanceur.widgets.WidgetAddFlow.State
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetAddFlowTest {
    private val meteo = ProviderEntry(AppKey("app.meteo", "app.meteo.Widget", 0), ProfileKind.MAIN, "Météo", "Jour", 200)
    private val saved = Effect.Save(WidgetSlot(7, meteo.provider, WidgetSize.MEDIUM))

    @Test
    fun bound_without_configuration_is_saved_right_away() {
        assertEquals(State.Idle to listOf(saved), WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = true, needsConfiguration = false)))
    }

    @Test
    fun refused_permission_frees_the_id() {
        val (binding, effects) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = false, needsConfiguration = false))
        assertEquals(State.Binding(7, meteo), binding)
        assertEquals(listOf(Effect.LaunchBind(7, meteo.provider)), effects)
        assertEquals(State.Idle to listOf(Effect.Delete(7)), WidgetAddFlow.reduce(binding, Event.BindResult(granted = false, needsConfiguration = false)))
    }

    @Test
    fun granted_then_configured_is_saved() {
        val (binding, _) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = false, needsConfiguration = false))
        val (configuring, effects) = WidgetAddFlow.reduce(binding, Event.BindResult(granted = true, needsConfiguration = true))
        assertEquals(State.Configuring(7, meteo), configuring)
        assertEquals(listOf(Effect.LaunchConfigure(7)), effects)
        assertEquals(State.Idle to listOf(saved), WidgetAddFlow.reduce(configuring, Event.ConfigureResult(ok = true)))
    }

    @Test
    fun cancelled_configuration_frees_the_id() {
        val (configuring, _) = WidgetAddFlow.reduce(State.Idle, Event.Start(7, meteo, bound = true, needsConfiguration = true))
        assertEquals(State.Idle to listOf(Effect.Delete(7)), WidgetAddFlow.reduce(configuring, Event.ConfigureResult(ok = false)))
    }

    @Test
    fun result_without_pending_add_is_ignored() {
        // Lanceur a été relancé entre-temps : l'identifiant sera libéré par le nettoyage des orphelins
        assertEquals(State.Idle to emptyList<Effect>(), WidgetAddFlow.reduce(State.Idle, Event.ConfigureResult(ok = true)))
        assertEquals(State.Idle to emptyList<Effect>(), WidgetAddFlow.reduce(State.Idle, Event.BindResult(granted = true, needsConfiguration = false)))
    }

    @Test
    fun new_add_while_one_is_pending_frees_the_old_id() {
        val other = meteo.copy(minHeightDp = 80)
        val (state, effects) = WidgetAddFlow.reduce(State.Binding(7, meteo), Event.Start(8, other, bound = true, needsConfiguration = false))
        assertEquals(State.Idle, state)
        assertEquals(listOf(Effect.Delete(7), Effect.Save(WidgetSlot(8, other.provider, WidgetSize.SMALL))), effects)
    }
}
