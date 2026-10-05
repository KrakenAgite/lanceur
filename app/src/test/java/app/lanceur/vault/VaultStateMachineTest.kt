package app.lanceur.vault

import app.lanceur.vault.VaultEvent.AuthFailed
import app.lanceur.vault.VaultEvent.AuthSucceeded
import app.lanceur.vault.VaultEvent.Backgrounded
import app.lanceur.vault.VaultEvent.Closed
import app.lanceur.vault.VaultEvent.ExternalPromptStarted
import app.lanceur.vault.VaultEvent.HomePressed
import app.lanceur.vault.VaultEvent.OpenRequested
import app.lanceur.vault.VaultEvent.ScreenOff
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultStateMachineTest {
    private fun run(vararg events: VaultEvent, from: VaultState = VaultState.Locked) =
        events.fold(from, VaultStateMachine::reduce)

    @Test
    fun open_then_success_unlocks() {
        assertEquals(VaultState.Authenticating, run(OpenRequested))
        assertEquals(VaultState.Unlocked(), run(OpenRequested, AuthSucceeded))
    }

    @Test
    fun failure_or_cancel_relocks() {
        assertEquals(VaultState.Locked, run(OpenRequested, AuthFailed))
    }

    @Test
    fun backgrounded_while_authenticating_keeps_waiting() {
        // Le code du téléphone (repli) est une autre activité : le launcher passe en onStop.
        assertEquals(VaultState.Authenticating, run(OpenRequested, Backgrounded))
        assertEquals(VaultState.Unlocked(), run(OpenRequested, Backgrounded, AuthSucceeded))
    }

    @Test
    fun late_success_after_cancel_is_ignored() {
        assertEquals(VaultState.Locked, run(OpenRequested, HomePressed, AuthSucceeded))
    }

    @Test
    fun open_requested_twice_stays_authenticating() {
        assertEquals(VaultState.Authenticating, run(OpenRequested, OpenRequested))
    }

    @Test
    fun unlocked_vault_relocks_when_leaving() {
        listOf(Backgrounded, ScreenOff, HomePressed, Closed).forEach {
            assertEquals(it.name, VaultState.Locked, run(it, from = VaultState.Unlocked()))
        }
    }

    @Test
    fun external_prompt_grants_one_background() {
        val afterPrompt = run(ExternalPromptStarted, from = VaultState.Unlocked())
        assertEquals(VaultState.Unlocked(keepOnNextBackground = true), afterPrompt)
        assertEquals(VaultState.Unlocked(), run(Backgrounded, from = afterPrompt))
        assertEquals(VaultState.Locked, run(Backgrounded, Backgrounded, from = afterPrompt))
    }

    @Test
    fun external_prompt_does_not_survive_screen_off_or_home() {
        val afterPrompt = VaultState.Unlocked(keepOnNextBackground = true)
        assertEquals(VaultState.Locked, run(ScreenOff, from = afterPrompt))
        assertEquals(VaultState.Locked, run(HomePressed, from = afterPrompt))
    }
}
