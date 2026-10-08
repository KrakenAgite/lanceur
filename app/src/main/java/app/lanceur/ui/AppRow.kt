package app.lanceur.ui

import app.lanceur.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import app.lanceur.apps.AppShortcut
import app.lanceur.apps.LocalAppShortcuts
import app.lanceur.builtin.media.LocalBadges
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey

enum class AppMenuAction(private val fr: String, private val en: String) {
    ADD_FAVORITE("Ajouter aux favoris", "Add to favorites"),
    REMOVE_FAVORITE("Retirer des favoris", "Remove from favorites"),
    HIDE("Cacher", "Hide"),
    UNHIDE("Ne plus cacher", "Unhide"),
    INFO("Infos de l'appli", "App info"),
    UNINSTALL("Désinstaller", "Uninstall"),
    FOLDER("Ranger dans un dossier…", "Add to folder…"),
    REMOVE_FROM_FOLDER("Retirer du dossier", "Remove from folder"),
    RENAME("Renommer…", "Rename…"),
    PAUSE("Pause avant d'ouvrir", "Pause before opening"),
    UNPAUSE("Ouvrir sans pause", "Open without pause"),
    ;

    val label: String get() = tr(fr, en)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppRow(
    entry: AppEntry,
    icon: @Composable (AppKey) -> Unit,
    menuItems: List<AppMenuAction>,
    onClick: () -> Unit,
    onMenu: (AppMenuAction) -> Unit,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val shortcutSource = LocalAppShortcuts.current
    val iconPx = with(LocalDensity.current) { 24.dp.roundToPx() }
    // Raccourcis lus à l'ouverture du menu seulement (appel système)
    val shortcuts by produceState(emptyList<AppShortcut>(), menuOpen, entry.key) {
        value = if (menuOpen) withContext(Dispatchers.IO) { shortcutSource.list(entry.key, iconPx) } else emptyList()
    }
    val badged = (entry.key.packageName to entry.key.userSerial) in LocalBadges.current
    val background by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "surbrillance",
    )
    val style = LocalAppLabelStyle.current
    Box(modifier) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (menuItems.isEmpty()) null else ({ menuOpen = true }),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (style.showIcons) {
                icon(entry.key)
                Spacer(Modifier.width(16.dp))
            }
            Text(
                style.format(entry.label),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            if (badged) {
                Box(
                    Modifier
                        .padding(start = 8.dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .testTag("badge"),
                )
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            shortcuts.forEach { shortcut ->
                DropdownMenuItem(
                    text = { Text(shortcut.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    leadingIcon = shortcut.icon?.let { bitmap -> { Image(bitmap, contentDescription = null, modifier = Modifier.size(24.dp)) } },
                    onClick = { menuOpen = false; shortcutSource.open(shortcut) },
                )
            }
            if (shortcuts.isNotEmpty()) HorizontalDivider()
            menuItems.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = { menuOpen = false; onMenu(action) })
            }
        }
    }
}
