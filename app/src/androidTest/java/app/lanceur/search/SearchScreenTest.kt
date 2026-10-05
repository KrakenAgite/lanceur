package app.lanceur.search

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
}
