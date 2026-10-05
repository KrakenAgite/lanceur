package app.lanceur.widgets

import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
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
import app.lanceur.ui.HintText
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
private val GAP = 12.dp

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
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        if (summary != null) SummaryCard(summary, actions)
        if (cards.isEmpty()) HintText("Ajoute ton premier widget")
        WidgetList(cards, editMode, label, isReconfigurable, widgetView, actions)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            if (editMode) {
                Button(onClick = { actions.setEditMode(false) }) { Text("Terminé") }
            } else {
                FilledTonalButton(onClick = actions.addWidget) { Text("+ Ajouter un widget") }
                if (cards.isNotEmpty()) OutlinedButton(onClick = { actions.setEditMode(true) }) { Text("Modifier") }
            }
        }
    }
}

@Composable
private fun SummaryCard(summary: DaySummaryState, actions: WidgetPageActions) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        Text(summary.dateLabel, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        when {
            !summary.calendarGranted ->
                TextButton(onClick = actions.requestCalendar) { Text("Autoriser l'agenda pour voir tes événements") }
            summary.events.isEmpty() ->
                Text("Rien de prévu", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> summary.events.forEach { line ->
                Text(
                    line.text,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().clickable { actions.openEvent(line.event) }.padding(vertical = 6.dp),
                )
            }
        }
        summary.alarmLabel?.let { alarm ->
            Text("⏰ $alarm", modifier = Modifier.clickable(onClick = actions.openClock).padding(vertical = 6.dp))
        }
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
    val heightPx = { card: WidgetCard -> with(density) { (card.slot.size.heightDp.dp + TOOLBAR_HEIGHT + GAP).toPx() } }

    Column(verticalArrangement = Arrangement.spacedBy(GAP)) {
        working.forEach { card ->
            key(card.slot.appWidgetId) {
                val dragging = card.slot.appWidgetId == draggingId
                Column(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f },
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
                            .clip(RoundedCornerShape(16.dp)),
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

@Composable
private fun EditToolbar(card: WidgetCard, label: String, reconfigurable: Boolean, actions: WidgetPageActions, handle: Modifier) {
    Row(Modifier.fillMaxWidth().height(TOOLBAR_HEIGHT), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Menu, contentDescription = "Déplacer $label", modifier = handle.padding(12.dp))
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
        WidgetSize.entries.forEach { size ->
            FilterChip(
                selected = card.slot.size == size,
                onClick = { actions.resize(card.slot, size) },
                label = { Text(size.shortLabel) },
                modifier = Modifier.padding(horizontal = 2.dp),
            )
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
