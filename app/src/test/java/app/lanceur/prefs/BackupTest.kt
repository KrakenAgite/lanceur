package app.lanceur.prefs

import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun store() = LauncherDataStore.create { File(tmp.newFolder(), "test.preferences_pb") }

    @Test
    fun every_kind_of_value_round_trips() {
        val values = mapOf("a" to "texte\navec ligne", "b" to true, "c" to setOf("x", "y"), "d" to 42, "e" to 7L, "f" to 1.5f, "g" to 2.25)
        val text = Backup.encode(values, createdAt = 123L)
        val back = Backup.decode(text)!!
        assertEquals(values, back.values)
        assertEquals(123L, back.createdAt)
    }

    @Test
    fun foreign_or_broken_files_are_refused() {
        assertNull(Backup.decode("{\"app\":\"autre\",\"version\":1,\"prefs\":{}}"))
        assertNull(Backup.decode("pas du json"))
        assertNull(Backup.decode("{\"app\":\"lanceur\",\"version\":99,\"prefs\":{}}"))
    }

    @Test
    fun file_names_sort_by_date() {
        assertEquals("lanceur-2026-10-06-143005.json", Backup.fileName(java.time.LocalDateTime.of(2026, 10, 6, 14, 30, 5)))
        assertTrue(Backup.isBackupName("lanceur-2026-10-06-143005.json"))
        assertTrue(!Backup.isBackupName("photo.jpg"))
    }

    @Test
    fun restore_replaces_the_settings_but_keeps_the_backup_settings() = runTest {
        val repo = PrefsRepo(store())
        repo.setNewsEnabled(true)
        repo.setAlphabetSide(AlphabetSide.LEFT)
        repo.setBackupFolder("content://dossier")
        val snapshot = repo.snapshot()
        assertTrue(snapshot.keys.none { it.startsWith("backup_") || it.startsWith("update_") })

        repo.setNewsEnabled(false)
        repo.setAlphabetSide(AlphabetSide.RIGHT)
        repo.setFavoritesOrder(listOf(app.lanceur.apps.AppKey("x", "y", 0L)))
        repo.restore(snapshot)
        val prefs = repo.prefs.first()
        assertTrue(prefs.newsEnabled)
        assertEquals(AlphabetSide.LEFT, prefs.alphabetSide)
        assertEquals(emptyList<app.lanceur.apps.AppKey>(), prefs.favorites)
        assertEquals("content://dossier", prefs.backup.folder)
    }
}
