package app.lanceur.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.launcherDataStore: DataStore<Preferences> by preferencesDataStore(name = "lanceur")

class PrefsRepo(private val store: DataStore<Preferences>) {
    val prefs: Flow<LauncherPrefs> = store.data.map { decode(it) }

    suspend fun addFavorite(key: AppKey) = update {
        if (key in it.favorites) it else it.copy(favorites = it.favorites + key)
    }

    suspend fun removeFavorite(key: AppKey) = update { it.copy(favorites = it.favorites - key) }

    suspend fun setFavoritesOrder(keys: List<AppKey>) = update { it.copy(favorites = keys.distinct()) }

    /** Cacher une appli la retire aussi des favoris. */
    suspend fun hide(key: AppKey) = update { it.copy(hidden = it.hidden + key, favorites = it.favorites - key) }

    /** Démasque tout le paquet dans ce profil (voir `VisibleApps.packageInProfile`). */
    suspend fun unhide(key: AppKey) = update { prefs ->
        with(VisibleApps) { prefs.copy(hidden = prefs.hidden.filterNotTo(LinkedHashSet()) { it.packageInProfile() == key.packageInProfile() }) }
    }

    suspend fun setAlphabetSide(side: AlphabetSide) = update { it.copy(alphabetSide = side) }

    suspend fun dismissPermissionHint() = update { it.copy(permissionHintDismissed = true) }

    suspend fun prune(catalog: List<AppEntry>) = update { VisibleApps.prune(catalog, it) }

    private suspend fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        store.edit { stored -> encode(transform(decode(stored)), stored) }
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val SIDE = stringPreferencesKey("alphabet_side")
        val HINT_DISMISSED = booleanPreferencesKey("permission_hint_dismissed")

        fun decode(stored: Preferences) = LauncherPrefs(
            favorites = stored[FAVORITES].orEmpty().split('\n').mapNotNull(AppKey::decode),
            hidden = stored[HIDDEN].orEmpty().mapNotNullTo(LinkedHashSet(), AppKey::decode),
            alphabetSide = AlphabetSide.entries.firstOrNull { it.name == stored[SIDE] } ?: AlphabetSide.RIGHT,
            permissionHintDismissed = stored[HINT_DISMISSED] ?: false,
        )

        fun encode(prefs: LauncherPrefs, out: MutablePreferences) {
            out[FAVORITES] = prefs.favorites.joinToString("\n") { it.encode() }
            out[HIDDEN] = prefs.hidden.mapTo(HashSet()) { it.encode() }
            out[SIDE] = prefs.alphabetSide.name
            out[HINT_DISMISSED] = prefs.permissionHintDismissed
        }
    }
}
