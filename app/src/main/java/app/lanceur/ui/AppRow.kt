package app.lanceur.ui

import app.lanceur.i18n.tr
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
    val background by animateColorAsState(
        if (highlighted) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        label = "surbrillance",
    )
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
            icon(entry.key)
            Spacer(Modifier.width(16.dp))
            Text(
                entry.label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            menuItems.forEach { action ->
                DropdownMenuItem(text = { Text(action.label) }, onClick = { menuOpen = false; onMenu(action) })
            }
        }
    }
}
