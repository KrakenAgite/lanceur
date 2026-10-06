package app.lanceur.prefs

import app.lanceur.apps.icons.IconShape
import app.lanceur.focus.FocusMode
import app.lanceur.ui.AppLabelStyle
import app.lanceur.apps.icons.IconStyle
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
import app.lanceur.builtin.BuiltinKind
import app.lanceur.builtin.BuiltinSlots
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class PrefsRepo(
    private val store: DataStore<Preferences>,
    private val onError: (Throwable) -> Unit = {},
) {
    /** Fichier illisible : réglages par défaut, sans planter. */
    val prefs: Flow<LauncherPrefs> = store.data
        .catch { e -> if (e is IOException) { onError(e); emit(emptyPreferences()) } else throw e }
        .map { decode(it) }

    /** Réglages actuels, ou `null` si le fichier est illisible (et non des valeurs par défaut). */
    suspend fun readOrNull(): LauncherPrefs? = try {
        decode(store.data.first())
    } catch (e: IOException) {
        onError(e)
        null
    }

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

    /** L'identifiant est choisi dans la même transaction que l'ajout : deux ajouts rapides n'ont jamais le même. */
    suspend fun addBuiltinWidget(kind: BuiltinKind, data: String? = null) = update { prefs ->
        val slot = BuiltinSlots.create(kind, prefs.widgets)
        prefs.copy(
            widgets = prefs.widgets + slot,
            widgetData = if (data == null) prefs.widgetData else prefs.widgetData + (slot.appWidgetId to data),
        )
    }

    suspend fun setWidgetData(appWidgetId: Int, data: String) = update { it.copy(widgetData = it.widgetData + (appWidgetId to data)) }

    suspend fun removeWidget(appWidgetId: Int) = update { prefs ->
        prefs.copy(widgets = prefs.widgets.filterNot { it.appWidgetId == appWidgetId }, widgetData = prefs.widgetData - appWidgetId)
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

    suspend fun setNewsEnabled(enabled: Boolean) = update { it.copy(newsEnabled = enabled) }

    suspend fun setPageOrder(order: List<PageKind>) = update { it.copy(pageOrder = PageLayout.normalize(order)) }

    /** Transformation dans la transaction : une actualisation et un ajout de flux ne s'écrasent pas. */
    suspend fun updateNews(transform: (String?) -> String) = update { it.copy(news = transform(it.news)) }

    /** Transformation dans la transaction : démarrer, arrêter et cocher ne s'écrasent pas. */
    suspend fun updateFocus(transform: (FocusMode) -> FocusMode) = update { it.copy(focus = transform(it.focus)) }

    suspend fun setAppLabelStyle(style: AppLabelStyle) = update { it.copy(appLabelStyle = style) }

    suspend fun setIconStyle(style: IconStyle) = update { it.copy(iconStyle = style) }

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
        const val DATA_PREFIX = "widget_data_"
        val FAVORITES = stringPreferencesKey("favorites")
        val HIDDEN = stringSetPreferencesKey("hidden")
        val SIDE = stringPreferencesKey("alphabet_side")
        val HINT_DISMISSED = booleanPreferencesKey("permission_hint_dismissed")
        val WIDGETS = stringPreferencesKey("widgets")
        val WIDGET_PAGE = booleanPreferencesKey("widget_page_enabled")
        val NEWS_ENABLED = booleanPreferencesKey("news_enabled")
        val PAGE_ORDER = stringPreferencesKey("page_order")
        val NEWS = stringPreferencesKey("news")
        val ICON_PACK = stringPreferencesKey("icon_pack")
        val ICON_SHAPE = stringPreferencesKey("icon_shape")
        val ICON_THEMED = booleanPreferencesKey("icon_themed")
        val SHOW_ICONS = booleanPreferencesKey("show_icons")
        val FOCUS = stringPreferencesKey("focus")
        val LABEL_UPPERCASE = booleanPreferencesKey("label_uppercase")

        fun decode(stored: Preferences) = LauncherPrefs(
            favorites = stored[FAVORITES].orEmpty().split('\n').mapNotNull(AppKey::decode),
            hidden = stored[HIDDEN].orEmpty().mapNotNullTo(LinkedHashSet(), AppKey::decode),
            alphabetSide = AlphabetSide.entries.firstOrNull { it.name == stored[SIDE] } ?: AlphabetSide.RIGHT,
            permissionHintDismissed = stored[HINT_DISMISSED] ?: false,
            widgetPageEnabled = stored[WIDGET_PAGE] ?: true,
            newsEnabled = stored[NEWS_ENABLED] ?: false,
            pageOrder = PageLayout.decode(stored[PAGE_ORDER]),
            news = stored[NEWS],
            focus = FocusMode.decode(stored[FOCUS]),
            appLabelStyle = AppLabelStyle(stored[SHOW_ICONS] ?: true, stored[LABEL_UPPERCASE] ?: false),
            iconStyle = IconStyle(stored[ICON_PACK], IconShape.decode(stored[ICON_SHAPE]), stored[ICON_THEMED] ?: false),
            widgets = stored[WIDGETS].orEmpty().split('\n').mapNotNull(WidgetSlot::decode).distinctBy { it.appWidgetId },
            widgetData = stored.asMap().mapNotNull { (key, value) ->
                val id = key.name.takeIf { it.startsWith(DATA_PREFIX) }?.removePrefix(DATA_PREFIX)?.toIntOrNull()
                if (id != null && value is String) id to value else null
            }.toMap(),
        )

        fun encode(prefs: LauncherPrefs, out: MutablePreferences) {
            out[FAVORITES] = prefs.favorites.joinToString("\n") { it.encode() }
            out[HIDDEN] = prefs.hidden.mapTo(HashSet()) { it.encode() }
            out[SIDE] = prefs.alphabetSide.name
            out[HINT_DISMISSED] = prefs.permissionHintDismissed
            out[WIDGET_PAGE] = prefs.widgetPageEnabled
            out[NEWS_ENABLED] = prefs.newsEnabled
            out[PAGE_ORDER] = PageLayout.encode(prefs.pageOrder)
            if (prefs.news != null) out[NEWS] = prefs.news else out.remove(NEWS)
            prefs.iconStyle.pack?.let { out[ICON_PACK] = it } ?: out.remove(ICON_PACK)
            out[ICON_SHAPE] = prefs.iconStyle.shape.name
            out[ICON_THEMED] = prefs.iconStyle.themed
            out[SHOW_ICONS] = prefs.appLabelStyle.showIcons
            out[FOCUS] = prefs.focus.encode()
            out[LABEL_UPPERCASE] = prefs.appLabelStyle.uppercase
            out[WIDGETS] = prefs.widgets.joinToString("\n") { it.encode() }
            // Les données d'un widget retiré disparaissent avec lui
            out.asMap().keys.filter { it.name.startsWith(DATA_PREFIX) }.toList().forEach { out.remove(it) }
            prefs.widgetData.forEach { (id, text) -> out[stringPreferencesKey(DATA_PREFIX + id)] = text }
        }
    }
}
