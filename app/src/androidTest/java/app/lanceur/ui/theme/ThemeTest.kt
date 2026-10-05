package app.lanceur.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.v2.createComposeRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ThemeTest {
    @get:Rule val rule = createComposeRule()

    /** Sans Surface, Compose écrit en noir : illisible sur le fond sombre de la recherche, du dossier et des réglages. */
    @Test
    fun default_text_color_follows_the_theme() {
        var content: Color? = null
        var expected: Color? = null
        rule.setContent {
            LanceurTheme {
                content = LocalContentColor.current
                expected = MaterialTheme.colorScheme.onSurface
            }
        }
        rule.waitForIdle()
        assertEquals(expected, content)
    }
}
