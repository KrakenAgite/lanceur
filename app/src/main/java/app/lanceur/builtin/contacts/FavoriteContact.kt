package app.lanceur.builtin.contacts

import androidx.compose.ui.graphics.ImageBitmap
import java.util.Locale

data class FavoriteContact(val lookupUri: String, val name: String, val phone: String?, val photo: ImageBitmap?) {
    val firstName: String get() = name.trim().substringBefore(' ').ifEmpty { "?" }

    val initial: String get() = name.trim().firstOrNull()?.toString()?.uppercase(Locale.FRENCH) ?: "?"
}

sealed interface FavoritesState {
    data object NoPermission : FavoritesState
    data class Loaded(val contacts: List<FavoriteContact>) : FavoritesState
}
