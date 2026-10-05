package app.lanceur.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lanceur.app
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PrefsRepoTest {
    @get:Rule val tmp = TemporaryFolder()

    private val chrome = app("Chrome")
    private val agenda = app("Agenda")

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(tmp.root, "test.preferences_pb") })

    @Test
    fun defaults_when_nothing_is_stored() = runTest {
        assertEquals(LauncherPrefs(), PrefsRepo(store()).prefs.first())
    }

    @Test
    fun favorites_keep_their_order_without_duplicates() = runTest {
        val repo = PrefsRepo(store())
        repo.addFavorite(chrome.key)
        repo.addFavorite(agenda.key)
        repo.addFavorite(chrome.key)
        assertEquals(listOf(chrome.key, agenda.key), repo.prefs.first().favorites)

        repo.setFavoritesOrder(listOf(agenda.key, chrome.key))
        assertEquals(listOf(agenda.key, chrome.key), repo.prefs.first().favorites)

        repo.removeFavorite(agenda.key)
        assertEquals(listOf(chrome.key), repo.prefs.first().favorites)
    }

    @Test
    fun hiding_removes_from_favorites_and_unhiding_does_not_restore_it() = runTest {
        val repo = PrefsRepo(store())
        repo.addFavorite(chrome.key)
        repo.hide(chrome.key)
        assertEquals(emptyList<Any>(), repo.prefs.first().favorites)
        assertEquals(setOf(chrome.key), repo.prefs.first().hidden)

        repo.unhide(chrome.key)
        assertEquals(emptySet<Any>(), repo.prefs.first().hidden)
        assertEquals(emptyList<Any>(), repo.prefs.first().favorites)
    }

    @Test
    fun side_and_permission_hint_are_saved() = runTest {
        val repo = PrefsRepo(store())
        repo.setAlphabetSide(AlphabetSide.LEFT)
        repo.dismissPermissionHint()
        assertEquals(LauncherPrefs(alphabetSide = AlphabetSide.LEFT, permissionHintDismissed = true), repo.prefs.first())
    }

    @Test
    fun malformed_stored_values_are_ignored() = runTest {
        val store = store()
        store.edit {
            it[stringPreferencesKey("favorites")] = "nimportequoi\n" + chrome.key.encode()
            it[stringPreferencesKey("alphabet_side")] = "HAUT"
        }
        val prefs = PrefsRepo(store).prefs.first()
        assertEquals(listOf(chrome.key), prefs.favorites)
        assertEquals(AlphabetSide.RIGHT, prefs.alphabetSide)
    }

    @Test
    fun prune_keeps_only_installed_apps() = runTest {
        val repo = PrefsRepo(store())
        repo.addFavorite(chrome.key)
        repo.addFavorite(agenda.key)
        repo.prune(listOf(chrome))
        assertEquals(listOf(chrome.key), repo.prefs.first().favorites)
    }

    @Test
    fun unhiding_a_renamed_activity_unhides_the_whole_package() = runTest {
        val repo = PrefsRepo(store())
        repo.hide(chrome.key)
        repo.unhide(chrome.key.copy(className = "app.chrome.Other"))
        assertEquals(emptySet<Any>(), repo.prefs.first().hidden)
    }
}
