package app.lanceur.folders

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Chat
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Work
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.alphabet.ScrubInput
import app.lanceur.i18n.tr
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.FolderApps
import app.lanceur.ui.cardBackground

private val ROW_SHAPE = RoundedCornerShape(20.dp)

@Suppress("DEPRECATION") // Les variantes « AutoMirrored » n'existent pas pour toutes ces icônes
val FolderIcon.vector: ImageVector
    get() = when (this) {
        FolderIcon.FOLDER -> Icons.Outlined.Folder
        FolderIcon.WORK -> Icons.Outlined.Work
        FolderIcon.GAMES -> Icons.Outlined.SportsEsports
        FolderIcon.MUSIC -> Icons.Outlined.MusicNote
        FolderIcon.PHOTO -> Icons.Outlined.PhotoCamera
        FolderIcon.CHAT -> Icons.Outlined.Chat
        FolderIcon.SHOPPING -> Icons.Outlined.ShoppingCart
        FolderIcon.TRAVEL -> Icons.Outlined.Flight
        FolderIcon.SPORT -> Icons.Outlined.FitnessCenter
        FolderIcon.MONEY -> Icons.Outlined.AccountBalanceWallet
        FolderIcon.TOOLS -> Icons.Outlined.Build
        FolderIcon.BOOK -> Icons.Outlined.MenuBook
        FolderIcon.MOVIE -> Icons.Outlined.Movie
        FolderIcon.HOME -> Icons.Outlined.Home
        FolderIcon.HEALTH -> Icons.Outlined.FavoriteBorder
        FolderIcon.FOOD -> Icons.Outlined.Restaurant
        FolderIcon.SCHOOL -> Icons.Outlined.School
        FolderIcon.STAR -> Icons.Outlined.StarOutline
    }

/**
 * Icônes des dossiers, dans l'angle au-dessus de l'alphabet, qui se parcourent du doigt comme l'alphabet :
 * [onScrub] reçoit la position du doigt (hauteur = celle des icônes), [onRelease] le lever. Un toucher sans
 * glisser est signalé par `tap`, un appui long sans bouger par [onLongPress] (le lever est alors annulé).
 */
@Composable
fun FolderColumn(
    folders: List<FolderApps>,
    openId: Int?,
    activeIndex: Int?,
    side: AlphabetSide,
    onScrub: (ScrubInput) -> Unit,
    onRelease: (cancelled: Boolean, tap: Boolean) -> Unit,
    onLongPress: (Folder) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val threshold = with(density) { 24.dp.toPx() }
    val latestScrub by rememberUpdatedState(onScrub)
    val latestRelease by rememberUpdatedState(onRelease)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val latestSide by rememberUpdatedState(side)
    val latestFolders by rememberUpdatedState(folders)
    BoxWithConstraints(modifier.testTag("folders")) {
        val count = folders.size
        // Beaucoup de dossiers : les icônes se serrent pour toujours tenir dans l'angle
        val pitch = if (count == 0) MAX_PITCH else minOf(MAX_PITCH, maxHeight / count)
        val pitchPx = with(density) { pitch.toPx() }
        Column(
            Modifier.pointerInput(count, pitchPx) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    if (down.position.y >= pitchPx * count) return@awaitEachGesture
                    down.consume()
                    fun input(p: Offset) = ScrubInput(p.x, p.y, size.width.toFloat(), pitchPx * count, latestSide, threshold)
                    latestScrub(input(down.position))
                    var moved = false
                    var cancelled = true
                    var longPressed = false
                    while (true) {
                        val event = if (moved || longPressed) {
                            awaitPointerEvent()
                        } else {
                            withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
                        }
                        if (event == null) {
                            longPressed = true
                            val index = (down.position.y / pitchPx).toInt().coerceIn(0, count - 1)
                            latestFolders.getOrNull(index)?.let { latestLongPress(it.folder) }
                            continue
                        }
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) {
                            // Une annulation arrive comme un lever déjà consommé
                            cancelled = change.isConsumed || longPressed
                            break
                        }
                        change.consume()
                        if (longPressed) continue
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        latestScrub(input(change.position))
                    }
                    latestRelease(cancelled, !moved && !longPressed)
                }
            },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            folders.forEachIndexed { i, (folder) ->
                val open = folder.id == openId
                val active = i == activeIndex
                Box(Modifier.fillMaxWidth().height(pitch), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier
                            .size(minOf(pitch, 36.dp))
                            .clip(CircleShape)
                            .background(if (open || active) colors.primaryContainer else Color.Transparent)
                            .semantics { contentDescription = folder.name }
                            .testTag("folder-${folder.id}"),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            folder.icon.vector,
                            contentDescription = null,
                            tint = if (open || active) colors.onPrimaryContainer else colors.onSurface.copy(alpha = 0.85f),
                            modifier = Modifier.size(minOf(pitch * 0.62f, 22.dp)),
                        )
                    }
                }
            }
        }
    }
}

private val MAX_PITCH = 40.dp

/** Ranger une appli : chaque dossier se coche ou se décoche, et on peut en créer un. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderSheet(
    appLabel: String,
    folders: List<Folder>,
    contains: (Folder) -> Boolean,
    onToggle: (Folder, Boolean) -> Unit,
    onCreate: (name: String, icon: FolderIcon) -> Unit,
    onDismiss: () -> Unit,
) {
    var creating by remember { mutableStateOf(folders.isEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(tr("Ranger « $appLabel »", "File “$appLabel”"), style = MaterialTheme.typography.headlineSmall)
            folders.forEach { folder ->
                val checked = contains(folder)
                FolderRow(folder.icon.vector, folder.name, checked, Modifier.testTag("pick-folder-${folder.id}")) {
                    onToggle(folder, !checked)
                }
            }
            if (creating) {
                FolderForm(
                    initialName = "",
                    initialIcon = FolderIcon.FOLDER,
                    confirmLabel = tr("Créer et ranger", "Create and file"),
                    onConfirm = { name, icon -> onCreate(name, icon); creating = false },
                )
            } else {
                FolderRow(Icons.Outlined.Add, tr("Nouveau dossier", "New folder"), checked = false, Modifier.testTag("new-folder")) {
                    creating = true
                }
            }
        }
    }
}

@Composable
private fun FolderRow(icon: ImageVector, label: String, checked: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(ROW_SHAPE)
            .background(cardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = colors.onSurface)
        Spacer(Modifier.width(16.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (checked) Icon(Icons.Outlined.Check, contentDescription = tr("Rangée ici", "Filed here"), tint = colors.primary)
    }
}

/** Nom et choix de l'icône, communs à la création et à la modification. */
@Composable
private fun FolderForm(
    initialName: String,
    initialIcon: FolderIcon,
    confirmLabel: String?,
    onConfirm: (String, FolderIcon) -> Unit,
    onChange: (String, FolderIcon) -> Unit = { _, _ -> },
) {
    var name by remember { mutableStateOf(initialName) }
    var icon by remember { mutableStateOf(initialIcon) }
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it; onChange(it, icon) },
            label = { Text(tr("Nom du dossier", "Folder name")) },
            singleLine = true,
            shape = ROW_SHAPE,
            modifier = Modifier.fillMaxWidth().testTag("folder-name"),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FolderIcon.entries.forEach { choice ->
                val selected = choice == icon
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (selected) colors.primary else cardBackground())
                        .clickable { icon = choice; onChange(name, choice) }
                        .testTag("icon-${choice.name}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(choice.vector, contentDescription = null, tint = if (selected) colors.onPrimary else colors.onSurface)
                }
            }
        }
        if (confirmLabel != null) {
            Button(onClick = { onConfirm(name, icon) }, enabled = name.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                Text(confirmLabel)
            }
        }
    }
}

/** Modifier un dossier : nom, icône, ou suppression (les applis restent installées). */
@Composable
fun FolderEditor(
    folder: Folder,
    onSave: (String, FolderIcon) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(folder.name) }
    var icon by remember { mutableStateOf(folder.icon) }
    var confirmDelete by remember { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(tr("Supprimer « ${folder.name} » ?", "Delete “${folder.name}”?")) },
            text = { Text(tr("Les applis restent installées et dans la liste.", "The apps stay installed and in the list.")) },
            confirmButton = { Button(onClick = onDelete) { Text(tr("Supprimer", "Delete")) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(tr("Annuler", "Cancel")) } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Dossier", "Folder")) },
        text = {
            FolderForm(folder.name, folder.icon, confirmLabel = null, onConfirm = { _, _ -> }, onChange = { n, i -> name = n; icon = i })
        },
        confirmButton = {
            Button(onClick = { onSave(name, icon) }, enabled = name.isNotBlank()) { Text(tr("Enregistrer", "Save")) }
        },
        dismissButton = {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete-folder")) {
                Text(tr("Supprimer", "Delete"), color = MaterialTheme.colorScheme.error)
            }
        },
    )
}
