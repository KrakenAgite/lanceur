package app.lanceur.builtin.media

import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Applis (paquet, série du profil) qui ont au moins une notification à pastille. Tenu à jour par l'accès aux
 * notifications ([MediaListener]) ; seuls comptent l'appli et le profil, jamais le contenu.
 */
object NotificationBadges {
    private val state = MutableStateFlow<Set<Pair<String, Long>>>(emptySet())
    val apps: StateFlow<Set<Pair<String, Long>>> = state.asStateFlow()

    internal fun set(apps: Set<Pair<String, Long>>) {
        state.value = apps
    }
}

/** Applis à pastille affichées par `AppRow` ; vide si l'option est coupée. */
val LocalBadges = compositionLocalOf { emptySet<Pair<String, Long>>() }
