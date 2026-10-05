package app.lanceur.home

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.lanceur.MainDispatcherRule
import app.lanceur.app
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import app.lanceur.prefs.PrefsRepo
import app.lanceur.vault.VaultEvent
import app.lanceur.vault.VaultState
import java.io.File
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class LauncherViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val chrome = app("Chrome")
    private val gone = app("Disparue")
    private val secret = app("Secret")

    private fun TestScope.repo() = PrefsRepo(
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "vm.preferences_pb") }),
    )

    private fun TestScope.viewModel(apps: List<AppEntry> = emptyList()) =
        LauncherViewModel(MutableStateFlow<List<AppEntry>?>(apps), repo())

    @Test
    fun prune_waits_for_the_catalog() = runTest(main.dispatcher) {
        val repo = repo()
        repo.addFavorite(chrome.key)
        repo.addFavorite(gone.key)
        val catalog = MutableStateFlow<List<AppEntry>?>(null)
        LauncherViewModel(catalog, repo)
        advanceUntilIdle()
        assertEquals(listOf(chrome.key, gone.key), repo.prefs.first().favorites)

        catalog.value = listOf(chrome)
        repo.prefs.first { it.favorites == listOf(chrome.key) }
    }

    @Test
    fun hidden_apps_leave_the_visible_list() = runTest(main.dispatcher) {
        val vm = viewModel(listOf(chrome, secret))
        vm.hide(secret.key)
        vm.lists.first { it.allVisible == listOf(chrome) && it.hiddenApps == listOf(secret) }
    }

    @Test
    fun successful_auth_opens_the_vault_and_home_locks_it() = runTest(main.dispatcher) {
        val vm = viewModel()
        assertTrue(vm.requestVault())
        assertFalse(vm.requestVault())
        vm.vaultEvent(VaultEvent.AuthSucceeded)
        assertEquals(Screen.VAULT, vm.screen.value)

        vm.setListMode(ListMode.Letter('B'))
        vm.goHome()
        assertEquals(VaultState.Locked, vm.vault.value)
        assertEquals(Screen.HOME, vm.screen.value)
        assertEquals(ListMode.Favorites, vm.listMode.value)
    }

    @Test
    fun going_to_background_closes_the_open_vault() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.requestVault()
        vm.vaultEvent(VaultEvent.AuthSucceeded)
        vm.vaultEvent(VaultEvent.Backgrounded)
        assertEquals(VaultState.Locked, vm.vault.value)
        assertEquals(Screen.HOME, vm.screen.value)
    }

    @Test
    fun back_closes_screens_then_letter_mode() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.show(Screen.SEARCH)
        vm.back()
        assertEquals(Screen.HOME, vm.screen.value)

        vm.setListMode(ListMode.Letter('C'))
        vm.back()
        assertEquals(ListMode.Favorites, vm.listMode.value)
    }

    @Test
    fun not_loaded_until_the_catalog_and_prefs_arrive() = runTest(main.dispatcher) {
        val catalog = MutableStateFlow<List<AppEntry>?>(null)
        val vm = LauncherViewModel(catalog, repo())
        advanceUntilIdle()
        assertFalse(vm.loaded.value)

        catalog.value = listOf(chrome)
        vm.loaded.first { it }
    }

    @Test
    fun going_home_leaves_widget_edit_mode_and_asks_for_the_home_page() = runTest(main.dispatcher) {
        val vm = viewModel()
        vm.setWidgetEditMode(true)
        val before = vm.homePageRequests.value
        vm.goHome()
        assertFalse(vm.widgetEditMode.value)
        assertEquals(before + 1, vm.homePageRequests.value)
    }

    @Test
    fun widget_changes_reach_the_prefs() = runTest(main.dispatcher) {
        val vm = viewModel()
        val slot = WidgetSlot(5, AppKey("app.meteo", "app.meteo.Widget", 0), WidgetSize.SMALL)
        vm.addWidget(slot)
        vm.prefs.first { it.widgets == listOf(slot) }
        vm.setWidgetSize(5, WidgetSize.LARGE)
        vm.prefs.first { it.widgets.singleOrNull()?.size == WidgetSize.LARGE }
        vm.setWidgetPageEnabled(false)
        vm.prefs.first { !it.widgetPageEnabled }
        vm.removeWidget(5)
        vm.prefs.first { it.widgets.isEmpty() }
    }
}
