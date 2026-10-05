package app.lanceur.builtin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

/** Heure murale (ms), rafraîchie au début de chaque minute : horloges, compte à rebours. */
@Composable
fun rememberMinuteClock(): Long {
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(60_000 - System.currentTimeMillis() % 60_000)
            value = System.currentTimeMillis()
        }
    }
    return now
}
