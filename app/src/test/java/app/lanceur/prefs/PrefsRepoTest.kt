package app.lanceur.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.lanceur.app
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import app.lanceur.apps.AppKey
import app.lanceur.widgets.WidgetSize
import app.lanceur.widgets.WidgetSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun a_corrupted_file_starts_over_instead_of_crashing() = runTest {
        val file = File(tmp.root, "corrompu.preferences_pb").apply { writeBytes(byteArrayOf(0x7f, 0x13, 0x00, 0x42, 0x99.toByte())) }
        val repo = PrefsRepo(LauncherDataStore.create(backgroundScope) { file })
        assertEquals(LauncherPrefs(), repo.prefs.first())
        repo.addFavorite(chrome.key)
        assertEquals(listOf(chrome.key), repo.prefs.first().favorites)
    }

    @Test
    fun an_unreadable_file_gives_defaults_and_writes_do_not_throw() = runTest {
        val errors = mutableListOf<Throwable>()
        val folder = File(tmp.root, "dossier.preferences_pb").apply { mkdir() }
        val repo = PrefsRepo(LauncherDataStore.create(backgroundScope) { folder }, onError = { errors += it })
        assertEquals(LauncherPrefs(), repo.prefs.first())
        repo.addFavorite(chrome.key)
        assertTrue(errors.isNotEmpty())
    }

    private fun slot(id: Int, size: WidgetSize = WidgetSize.MEDIUM) =
        WidgetSlot(id, AppKey("app.meteo", "app.meteo.Widget$id", 0), size)

    @Test
    fun widgets_keep_their_order_and_size_and_can_be_removed() = runTest {
        val repo = PrefsRepo(store())
        repo.addWidget(slot(1))
        repo.addWidget(slot(2, WidgetSize.SMALL))
        repo.addWidget(slot(1))
        assertEquals(listOf(slot(1), slot(2, WidgetSize.SMALL)), repo.prefs.first().widgets)

        repo.setWidgetSize(1, WidgetSize.LARGE)
        repo.setWidgetsOrder(listOf(2, 1))
        assertEquals(listOf(slot(2, WidgetSize.SMALL), slot(1, WidgetSize.LARGE)), repo.prefs.first().widgets)

        repo.removeWidget(2)
        assertEquals(listOf(slot(1, WidgetSize.LARGE)), repo.prefs.first().widgets)
    }

    @Test
    fun widget_page_is_enabled_by_default_and_can_be_disabled() = runTest {
        val repo = PrefsRepo(store())
        assertTrue(repo.prefs.first().widgetPageEnabled)
        repo.setWidgetPageEnabled(false)
        assertFalse(repo.prefs.first().widgetPageEnabled)
    }

    @Test
    fun malformed_or_duplicated_widget_lines_are_ignored() = runTest {
        val store = store()
        store.edit { it[stringPreferencesKey("widgets")] = "n'importe quoi\n" + slot(3).encode() + "\n" + slot(3).encode() }
        assertEquals(listOf(slot(3)), PrefsRepo(store).prefs.first().widgets)
    }

    @Test
    fun read_or_null_reports_an_unreadable_file_instead_of_defaults() = runTest {
        val folder = File(tmp.root, "illisible.preferences_pb").apply { mkdir() }
        assertEquals(null, PrefsRepo(LauncherDataStore.create(backgroundScope) { folder }).readOrNull())
    }

    @Test
    fun read_or_null_returns_the_saved_prefs() = runTest {
        val repo = PrefsRepo(store())
        repo.addWidget(slot(4))
        assertEquals(listOf(slot(4)), repo.readOrNull()?.widgets)
    }

    @Test
    fun builtin_widgets_get_decreasing_negative_ids_after_android_ones() = runTest {
        val repo = PrefsRepo(store())
        repo.addWidget(slot(4))
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR)
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.NOW_PLAYING)
        val widgets = repo.prefs.first().widgets
        assertEquals(listOf(4, -1, -2), widgets.map { it.appWidgetId })
        assertEquals(WidgetSize.MEDIUM, widgets.last().size)
    }

    @Test
    fun widget_data_is_kept_per_widget_and_removed_with_it() = runTest {
        val repo = PrefsRepo(store())
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR, data = "a=1")
        repo.addBuiltinWidget(app.lanceur.builtin.BuiltinKind.BATTERY_BAR)
        repo.setWidgetData(-2, "b=2")
        assertEquals(mapOf(-1 to "a=1", -2 to "b=2"), repo.prefs.first().widgetData)
        repo.removeWidget(-1)
        assertEquals(mapOf(-2 to "b=2"), repo.prefs.first().widgetData)
        assertEquals(listOf(-2), repo.prefs.first().widgets.map { it.appWidgetId })
    }

    @Test
    fun icon_style_is_saved_and_the_pack_can_be_cleared() = runTest {
        val repo = PrefsRepo(store())
        assertEquals(app.lanceur.apps.icons.IconStyle(), repo.prefs.first().iconStyle)
        val style = app.lanceur.apps.icons.IconStyle(pack = "com.pack", shape = app.lanceur.apps.icons.IconShape.CIRCLE, themed = true)
        repo.setIconStyle(style)
        assertEquals(style, repo.prefs.first().iconStyle)
        repo.setIconStyle(style.copy(pack = null))
        assertEquals(null, repo.prefs.first().iconStyle.pack)
    }

    @Test
    fun app_label_style_defaults_to_icons_and_normal_case_and_is_saved() = runTest {
        val repo = PrefsRepo(store())
        assertEquals(app.lanceur.ui.AppLabelStyle(), repo.prefs.first().appLabelStyle)
        val style = app.lanceur.ui.AppLabelStyle(showIcons = false, uppercase = true)
        repo.setAppLabelStyle(style)
        assertEquals(style, repo.prefs.first().appLabelStyle)
    }

    @Test
    fun pages_default_and_migration() = runTest {
        val repo = PrefsRepo(store())
        val initial = repo.prefs.first()
        assertEquals(app.lanceur.home.PageLayout.DEFAULT_ORDER, initial.pageOrder)
        assertFalse(initial.newsEnabled)
        repo.setWidgetPageEnabled(false)
        repo.setNewsEnabled(true)
        repo.setPageOrder(listOf(app.lanceur.home.PageKind.WIDGETS, app.lanceur.home.PageKind.HOME, app.lanceur.home.PageKind.NEWS))
        val prefs = repo.prefs.first()
        assertFalse(prefs.widgetPageEnabled)
        assertTrue(prefs.newsEnabled)
        assertEquals(listOf(app.lanceur.home.PageKind.WIDGETS, app.lanceur.home.PageKind.HOME, app.lanceur.home.PageKind.NEWS), prefs.pageOrder)
        repo.updateNews { (it ?: "") + "x" }
        repo.updateNews { (it ?: "") + "y" }
        assertEquals("xy", repo.prefs.first().news)
    }

    @Test
    fun folders_are_created_filled_edited_and_deleted() = runTest {
        val repo = PrefsRepo(store())
        repo.createFolder("Travail", app.lanceur.folders.FolderIcon.WORK, chrome.key)
        repo.createFolder("Jeux", app.lanceur.folders.FolderIcon.GAMES)
        val (work, games) = repo.prefs.first().folders
        assertEquals(listOf(chrome.key), work.apps)
        assertEquals(work.id + 1, games.id)

        repo.setInFolder(games.id, agenda.key, true)
        repo.setInFolder(games.id, agenda.key, true)
        repo.setInFolder(work.id, chrome.key, false)
        repo.editFolder(games.id, "Loisirs", app.lanceur.folders.FolderIcon.MOVIE)
        val after = repo.prefs.first().folders
        assertEquals(emptyList<Any>(), after[0].apps)
        assertEquals(app.lanceur.folders.Folder(games.id, "Loisirs", app.lanceur.folders.FolderIcon.MOVIE, listOf(agenda.key)), after[1])

        repo.deleteFolder(work.id)
        assertEquals(listOf(games.id), repo.prefs.first().folders.map { it.id })
    }

    @Test
    fun labels_pauses_clock_and_badges_are_saved() = runTest {
        val repo = PrefsRepo(store())
        repo.setLabel(chrome.key, "  Web\tperso ")
        repo.setLabel(agenda.key, "Planning")
        repo.setLabel(agenda.key, " ")
        repo.setPaused(chrome.key, true)
        repo.setClockStyle(app.lanceur.home.ClockStyle(app.lanceur.home.ClockFont.MONO, stacked = true))
        repo.setBadges(false)
        val prefs = repo.prefs.first()
        assertEquals(mapOf(chrome.key to "Web perso"), prefs.labels)
        assertEquals(setOf(chrome.key), prefs.paused)
        assertEquals(app.lanceur.home.ClockStyle(app.lanceur.home.ClockFont.MONO, stacked = true), prefs.clock)
        assertFalse(prefs.badges)

        repo.setLabel(chrome.key, null)
        repo.setPaused(chrome.key, false)
        assertEquals(emptyMap<Any, Any>(), repo.prefs.first().labels)
        assertEquals(emptySet<Any>(), repo.prefs.first().paused)
    }

    @Test
    fun folder_order_follows_the_given_keys_and_keeps_the_others_at_the_end() = runTest {
        val repo = PrefsRepo(store())
        val banque = app("Banque")
        repo.createFolder("Perso", app.lanceur.folders.FolderIcon.HOME, chrome.key)
        val id = repo.prefs.first().folders.single().id
        repo.setInFolder(id, agenda.key, true)
        repo.setInFolder(id, banque.key, true)
        // Banque, cachée par exemple, n'est pas dans la liste réordonnée : elle reste, à la fin
        repo.setFolderOrder(id, listOf(agenda.key, chrome.key, app("Inconnue").key))
        assertEquals(listOf(agenda.key, chrome.key, banque.key), repo.prefs.first().folders.single().apps)
    }
}
