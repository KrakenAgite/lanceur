package app.lanceur.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SearchScreenTest {
    @get:Rule val rule = createComposeRule()

    private val banque = AppEntry(AppKey("app.banque", "app.banque.Main", 0), "Banque", false)

    @Test
    fun shows_each_kind_of_result() {
        val results = listOf(
            SearchResult.App(banque),
            SearchResult.Calc("2*3", "6"),
            SearchResult.Setting("android.settings.SETTINGS", "Paramètres"),
            SearchResult.Web("ba"),
        )
        rule.setContent { MaterialTheme { SearchScreen("ba", results, icon = {}, actions = SearchActions()) } }
        rule.onNodeWithText("Banque").assertIsDisplayed()
        rule.onNodeWithText("= 6").assertIsDisplayed()
        rule.onNodeWithText("Paramètres").assertIsDisplayed()
        rule.onNodeWithText("Rechercher « ba » sur le web").assertIsDisplayed()
    }

    @Test
    fun keyboard_action_opens_the_first_real_result() {
        var opened: SearchResult? = null
        rule.setContent {
            MaterialTheme {
                SearchScreen(
                    "ba",
                    listOf(SearchResult.PermissionHint, SearchResult.Web("ba")),
                    icon = {},
                    actions = SearchActions(open = { opened = it }),
                )
            }
        }
        rule.onNodeWithTag("search-field").performImeAction()
        assertEquals(SearchResult.Web("ba"), opened)
    }

    @Test
    fun results_are_grouped_under_section_titles() {
        val results = listOf(SearchResult.App(banque), SearchResult.Setting("android.settings.SETTINGS", "Paramètres"), SearchResult.Web("ba"))
        rule.setContent { MaterialTheme { SearchScreen("ba", results, icon = {}, actions = SearchActions()) } }
        rule.onNodeWithText("APPLIS").assertIsDisplayed()
        rule.onNodeWithText("RÉGLAGES").assertIsDisplayed()
        rule.onNodeWithText("WEB").assertIsDisplayed()
    }

    @Test
    fun clear_button_empties_the_query() {
        var query: String? = null
        rule.setContent {
            MaterialTheme { SearchScreen("ba", emptyList(), icon = {}, actions = SearchActions(queryChange = { query = it })) }
        }
        // Action d'accessibilité : le clavier qui s'ouvre déplace le champ pendant un toucher simulé
        rule.onNodeWithContentDescription("Effacer").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("", query)
    }

    @Test
    fun birthdays_show_a_gift_and_the_short_name() {
        val birthday = SearchResult.Event(1, "Léa - Anniversaire", 0, 86_400_000, true, null)
        rule.setContent { MaterialTheme { SearchScreen("léa", listOf(birthday), icon = {}, actions = SearchActions()) } }
        rule.onNodeWithText("🎁").assertIsDisplayed()
        rule.onNodeWithText("Léa").assertIsDisplayed()
    }
}
