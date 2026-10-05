package app.lanceur.builtin.contacts

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.lanceur.widgets.WidgetSize
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class FavoritesCardTest {
    @get:Rule val rule = createComposeRule()

    private val alice = FavoriteContact("uri-a", "Alice Durand", "0601020304", null)
    private val bob = FavoriteContact("uri-b", "Bob", null, null)

    @Test
    fun tap_calls_long_press_texts_and_no_number_opens() {
        val calls = mutableListOf<String>()
        val actions = FavoritesActions(call = { calls += "call:$it" }, sms = { calls += "sms:$it" }, open = { calls += "open:$it" })
        rule.setContent { MaterialTheme { Box(Modifier.height(120.dp)) { FavoritesCard(FavoritesState.Loaded(listOf(alice, bob)), WidgetSize.SMALL, actions) } } }
        rule.onNodeWithText("Alice").performClick()
        rule.onNodeWithText("Alice").performTouchInput { longClick() }
        rule.onNodeWithText("Bob").performClick()
        assertEquals(listOf("call:0601020304", "sms:0601020304", "open:uri-b"), calls)
    }

    @Test
    fun empty_and_no_permission() {
        var asked = false
        rule.setContent { MaterialTheme { FavoritesCard(FavoritesState.NoPermission, WidgetSize.SMALL, FavoritesActions(requestPermission = { asked = true })) } }
        rule.onNodeWithText("Autoriser les contacts").performClick()
        assertEquals(true, asked)
    }

    @Test
    fun no_favorites_explains_how_to_add_them() {
        rule.setContent { MaterialTheme { FavoritesCard(FavoritesState.Loaded(emptyList()), WidgetSize.SMALL, FavoritesActions()) } }
        rule.onNodeWithText("Ajoute des favoris ⭐ dans Contacts").assertIsDisplayed()
    }
}
