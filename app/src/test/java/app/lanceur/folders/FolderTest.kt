package app.lanceur.folders

import app.lanceur.app
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FolderTest {
    private val chrome = app("Chrome").key
    private val agenda = app("Agenda").key

    @Test
    fun folders_survive_encoding() {
        val folders = listOf(
            Folder(1, "Travail", FolderIcon.WORK, listOf(chrome, agenda)),
            Folder(3, "Vide", FolderIcon.GAMES),
        )
        assertEquals(folders, Folder.decodeAll(Folder.encodeAll(folders)))
    }

    @Test
    fun separators_in_a_name_become_spaces() {
        val folder = Folder(1, Folder.clean("  Jeux\tet\nfilms "), FolderIcon.MOVIE, listOf(chrome))
        assertEquals("Jeux et films", folder.name)
        assertEquals(listOf(folder), Folder.decodeAll(folder.encode()))
    }

    @Test
    fun unknown_icon_falls_back_and_broken_lines_are_skipped() {
        val decoded = Folder.decodeAll("2\tDISPARUE\tPerso\nnimporte quoi\n\n2\tWORK\tDoublon")
        assertEquals(listOf(Folder(2, "Perso", FolderIcon.FOLDER)), decoded)
        assertNull(Folder.decode("x\tWORK\tNom"))
    }

    @Test
    fun next_id_follows_the_largest() {
        assertEquals(1, Folder.nextId(emptyList()))
        assertEquals(6, Folder.nextId(listOf(Folder(5, "a", FolderIcon.STAR), Folder(2, "b", FolderIcon.STAR))))
    }
}
