package app.lanceur.apps

import app.lanceur.app
import org.junit.Assert.assertEquals
import org.junit.Test

class CatalogBuilderTest {
    private val chrome = app("Chrome")
    private val coffre = app("Coffre", serial = 11)
    private val mail = app("Mail pro", serial = 10)

    private fun profile(kind: ProfileKind, vararg apps: AppEntry, quiet: Boolean = false) =
        ProfileSnapshot(kind, quiet, apps.toList())

    @Test
    fun unlocked_private_space_apps_are_marked_private() {
        val state = CatalogBuilder.build(listOf(profile(ProfileKind.MAIN, chrome), profile(ProfileKind.PRIVATE, coffre)))
        assertEquals(listOf(chrome, coffre.copy(isPrivateSpace = true)), state.apps)
        assertEquals(PrivateSpaceState.UNLOCKED, state.privateSpace)
    }

    @Test
    fun locked_private_space_shows_no_app() {
        val state = CatalogBuilder.build(listOf(profile(ProfileKind.MAIN, chrome), profile(ProfileKind.PRIVATE, coffre, quiet = true)))
        assertEquals(listOf(chrome), state.apps)
        assertEquals(PrivateSpaceState.LOCKED, state.privateSpace)
    }

    @Test
    fun no_private_profile_means_absent() {
        val state = CatalogBuilder.build(listOf(profile(ProfileKind.MAIN, chrome), profile(ProfileKind.OTHER, mail)))
        assertEquals(listOf(chrome, mail), state.apps)
        assertEquals(PrivateSpaceState.ABSENT, state.privateSpace)
    }

    @Test
    fun a_profile_of_unknown_type_is_never_shown() {
        // Si Android ne dit pas de quel type est un profil, ce pourrait être l'Espace privé : on n'affiche rien
        val state = CatalogBuilder.build(listOf(profile(ProfileKind.MAIN, chrome), profile(ProfileKind.UNKNOWN, coffre)))
        assertEquals(listOf(chrome), state.apps)
    }
}
