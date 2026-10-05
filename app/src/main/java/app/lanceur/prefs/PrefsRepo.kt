package app.lanceur.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

class PrefsRepo(
    private val store: DataStore<Preferences>,
    private val onError: (Throwable) -> Unit = {},
) {
    /** Fichier illisible : réglages par défaut, sans planter. */
    val prefs: Flow<LauncherPrefs> = store.data
        .catch { e -> if (e is IOException) { onError(e); emit(emptyPreferences()) } else throw e }
        .map { decode(it) }

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

    suspend fun addWidget(slot: WidgetSlot) = update { prefs ->
        if (prefs.widgets.any { it.appWidgetId == slot.appWidgetId }) prefs else prefs.copy(widgets = prefs.widgets + slot)
    }

    suspend fun removeWidget(appWidgetId: Int) = update { prefs ->
        prefs.copy(widgets = prefs.widgets.filterNot { it.appWidgetId == appWidgetId })
    }

    suspend fun setWidgetSize(appWidgetId: Int, size: WidgetSize) = update { prefs ->
        prefs.copy(widgets = prefs.widgets.map { if (it.appWidgetId == appWidgetId) it.copy(size = size) else it })
    }

    /** Réordonne selon `ids` ; un widget absent de `ids` reste à la fin, dans son ordre. */
    suspend fun setWidgetsOrder(ids: List<Int>) = update { prefs ->
        val byId = prefs.widgets.associateBy { it.appWidgetId }
        val ordered = ids.distinct().mapNotNull { byId[it] }
        prefs.copy(widgets = ordered + prefs.widgets.filter { it.appWidgetId !in ids })
    }

    suspend fun setWidgetPageEnabled(enabled: Boolean) = update { it.copy(widgetPageEnabled = enabled) }

    suspend fun prune(catalog: List<AppEntry>) = update { VisibleApps.prune(catalog, it) }

    /** Une écriture impossible est signalée, pas propagée : le changement est simplement perdu. */
    private suspend fun update(transform: (LauncherPrefs) -> LauncherPrefs) {
        try {
            store.edit { stored -> encode(transform(decode(stored)), stored) }
        } catch (e: IOException) {
            onError(e)
        }
    }

    private companion object {
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val SIDE = stringPreferencesKey("alphabet_side")
        val HINT_DISMISSED = booleanPreferencesKey("permission_hint_dismissed")
        val WIDGETS = stringPreferencesKey("widgets")
        val WIDGET_PAGE = booleanPreferencesKey("widget_page_enabled")

        fun decode(stored: Preferences) = LauncherPrefs(
            favorites = stored[FAVORITES].orEmpty().split('\n').mapNotNull(AppKey::decode),
            hidden = stored[HIDDEN].orEmpty().mapNotNullTo(LinkedHashSet(), AppKey::decode),
            alphabetSide = AlphabetSide.entries.firstOrNull { it.name == stored[SIDE] } ?: AlphabetSide.RIGHT,
            permissionHintDismissed = stored[HINT_DISMISSED] ?: false,
            widgetPageEnabled = stored[WIDGET_PAGE] ?: true,
            widgets = stored[WIDGETS].orEmpty().split('\n').mapNotNull(WidgetSlot::decode).distinctBy { it.appWidgetId },
        )

        fun encode(prefs: LauncherPrefs, out: MutablePreferences) {
            out[FAVORITES] = prefs.favorites.joinToString("\n") { it.encode() }
            out[HIDDEN] = prefs.hidden.mapTo(HashSet()) { it.encode() }
            out[SIDE] = prefs.alphabetSide.name
            out[HINT_DISMISSED] = prefs.permissionHintDismissed
            out[WIDGET_PAGE] = prefs.widgetPageEnabled
            out[WIDGETS] = prefs.widgets.joinToString("\n") { it.encode() }
        }
    }
}
