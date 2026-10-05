package app.lanceur.vault

sealed interface VaultState {
    data object Locked : VaultState
    data object Authenticating : VaultState

    /** `keepOnNextBackground` : un écran système (verrou de l'Espace privé) va passer devant sans refermer le dossier. */
    data class Unlocked(val keepOnNextBackground: Boolean = false) : VaultState
}

enum class VaultEvent { OpenRequested, AuthSucceeded, AuthFailed, Backgrounded, ScreenOff, HomePressed, Closed, ExternalPromptStarted }

/** L'état n'existe qu'en mémoire : un redémarrage du launcher repart toujours de `Locked`. */
object VaultStateMachine {
    fun reduce(state: VaultState, event: VaultEvent): VaultState = when (state) {
        VaultState.Locked -> if (event == VaultEvent.OpenRequested) VaultState.Authenticating else state

        VaultState.Authenticating -> when (event) {
            VaultEvent.AuthSucceeded -> VaultState.Unlocked()
            VaultEvent.AuthFailed, VaultEvent.ScreenOff, VaultEvent.HomePressed, VaultEvent.Closed -> VaultState.Locked
            // Backgrounded : l'écran du code du téléphone passe devant pendant l'authentification
            else -> state
        }

        is VaultState.Unlocked -> when (event) {
            VaultEvent.ExternalPromptStarted -> VaultState.Unlocked(keepOnNextBackground = true)
            VaultEvent.Backgrounded -> if (state.keepOnNextBackground) VaultState.Unlocked() else VaultState.Locked
            VaultEvent.ScreenOff, VaultEvent.HomePressed, VaultEvent.Closed -> VaultState.Locked
            else -> state
        }
    }
}
