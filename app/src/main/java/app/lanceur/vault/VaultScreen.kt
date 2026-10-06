package app.lanceur.vault

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.apps.PrivateSpaceState
import app.lanceur.ui.AppMenuAction
import app.lanceur.ui.AppRow
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.blockTouchesBelow

class VaultActions(
    val launch: (AppEntry) -> Unit = {},
    val menu: (AppEntry, AppMenuAction) -> Unit = { _, _ -> },
    val unlockPrivateSpace: () -> Unit = {},
    val lockPrivateSpace: () -> Unit = {},
)

@Composable
fun VaultScreen(
    hidden: List<AppEntry>,
    privateApps: List<AppEntry>,
    privateSpace: PrivateSpaceState,
    icon: @Composable (AppKey) -> Unit,
    actions: VaultActions,
    modifier: Modifier = Modifier,
) {
    SecureWindow()
    LazyColumn(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouchesBelow()
            .systemBarsPadding(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item {
            Text(tr("Dossier caché", "Hidden folder"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        }
        item { SectionTitle(tr("Masquées", "Hidden")) }
        if (hidden.isEmpty()) {
            item { HintText(tr("Aucune appli cachée. Fais un appui long sur une appli, puis « Cacher ».", "No hidden apps. Long press an app, then “Hide”.")) }
        }
        items(hidden, key = { "h:" + it.key.encode() }) { entry ->
            AppRow(
                entry = entry,
                icon = icon,
                menuItems = listOf(AppMenuAction.UNHIDE, AppMenuAction.INFO),
                onClick = { actions.launch(entry) },
                onMenu = { actions.menu(entry, it) },
            )
        }
        item { SectionTitle(tr("Espace privé", "Private space")) }
        when (privateSpace) {
            PrivateSpaceState.ABSENT -> item {
                HintText(tr("Aucun Espace privé. Crée-le dans Paramètres > Sécurité et confidentialité > Espace privé.", "No Private space. Create it in Settings > Security & privacy > Private space."))
            }
            PrivateSpaceState.LOCKED -> item {
                Button(onClick = actions.unlockPrivateSpace, modifier = Modifier.padding(horizontal = 16.dp)) {
                    Icon(Icons.Default.Lock, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr("Déverrouiller", "Unlock"))
                }
            }
            PrivateSpaceState.UNLOCKED -> {
                items(privateApps, key = { "p:" + it.key.encode() }) { entry ->
                    AppRow(
                        entry = entry,
                        icon = icon,
                        menuItems = listOf(AppMenuAction.INFO),
                        onClick = { actions.launch(entry) },
                        onMenu = { actions.menu(entry, it) },
                    )
                }
                item {
                    OutlinedButton(onClick = actions.lockPrivateSpace, modifier = Modifier.padding(16.dp)) {
                        Text(tr("Verrouiller l'Espace privé", "Lock Private space"))
                    }
                }
            }
        }
    }
}
