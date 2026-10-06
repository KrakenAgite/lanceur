package app.lanceur.settings

import app.lanceur.i18n.tr
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
import app.lanceur.home.PageKind
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
    val setWidgetPageEnabled: (Boolean) -> Unit = {},
    val setNewsEnabled: (Boolean) -> Unit = {},
    val movePage: (PageKind, Int) -> Unit = { _, _ -> },
    val openLanguage: () -> Unit = {},
)

@Composable
fun SettingsScreen(
    favorites: List<AppEntry>,
    side: AlphabetSide,
    isDefaultLauncher: Boolean,
    permissionsGranted: Boolean,
    lockServiceEnabled: Boolean,
    pageOrder: List<PageKind>,
    widgetPageEnabled: Boolean,
    newsEnabled: Boolean,
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
        Text(tr("Réglages", "Settings"), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(vertical = 16.dp))
        SettingRow(
            title = tr("Écran d'accueil par défaut", "Default home app"),
            subtitle = if (isDefaultLauncher) tr("Lanceur est ton écran d'accueil", "Lanceur is your home app") else tr("Lanceur n'est pas encore ton écran d'accueil", "Lanceur is not your home app yet"),
            actionLabel = if (isDefaultLauncher) null else tr("Définir", "Set"),
            onAction = actions.setDefault,
        )
        SettingRow(
            title = tr("Agenda et contacts dans la recherche", "Calendar and contacts in search"),
            subtitle = if (permissionsGranted) tr("Autorisés", "Allowed") else tr("Non autorisés", "Not allowed"),
            actionLabel = if (permissionsGranted) null else tr("Autoriser", "Allow"),
            onAction = actions.requestPermissions,
        )
        SettingRow(
            title = tr("Double toucher pour verrouiller", "Double tap to lock"),
            subtitle = if (lockServiceEnabled) tr("Activé", "On") else tr("Non activé : à autoriser dans Accessibilité", "Off: allow it in Accessibility"),
            actionLabel = if (lockServiceEnabled) null else tr("Activer", "Turn on"),
            onAction = actions.enableLockService,
        )
        SettingRow(
            title = tr("Applis cachées", "Hidden apps"),
            subtitle = tr("Protégées par ton empreinte", "Protected by your fingerprint"),
            actionLabel = tr("Ouvrir", "Open"),
            onAction = actions.openHidden,
        )
        SettingRow(
            title = tr("Langue", "Language"),
            subtitle = tr("Français · suit le téléphone ou ton choix", "English · follows the phone or your choice"),
            actionLabel = tr("Changer", "Change"),
            onAction = actions.openLanguage,
        )
        PagesSection(pageOrder, widgetPageEnabled, newsEnabled, actions)
        SectionTitle(tr("Côté de l'alphabet", "Alphabet side"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = side == AlphabetSide.LEFT, onClick = { actions.setSide(AlphabetSide.LEFT) }, label = { Text(tr("Gauche", "Left")) })
            FilterChip(selected = side == AlphabetSide.RIGHT, onClick = { actions.setSide(AlphabetSide.RIGHT) }, label = { Text(tr("Droite", "Right")) })
        }
        SectionTitle(tr("Ordre des favoris", "Favorites order"))
        if (favorites.isEmpty()) {
            HintText(tr("Aucun favori pour l'instant.", "No favorites yet."))
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

/** Pages de gauche à droite, comme quand on fait défiler ; l'Accueil ne se masque pas. */
@Composable
private fun PagesSection(order: List<PageKind>, widgetsEnabled: Boolean, newsEnabled: Boolean, actions: SettingsActions) {
    SectionTitle(tr("Pages", "Pages"))
    HintText(tr("Appui long puis glisse pour changer l'ordre ; l'œil affiche ou masque la page", "Long press then drag to reorder; the eye shows or hides the page"))
    PagePreviews(
        order = order,
        isShown = { kind ->
            when (kind) {
                PageKind.HOME -> true
                PageKind.WIDGETS -> widgetsEnabled
                PageKind.NEWS -> newsEnabled
            }
        },
        onToggle = { kind, on ->
            when (kind) {
                PageKind.WIDGETS -> actions.setWidgetPageEnabled(on)
                PageKind.NEWS -> actions.setNewsEnabled(on)
                PageKind.HOME -> Unit
            }
        },
        onMove = actions.movePage,
    )
}
