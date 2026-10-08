package app.lanceur.settings

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ReorderableFavoritesTest {
    @get:Rule val rule = createComposeRule()

    private fun app(label: String) = AppEntry(AppKey("app.$label", "app.$label.Main", 0), label, false)

    private val a = app("A")
    private val b = app("B")
    private val c = app("C")

    private fun drag(label: String, rows: Float) {
        val rowPx = with(rule.density) { 64.dp.toPx() }
        rule.onNodeWithContentDescription("Déplacer $label").performTouchInput {
            down(center)
            repeat(10) { moveBy(Offset(0f, rows * rowPx / 10)) }
            up()
        }
        rule.waitForIdle()
    }

    @Test
    fun successive_drags_build_on_each_other() {
        var order by mutableStateOf(listOf(a, b, c))
        rule.setContent {
            val current by remember { derivedStateOf { order } }
            MaterialTheme {
                // Comme dans l'app : l'ordre enregistré revient dans `items` après chaque dépôt
                ReorderableApps(current, icon = {}, onCommit = { keys -> order = keys.map { k -> listOf(a, b, c).first { it.key == k } } })
            }
        }
        drag("A", 2.4f) // A, B, C → B, C, A
        assertEquals(listOf(b, c, a), order)
        drag("C", -1.4f) // → C, B, A
        assertEquals(listOf(c, b, a), order)
        drag("A", -1.2f) // → C, A, B
        assertEquals(listOf(c, a, b), order)
    }

    @Test
    fun folder_editor_reorders_its_apps() {
        var order: List<AppKey>? = null
        rule.setContent {
            MaterialTheme {
                app.lanceur.folders.FolderEditor(
                    folder = app.lanceur.folders.Folder(1, "Perso", app.lanceur.folders.FolderIcon.HOME, listOf(a.key, b.key, c.key)),
                    onSave = { _, _ -> }, onDelete = {}, onDismiss = {},
                    apps = listOf(a, b, c),
                    onReorder = { order = it },
                )
            }
        }
        drag("A", 1.4f)
        assertEquals(listOf(b.key, a.key, c.key), order)
    }
}
