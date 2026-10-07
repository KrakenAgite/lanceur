package app.lanceur.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import app.lanceur.apps.icons.IconPackInfo
import app.lanceur.apps.icons.IconShape
import app.lanceur.apps.icons.IconStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AppearanceTest {
    @get:Rule val rule = createComposeRule()

    private val state = AppearanceState(
        iconStyle = IconStyle(pack = "com.pack.arc"),
        packs = listOf(IconPackInfo("com.pack.arc", "Arcticons"), IconPackInfo("com.pack.lines", "Lines")),
        wallpaperLabel = "Fond d'écran et style",
    )

    @Test
    fun section_shows_wallpaper_app_and_pack_and_changes_shape_and_theme() {
        var style: IconStyle? = null
        var wallpaper = false
        var choose = false
        rule.setContent {
            MaterialTheme {
                Column(androidx.compose.ui.Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) { AppearanceSection(state, SettingsActions(openWallpaper = { wallpaper = true }, setIconStyle = { style = it }), onChoosePack = { choose = true }) }
            }
        }
        rule.onNodeWithText("Fond d'écran et style").assertExists()
        rule.onNodeWithText("Arcticons").assertExists()
        rule.onNodeWithText("Ouvrir").performClick()
        assertTrue(wallpaper)
        rule.onNodeWithText("Choisir").performClick()
        assertTrue(choose)
        rule.onNodeWithTag("shape-CIRCLE").performScrollTo().performClick()
        assertEquals(IconStyle(pack = "com.pack.arc", shape = IconShape.CIRCLE), style)
        rule.onNodeWithTag("shape-HEART").performScrollTo().performClick()
        assertEquals(IconStyle(pack = "com.pack.arc", shape = IconShape.HEART), style)
        rule.onNodeWithTag("icon-themed").performScrollTo().performClick()
        assertEquals(IconStyle(pack = "com.pack.arc", themed = true), style)
    }

    @Test
    fun icons_and_capitals_switches_and_icon_options_hidden_without_icons() {
        val styles = mutableListOf<app.lanceur.ui.AppLabelStyle>()
        rule.setContent {
            MaterialTheme {
                Column {
                    AppearanceSection(state.copy(labelStyle = app.lanceur.ui.AppLabelStyle(showIcons = false)), SettingsActions(setAppLabelStyle = { styles += it }), onChoosePack = {})
                }
            }
        }
        rule.onNodeWithTag("shape-CIRCLE").assertDoesNotExist()
        rule.onNodeWithText("Arcticons").assertDoesNotExist()
        rule.onNodeWithTag("show-icons").performClick()
        rule.onNodeWithTag("label-uppercase").performClick()
        assertEquals(listOf(app.lanceur.ui.AppLabelStyle(showIcons = true), app.lanceur.ui.AppLabelStyle(showIcons = false, uppercase = true)), styles)
    }

    @Test
    fun pack_list_picks_a_pack_or_the_system_icons_and_links_to_the_store() {
        val picks = mutableListOf<String?>()
        var store = false
        rule.setContent { MaterialTheme { IconPackList(state, onPick = { picks += it }, onFindPacks = { store = true }) } }
        rule.onNodeWithTag("pack-com.pack.lines").performClick()
        rule.onNodeWithTag("pack-system").performClick()
        rule.onNodeWithTag("pack-store").performScrollTo().performClick()
        assertEquals(listOf("com.pack.lines", null), picks)
        assertTrue(store)
    }
}
