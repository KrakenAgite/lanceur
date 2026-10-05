package app.lanceur.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.prefs.AlphabetSide
import app.lanceur.ui.HintText
import app.lanceur.ui.SectionTitle
import app.lanceur.ui.blockTouchesBelow

class SettingsActions(
    val setDefault: () -> Unit = {},
    val reorderFavorites: (List<AppKey>) -> Unit = {},
    val openHidden: () -> Unit = {},
    val requestPermissions: () -> Unit = {},
    val setSide: (AlphabetSide) -> Unit = {},
    val enableLockService: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    favorites: List<AppEntry>,
    side: AlphabetSide,
    isDefaultLauncher: Boolean,
    permissionsGranted: Boolean,
    lockServiceEnabled: Boolean,
    icon: @Composable (AppKey) -> Unit,
    actions: SettingsActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .blockTouchesBelow()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Réglages", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        SettingRow(
            title = "Écran d'accueil par défaut",
            subtitle = if (isDefaultLauncher) "Lanceur est ton écran d'accueil" else "Lanceur n'est pas encore ton écran d'accueil",
            actionLabel = if (isDefaultLauncher) null else "Définir",
            onAction = actions.setDefault,
        )
        SettingRow(
            title = "Agenda et contacts dans la recherche",
            subtitle = if (permissionsGranted) "Autorisés" else "Non autorisés",
            actionLabel = if (permissionsGranted) null else "Autoriser",
            onAction = actions.requestPermissions,
        )
        SettingRow(
            title = "Double toucher pour verrouiller",
            subtitle = if (lockServiceEnabled) "Activé" else "Non activé : à autoriser dans Accessibilité",
            actionLabel = if (lockServiceEnabled) null else "Activer",
            onAction = actions.enableLockService,
        )
        SettingRow(
            title = "Applis cachées",
            subtitle = "Protégées par ton empreinte",
            actionLabel = "Ouvrir",
            onAction = actions.openHidden,
        )
        SectionTitle("Côté de l'alphabet")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = side == AlphabetSide.LEFT, onClick = { actions.setSide(AlphabetSide.LEFT) }, label = { Text("Gauche") })
            FilterChip(selected = side == AlphabetSide.RIGHT, onClick = { actions.setSide(AlphabetSide.RIGHT) }, label = { Text("Droite") })
        }
        SectionTitle("Ordre des favoris")
        if (favorites.isEmpty()) {
            HintText("Aucun favori pour l'instant.")
        } else {
            ReorderableFavorites(favorites, icon, actions.reorderFavorites)
        }
    }
}

@Composable
private fun SettingRow(title: String, subtitle: String, actionLabel: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) FilledTonalButton(onClick = onAction) { Text(actionLabel) }
    }
}
