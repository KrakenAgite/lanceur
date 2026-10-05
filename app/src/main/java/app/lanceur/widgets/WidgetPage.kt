package app.lanceur.widgets

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.animation.core.animateFloatAsState
import java.util.Locale
import app.lanceur.summary.SummaryLine
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.lanceur.settings.Reorder
import app.lanceur.summary.DaySummaryState
import app.lanceur.summary.SummaryEvent
import app.lanceur.ui.blockTouchesBelow

class WidgetPageActions(
    val openEvent: (SummaryEvent) -> Unit = {},
    val openClock: () -> Unit = {},
    val requestCalendar: () -> Unit = {},
    val addWidget: () -> Unit = {},
    val setEditMode: (Boolean) -> Unit = {},
    val remove: (WidgetSlot) -> Unit = {},
    val resize: (WidgetSlot, WidgetSize) -> Unit = { _, _ -> },
    val reconfigure: (WidgetSlot) -> Unit = {},
    val reorder: (List<Int>) -> Unit = {},
)

private val TOOLBAR_HEIGHT = 48.dp
private val GAP = 14.dp
private val FRAME_PADDING = 8.dp

@Composable
fun WidgetPage(
    summary: DaySummaryState?,
    cards: List<WidgetCard>,
    editMode: Boolean,
    label: (WidgetSlot) -> String,
    isReconfigurable: (WidgetSlot) -> Boolean,
    widgetView: @Composable (WidgetSlot, Modifier) -> Unit,
    actions: WidgetPageActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxSize()
            // Dégradé : le haut laisse voir le fond d'écran, le bas reste lisible
            .background(
                Brush.verticalGradient(
                    0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.15f),
                    1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                ),
            )
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (summary != null) SummaryCard(summary, actions)
        if (cards.isEmpty()) {
            EmptyWidgetsCard(onAdd = actions.addWidget)
        } else {
            WidgetList(cards, editMode, label, isReconfigurable, widgetView, actions)
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            ) {
                if (editMode) {
                    Button(onClick = { actions.setEditMode(false) }) { ButtonContent(Icons.Default.Check, "Terminé") }
                } else {
                    FilledTonalButton(onClick = actions.addWidget) { ButtonContent(Icons.Default.Add, "Ajouter") }
                    OutlinedButton(onClick = { actions.setEditMode(true) }) { ButtonContent(Icons.Default.Edit, "Modifier") }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DaySummaryState, actions: WidgetPageActions) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            // Légèrement translucide : le fond d'écran transparaît, aux couleurs Material You
            .background(colors.surfaceContainerHigh.copy(alpha = 0.82f))
            .padding(horizontal = 22.dp, vertical = 20.dp),
    ) {
        Text(
            summary.dateLabel.substringBefore(' ').uppercase(Locale.FRENCH),
            style = MaterialTheme.typography.labelLarge,
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 2.sp,
        )
        Text(summary.dateLabel.substringAfter(' '), style = MaterialTheme.typography.headlineMedium, color = colors.onSurface)
        Spacer(Modifier.height(14.dp))
        when {
            !summary.calendarGranted ->
                TextButton(onClick = actions.requestCalendar) { Text("Autoriser l'agenda pour voir tes événements") }
            summary.events.isEmpty() ->
                Text("Rien de prévu", style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            else -> summary.events.forEachIndexed { index, line ->
                // Un seul intertitre au passage à demain, au lieu de répéter « Demain » sur chaque ligne
                if (line.tomorrow && (index == 0 || !summary.events[index - 1].tomorrow)) {
                    Text(
                        "DEMAIN",
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                        letterSpacing = 1.5.sp,
                        modifier = Modifier.padding(top = if (index == 0) 0.dp else 4.dp, bottom = 6.dp),
                    )
                }
                TimelineRow(line, isLast = index == summary.events.lastIndex) { actions.openEvent(line.event) }
            }
        }
        summary.alarmLabel?.let { alarm ->
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier
                    .clip(CircleShape)
                    .background(colors.secondaryContainer)
                    .clickable(onClick = actions.openClock)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text("⏰ $alarm", style = MaterialTheme.typography.labelLarge, color = colors.onSecondaryContainer)
            }
        }
    }
}

/** Une étape de la chronologie : pastille de la couleur de l'agenda, fil vers la suivante, heure, titre. */
@Composable
private fun TimelineRow(line: SummaryLine, isLast: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    // Anniversaire : pastille aux couleurs festives de Material You
    val dot = if (line.birthday) colors.tertiary else line.event.color?.let { Color(it) } ?: colors.primary
    Row(
        Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
    ) {
        Column(Modifier.width(20.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Box(Modifier.size(10.dp).clip(CircleShape).background(dot))
            if (!isLast) {
                Box(Modifier.padding(top = 4.dp).width(2.dp).weight(1f).background(colors.outlineVariant))
            }
        }
        Text(
            line.timeLabel,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            modifier = Modifier.width(72.dp).padding(start = 8.dp, top = 3.dp, bottom = 12.dp),
        )
        Text(
            line.title,
            style = MaterialTheme.typography.bodyLarge,
            color = colors.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(top = 2.dp, bottom = 12.dp, end = 4.dp),
        )
    }
}

/** Liste des widgets ; en mode édition, glisser la poignée les réordonne (hauteurs différentes : `HeightReorder`). */
@Composable
private fun WidgetList(
    cards: List<WidgetCard>,
    editMode: Boolean,
    label: (WidgetSlot) -> String,
    isReconfigurable: (WidgetSlot) -> Boolean,
    widgetView: @Composable (WidgetSlot, Modifier) -> Unit,
    actions: WidgetPageActions,
) {
    // Un seul état stable, resynchronisé hors glisser (même correctif que les favoris)
    var working by remember { mutableStateOf(cards) }
    var draggingId by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestReorder by rememberUpdatedState(actions.reorder)
    LaunchedEffect(cards) { if (draggingId == null) working = cards }
    val density = LocalDensity.current
    val heightPx = { card: WidgetCard -> with(density) { (card.slot.size.heightDp.dp + TOOLBAR_HEIGHT + FRAME_PADDING * 2 + GAP).toPx() } }

    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        working.forEach { card ->
            key(card.slot.appWidgetId) {
                val dragging = card.slot.appWidgetId == draggingId
                val frameScale by animateFloatAsState(if (editMode) 0.97f else 1f, label = "édition")
                // Chaque widget est posé dans un cadre arrondi translucide, comme sur l'accueil du Pixel
                Column(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            scaleX = frameScale
                            scaleY = frameScale
                        }
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha = if (dragging) 0.85f else 0.55f))
                        .padding(FRAME_PADDING),
                ) {
                    if (editMode) {
                        EditToolbar(
                            card = card,
                            label = label(card.slot),
                            reconfigurable = isReconfigurable(card.slot),
                            actions = actions,
                            handle = Modifier.pointerInput(card.slot.appWidgetId) {
                                detectDragGestures(
                                    onDragStart = { draggingId = card.slot.appWidgetId; dragOffset = 0f },
                                    onDragEnd = {
                                        draggingId = null
                                        dragOffset = 0f
                                        latestReorder(working.map { it.slot.appWidgetId })
                                    },
                                    onDragCancel = { draggingId = null; dragOffset = 0f },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.y
                                    val from = working.indexOfFirst { it.slot.appWidgetId == card.slot.appWidgetId }
                                    val step = HeightReorder.step(working.map(heightPx), from, dragOffset)
                                    if (step.to != from) {
                                        working = Reorder.move(working, from, step.to)
                                        dragOffset = step.offset
                                    }
                                }
                            },
                        )
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(card.slot.size.heightDp.dp)
                            .clip(RoundedCornerShape(18.dp)),
                    ) {
                        when (card) {
                            is WidgetCard.Live -> widgetView(card.slot, Modifier.fillMaxSize())
                            is WidgetCard.Unavailable -> UnavailableCard(label(card.slot)) { actions.remove(card.slot) }
                        }
                        // En mode édition, le widget ne reçoit plus aucun toucher
                        if (editMode) {
                            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.15f)).blockTouchesBelow())
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditToolbar(card: WidgetCard, label: String, reconfigurable: Boolean, actions: WidgetPageActions, handle: Modifier) {
    Row(Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Menu, contentDescription = "Déplacer $label", modifier = handle.padding(12.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        SingleChoiceSegmentedButtonRow(Modifier.padding(horizontal = 4.dp)) {
            WidgetSize.entries.forEachIndexed { index, size ->
                SegmentedButton(
                    selected = card.slot.size == size,
                    onClick = { actions.resize(card.slot, size) },
                    shape = SegmentedButtonDefaults.itemShape(index, WidgetSize.entries.size),
                    icon = {},
                    label = { Text(size.shortLabel) },
                )
            }
        }
        if (reconfigurable) {
            IconButton(onClick = { actions.reconfigure(card.slot) }) { Icon(Icons.Default.Settings, contentDescription = "Reconfigurer") }
        }
        IconButton(onClick = { actions.remove(card.slot) }) { Icon(Icons.Default.Clear, contentDescription = "Retirer") }
    }
}

@Composable
private fun UnavailableCard(label: String, onRemove: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Widget indisponible", style = MaterialTheme.typography.titleMedium)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onRemove) { Text("Retirer") }
    }
}

@Composable
private fun ButtonContent(icon: ImageVector, text: String) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
    Spacer(Modifier.width(8.dp))
    Text(text)
}

/** Aucun widget : une grande carte en pointillés qu'on touche pour en ajouter un. */
@Composable
private fun EmptyWidgetsCard(onAdd: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val outline = colors.outline
    Column(
        Modifier
            .fillMaxWidth()
            .height(150.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(colors.surfaceContainer.copy(alpha = 0.35f))
            .drawBehind {
                drawRoundRect(
                    color = outline,
                    style = Stroke(width = 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(18f, 12f))),
                    cornerRadius = CornerRadius(24.dp.toPx()),
                )
            }
            .clickable(onClick = onAdd)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Add, contentDescription = null, tint = colors.primary, modifier = Modifier.size(32.dp))
        Spacer(Modifier.height(6.dp))
        Text("Ajoute ton premier widget", style = MaterialTheme.typography.titleMedium, color = colors.onSurface)
        Text("Météo, agenda, musique…", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}
