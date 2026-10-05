package app.lanceur.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.lanceur.AppContainer
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.AppLists
import app.lanceur.prefs.LauncherPrefs
import app.lanceur.prefs.PrefsRepo
import app.lanceur.prefs.VisibleApps
import app.lanceur.vault.VaultEvent
import app.lanceur.vault.VaultState
import app.lanceur.vault.VaultStateMachine
import app.lanceur.widgets.WidgetAddFlow
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LauncherViewModel(
    catalogApps: StateFlow<List<AppEntry>?>,
    private val prefsRepo: PrefsRepo,
) : ViewModel() {
    val prefs: StateFlow<LauncherPrefs> = prefsRepo.prefs.stateIn(viewModelScope, SharingStarted.Eagerly, LauncherPrefs())

    val lists: StateFlow<AppLists> = combine(catalogApps.filterNotNull(), prefsRepo.prefs) { apps, prefs ->
        VisibleApps.compute(apps, prefs)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, AppLists.EMPTY)

    /** Vrai une fois le catalogue et les réglages lus : avant, l'accueil afficherait des valeurs par défaut. */
    val loaded: StateFlow<Boolean> = combine(catalogApps, prefsRepo.prefs) { apps, _ -> apps != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _listMode = MutableStateFlow<ListMode>(ListMode.Favorites)
    val listMode: StateFlow<ListMode> = _listMode.asStateFlow()

    private val _vault = MutableStateFlow<VaultState>(VaultState.Locked)
    val vault: StateFlow<VaultState> = _vault.asStateFlow()

    private val _widgetEditMode = MutableStateFlow(false)
    val widgetEditMode: StateFlow<Boolean> = _widgetEditMode.asStateFlow()

    private val _homePageRequests = MutableStateFlow(0)

    /** Incrémenté par `goHome()` : l'écran ramène alors le défilement sur la page d'accueil. */
    val homePageRequests: StateFlow<Int> = _homePageRequests.asStateFlow()

    /** Ajout de widget en cours ; vit dans le ViewModel pour survivre à une recréation de l'activité. */
    var widgetAddState: WidgetAddFlow.State = WidgetAddFlow.State.Idle

    init {
        // Nettoyage seulement une fois le catalogue chargé (jamais sur la valeur initiale null)
        viewModelScope.launch { catalogApps.filterNotNull().collect { prefsRepo.prune(it) } }
    }

    fun show(screen: Screen) {
        _screen.value = screen
    }

    fun setListMode(mode: ListMode) {
        _listMode.value = mode
    }

    fun back() {
        when (_screen.value) {
            Screen.HOME -> _listMode.value = ListMode.Favorites
            Screen.VAULT -> vaultEvent(VaultEvent.Closed)
            else -> _screen.value = Screen.HOME
        }
    }

    /** Bouton Accueil : retour aux favoris, recherche fermée, dossier reverrouillé, page d'accueil. */
    fun goHome() {
        vaultEvent(VaultEvent.HomePressed)
        _screen.value = Screen.HOME
        _listMode.value = ListMode.Favorites
        _widgetEditMode.value = false
        _homePageRequests.value++
    }

    /** `true` si l'écran doit lancer l'authentification (le dossier était verrouillé). */
    fun requestVault(): Boolean {
        val before = _vault.value
        vaultEvent(VaultEvent.OpenRequested)
        return before == VaultState.Locked && _vault.value == VaultState.Authenticating
    }

    fun vaultEvent(event: VaultEvent) {
        val next = VaultStateMachine.reduce(_vault.value, event)
        _vault.value = next
        if (event == VaultEvent.AuthSucceeded && next is VaultState.Unlocked) _screen.value = Screen.VAULT
        if (next !is VaultState.Unlocked && _screen.value == Screen.VAULT) _screen.value = Screen.HOME
    }

    fun addFavorite(key: AppKey) {
        viewModelScope.launch { prefsRepo.addFavorite(key) }
    }

    fun removeFavorite(key: AppKey) {
        viewModelScope.launch { prefsRepo.removeFavorite(key) }
    }

    fun hide(key: AppKey) {
        viewModelScope.launch { prefsRepo.hide(key) }
    }

    fun unhide(key: AppKey) {
        viewModelScope.launch { prefsRepo.unhide(key) }
    }

    fun setFavoritesOrder(keys: List<AppKey>) {
        viewModelScope.launch { prefsRepo.setFavoritesOrder(keys) }
    }

    fun setAlphabetSide(side: AlphabetSide) {
        viewModelScope.launch { prefsRepo.setAlphabetSide(side) }
    }

    fun setWidgetEditMode(on: Boolean) {
        _widgetEditMode.value = on
    }

    fun addWidget(slot: WidgetSlot) {
        viewModelScope.launch { prefsRepo.addWidget(slot) }
    }

    fun removeWidget(appWidgetId: Int) {
        viewModelScope.launch { prefsRepo.removeWidget(appWidgetId) }
    }

    fun setWidgetSize(appWidgetId: Int, size: WidgetSize) {
        viewModelScope.launch { prefsRepo.setWidgetSize(appWidgetId, size) }
    }

    fun setWidgetsOrder(ids: List<Int>) {
        viewModelScope.launch { prefsRepo.setWidgetsOrder(ids) }
    }

    fun setWidgetPageEnabled(enabled: Boolean) {
        viewModelScope.launch { prefsRepo.setWidgetPageEnabled(enabled) }
    }

    /** `onDone` est appelé une fois la préférence visible dans `prefs`. */
    fun dismissPermissionHint(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            prefsRepo.dismissPermissionHint()
            prefs.first { it.permissionHintDismissed }
            onDone()
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { LauncherViewModel(container.catalog.apps, container.prefsRepo) }
        }
    }
}
