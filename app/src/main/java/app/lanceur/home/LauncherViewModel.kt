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

    private val _screen = MutableStateFlow(Screen.HOME)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _listMode = MutableStateFlow<ListMode>(ListMode.Favorites)
    val listMode: StateFlow<ListMode> = _listMode.asStateFlow()

    private val _vault = MutableStateFlow<VaultState>(VaultState.Locked)
    val vault: StateFlow<VaultState> = _vault.asStateFlow()

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

    /** Bouton Accueil : retour aux favoris, recherche fermée, dossier reverrouillé. */
    fun goHome() {
        vaultEvent(VaultEvent.HomePressed)
        _screen.value = Screen.HOME
        _listMode.value = ListMode.Favorites
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
