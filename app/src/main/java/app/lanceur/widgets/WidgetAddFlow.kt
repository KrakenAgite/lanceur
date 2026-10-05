package app.lanceur.widgets

import app.lanceur.apps.AppKey

/**
 * Ajout d'un widget : autorisation d'Android, puis configuration éventuelle. Toute sortie anticipée libère
 * l'identifiant réservé, pour ne jamais laisser de widget fantôme. Les effets sont exécutés par `MainActivity`.
 */
object WidgetAddFlow {
    sealed interface State {
        data object Idle : State
        data class Binding(val id: Int, val entry: ProviderEntry) : State
        data class Configuring(val id: Int, val entry: ProviderEntry) : State
    }

    sealed interface Event {
        /** `bound` : lié sans demander ; `needsConfiguration` n'a de sens que si `bound`. */
        data class Start(val id: Int, val entry: ProviderEntry, val bound: Boolean, val needsConfiguration: Boolean) : Event
        data class BindResult(val granted: Boolean, val needsConfiguration: Boolean) : Event
        data class ConfigureResult(val ok: Boolean) : Event
    }

    sealed interface Effect {
        data class LaunchBind(val id: Int, val provider: AppKey) : Effect
        data class LaunchConfigure(val id: Int) : Effect
        data class Save(val slot: WidgetSlot) : Effect
        data class Delete(val id: Int) : Effect
    }

    fun reduce(state: State, event: Event): Pair<State, List<Effect>> = when (event) {
        is Event.Start -> {
            val abandoned = abandon(state)
            when {
                !event.bound -> State.Binding(event.id, event.entry) to abandoned + Effect.LaunchBind(event.id, event.entry.provider)
                event.needsConfiguration -> State.Configuring(event.id, event.entry) to abandoned + Effect.LaunchConfigure(event.id)
                else -> State.Idle to abandoned + save(event.id, event.entry)
            }
        }
        is Event.BindResult -> when {
            state !is State.Binding -> state to emptyList()
            !event.granted -> State.Idle to listOf(Effect.Delete(state.id))
            event.needsConfiguration -> State.Configuring(state.id, state.entry) to listOf(Effect.LaunchConfigure(state.id))
            else -> State.Idle to listOf(save(state.id, state.entry))
        }
        is Event.ConfigureResult -> when {
            state !is State.Configuring -> state to emptyList()
            event.ok -> State.Idle to listOf(save(state.id, state.entry))
            else -> State.Idle to listOf(Effect.Delete(state.id))
        }
    }

    private fun abandon(state: State): List<Effect> = when (state) {
        is State.Binding -> listOf(Effect.Delete(state.id))
        is State.Configuring -> listOf(Effect.Delete(state.id))
        State.Idle -> emptyList()
    }

    private fun save(id: Int, entry: ProviderEntry) =
        Effect.Save(WidgetSlot(id, entry.provider, WidgetSize.fromMinHeightDp(entry.minHeightDp)))
}
