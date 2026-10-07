package app.lanceur.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.apps.icons.IconPackInfo
import app.lanceur.apps.icons.IconRenderer
import app.lanceur.apps.icons.IconShape
import app.lanceur.apps.icons.IconStyle
import app.lanceur.i18n.tr
import app.lanceur.ui.AppLabelStyle
import app.lanceur.ui.HintText
import app.lanceur.ui.cardBackground

/** Ce que la section Apparence affiche : style actuel, packs installés, outil de fond d'écran. */
data class AppearanceState(
    val iconStyle: IconStyle = IconStyle(),
    val labelStyle: AppLabelStyle = AppLabelStyle(),
    val packs: List<IconPackInfo> = emptyList(),
    val wallpaperLabel: String? = null,
)

private val ROW_SHAPE = RoundedCornerShape(20.dp)

@Composable
fun AppearanceSection(state: AppearanceState, actions: SettingsActions, onChoosePack: () -> Unit) {
    SettingRow(
        title = tr("Fond d'écran", "Wallpaper"),
        subtitle = state.wallpaperLabel ?: tr("Outil du téléphone", "Phone's wallpaper app"),
        actionLabel = tr("Ouvrir", "Open"),
        onAction = actions.openWallpaper,
    )
    SwitchRow(
        title = tr("Afficher les icônes", "Show icons"),
        subtitle = tr("Sinon, seulement le nom des applis", "Otherwise, app names only"),
        checked = state.labelStyle.showIcons,
        tag = "show-icons",
    ) { actions.setAppLabelStyle(state.labelStyle.copy(showIcons = it)) }
    SwitchRow(
        title = tr("Noms en majuscules", "Names in capitals"),
        subtitle = tr("CALENDRIER au lieu de Calendrier", "CALENDAR instead of Calendar"),
        checked = state.labelStyle.uppercase,
        tag = "label-uppercase",
    ) { actions.setAppLabelStyle(state.labelStyle.copy(uppercase = it)) }
    // Pack, forme et thème n'ont de sens qu'avec les icônes
    if (state.labelStyle.showIcons) {
        val packName = state.iconStyle.pack?.let { p -> state.packs.firstOrNull { it.packageName == p }?.label ?: p }
        SettingRow(
            title = tr("Pack d'icônes", "Icon pack"),
            subtitle = packName ?: tr("Icônes du système", "System icons"),
            actionLabel = tr("Choisir", "Choose"),
            onAction = onChoosePack,
        )
        Text(tr("Forme des icônes", "Icon shape"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
        // Grille sur plusieurs lignes : toutes les formes d'un coup d'œil
        FlowRow(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4,
        ) {
            IconShape.entries.forEach { shape ->
                ShapeChoice(shape, selected = shape == state.iconStyle.shape) { actions.setIconStyle(state.iconStyle.copy(shape = shape)) }
            }
        }
        SwitchRow(
            title = tr("Icônes thématisées", "Themed icons"),
            subtitle = tr("Monochromes, aux couleurs du fond d'écran", "Monochrome, in your wallpaper colors"),
            checked = state.iconStyle.themed,
            tag = "icon-themed",
        ) { actions.setIconStyle(state.iconStyle.copy(themed = it)) }
        if (state.iconStyle.pack != null) {
            HintText(tr("Forme et thème s'appliquent aux applis absentes du pack.", "Shape and theme apply to apps missing from the pack."))
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

@Composable
private fun RowScope.ShapeChoice(shape: IconShape, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .weight(1f)
            .clip(ROW_SHAPE)
            .background(if (selected) colors.primaryContainer else cardBackground())
            .clickable(onClick = onClick)
            .testTag("shape-${shape.name}")
            .padding(horizontal = 4.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val fill = if (selected) colors.primary else colors.onSurfaceVariant
        Canvas(Modifier.size(36.dp)) {
            val path = IconRenderer.shapePath(shape, size.minDimension)
            if (path != null) drawPath(path.asComposePath(), fill)
            else {
                // Système : la forme du téléphone, évoquée par un cercle en pointillés
                drawCircle(fill, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(), pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())),
                ))
            }
        }
        Text(
            shape.label,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) colors.onPrimaryContainer else colors.onSurface,
        )
    }
}

/** Packs installés + icônes du système, et un lien vers le Play Store. */
@Composable
fun IconPackList(state: AppearanceState, onPick: (String?) -> Unit, onFindPacks: () -> Unit) {
    Column(
        Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(tr("Pack d'icônes", "Icon pack"), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(bottom = 8.dp))
        PackRow(tr("Icônes du système", "System icons"), selected = state.iconStyle.pack == null, tag = "pack-system") { onPick(null) }
        state.packs.forEach { pack ->
            PackRow(pack.label, selected = state.iconStyle.pack == pack.packageName, tag = "pack-${pack.packageName}") { onPick(pack.packageName) }
        }
        if (state.packs.isEmpty()) {
            HintText(tr("Aucun pack installé. Les packs au format Nova sont compatibles.", "No pack installed. Nova-compatible packs work."))
        }
        PackRow(tr("Trouver des packs sur le Play Store", "Find packs on the Play Store"), selected = false, tag = "pack-store", accent = true, onClick = onFindPacks)
    }
}

@Composable
private fun PackRow(label: String, selected: Boolean, tag: String, accent: Boolean = false, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(if (selected) colors.primaryContainer else cardBackground())
            .clickable(onClick = onClick)
            .testTag(tag)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = when {
                accent -> colors.primary
                selected -> colors.onPrimaryContainer
                else -> colors.onSurface
            },
        )
        if (selected) Text("✓", color = colors.primary, fontWeight = FontWeight.Bold)
        if (accent) Text("↗", color = colors.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackSheet(state: AppearanceState, onPick: (String?) -> Unit, onFindPacks: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        IconPackList(state, onPick, onFindPacks)
    }
}
