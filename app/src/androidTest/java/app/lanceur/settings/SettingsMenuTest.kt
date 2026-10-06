package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.home.PageLayout
import app.lanceur.prefs.AlphabetSide
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class SettingsMenuTest {
    @get:Rule val rule = createComposeRule()

    private val now = 1_800_000_000_000L

    private fun show(actions: SettingsActions = SettingsActions(), isDefault: Boolean = true, page: SettingsPage? = null) = rule.setContent {
        MaterialTheme {
            SettingsScreen(
                favorites = emptyList(), side = AlphabetSide.RIGHT, isDefaultLauncher = isDefault, permissionsGranted = false,
                lockServiceEnabled = true, pageOrder = PageLayout.DEFAULT_ORDER, widgetPageEnabled = true, newsEnabled = false,
                icon = {}, actions = actions,
                backup = BackupState(auto = true, folderName = "Documents", last = now - 5 * 60_000),
                updates = UpdatesState(installed = "1.4.0", latest = "1.5.0"),
                now = now,
                initialPage = page,
            )
        }
    }

    @Test
    fun the_menu_shows_each_section_with_its_current_state_and_opens_it() {
        show()
        rule.onNodeWithText("Alphabet à droite · 0 favoris").assertIsDisplayed()
        rule.onNodeWithText("Widgets · Accueil").assertIsDisplayed()
        rule.onNodeWithText("1 à autoriser").assertIsDisplayed()
        rule.onNodeWithText("Automatique · il y a 5 min").performScrollTo().assertIsDisplayed()
        rule.onNodeWithText("1.5.0 disponible").performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("settings-HOME").performScrollTo().performClick()
        rule.onNodeWithText("Côté de l'alphabet").assertIsDisplayed()
        rule.onNodeWithTag("settings-back").performClick()
        rule.onNodeWithText("Réglages").assertIsDisplayed()
    }

    @Test
    fun a_home_app_banner_appears_when_lanceur_is_not_the_default() {
        var asked = false
        show(SettingsActions(setDefault = { asked = true }), isDefault = false)
        rule.onNodeWithText("Définir").performClick()
        assertTrue(asked)
    }

    @Test
    fun backup_page_saves_restores_and_switches_automatic_backups() {
        val calls = mutableListOf<String>()
        show(
            SettingsActions(backupNow = { calls += "save" }, restoreBackup = { calls += "restore" }, setBackupAuto = { calls += "auto=$it" }, chooseBackupFolder = { calls += "folder" }),
            page = SettingsPage.BACKUP,
        )
        rule.onNodeWithText("Documents").assertIsDisplayed()
        rule.onNodeWithTag("backup-now").performClick()
        rule.onNodeWithTag("backup-restore").performClick()
        rule.onNodeWithTag("backup-auto").performClick()
        rule.onNodeWithText("Changer").performClick()
        assertEquals(listOf("save", "restore", "auto=false", "folder"), calls)
    }

    @Test
    fun about_page_shows_the_update_and_checks_on_demand() {
        val calls = mutableListOf<String>()
        show(SettingsActions(checkUpdatesNow = { calls += "check" }, openReleases = { calls += "open" }, setUpdatesEnabled = { calls += "enabled=$it" }), page = SettingsPage.ABOUT)
        rule.onNodeWithText("1.4.0 · 1.5.0 disponible").assertIsDisplayed()
        rule.onNodeWithText("Télécharger").performClick()
        rule.onNodeWithTag("updates-check").performClick()
        rule.onNodeWithTag("updates-enabled").performClick()
        assertEquals(listOf("open", "check", "enabled=false"), calls)
    }
}
