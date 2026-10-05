package app.lanceur.prefs

import app.lanceur.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class VisibleAppsTest {
    private val agenda = app("Agenda")
    private val banque = app("Banque")
    private val chrome = app("Chrome")
    private val secret = app("Secret")
    private val coffre = app("Coffre", serial = 11, isPrivate = true)
    private val catalog = listOf(chrome, secret, agenda, coffre, banque)

    @Test
    fun visible_list_excludes_hidden_and_private_apps_and_is_sorted() {
        val lists = VisibleApps.compute(catalog, LauncherPrefs(hidden = setOf(secret.key)))
        assertEquals(listOf(agenda, banque, chrome), lists.allVisible)
        assertEquals(listOf(secret), lists.hiddenApps)
        assertEquals(listOf(coffre), lists.privateApps)
    }

    @Test
    fun favorites_keep_saved_order_and_skip_missing_or_hidden_apps() {
        val missing = app("Disparue")
        val prefs = LauncherPrefs(
            favorites = listOf(chrome.key, missing.key, secret.key, agenda.key),
            hidden = setOf(secret.key),
        )
        assertEquals(listOf(chrome, agenda), VisibleApps.compute(catalog, prefs).favorites)
    }

    @Test
    fun prune_removes_keys_of_uninstalled_apps() {
        val gone = app("Disparue")
        val prefs = LauncherPrefs(favorites = listOf(chrome.key, gone.key), hidden = setOf(secret.key, app("Partie").key))
        val pruned = VisibleApps.prune(catalog, prefs)
        assertEquals(listOf(chrome.key), pruned.favorites)
        assertEquals(setOf(secret.key), pruned.hidden)
    }

    @Test
    fun prune_ignores_an_empty_catalog() {
        val prefs = LauncherPrefs(favorites = listOf(chrome.key), hidden = setOf(secret.key))
        assertSame(prefs, VisibleApps.prune(emptyList(), prefs))
    }
}
