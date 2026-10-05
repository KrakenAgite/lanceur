package app.lanceur.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import kotlin.math.roundToInt

private val ROW_HEIGHT = 64.dp

/** Glisser la poignée pour déplacer un favori ; l'ordre est enregistré quand on lâche. */
@Composable
fun ReorderableFavorites(items: List<AppEntry>, icon: @Composable (AppKey) -> Unit, onCommit: (List<AppKey>) -> Unit) {
    // Un seul état stable : les gestes déjà lancés (pointerInput par clé) lisent toujours la liste à jour
    var working by remember { mutableStateOf(items) }
    var draggingKey by remember { mutableStateOf<AppKey?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val latestOnCommit by rememberUpdatedState(onCommit)
    LaunchedEffect(items) { if (draggingKey == null) working = items }
    val rowHeightPx = with(LocalDensity.current) { ROW_HEIGHT.toPx() }

    Column {
        working.forEach { entry ->
            key(entry.key) {
                val dragging = entry.key == draggingKey
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(ROW_HEIGHT)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer { translationY = if (dragging) dragOffset else 0f }
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (dragging) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    icon(entry.key)
                    Spacer(Modifier.width(16.dp))
                    Text(entry.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Icon(
                        Icons.Default.Menu,
                        contentDescription = "Déplacer ${entry.label}",
                        modifier = Modifier.pointerInput(entry.key) {
                            detectDragGestures(
                                onDragStart = { draggingKey = entry.key; dragOffset = 0f },
                                onDragEnd = { draggingKey = null; dragOffset = 0f; latestOnCommit(working.map { it.key }) },
                                onDragCancel = { draggingKey = null; dragOffset = 0f },
                            ) { change, amount ->
                                change.consume()
                                dragOffset += amount.y
                                val from = working.indexOfFirst { it.key == entry.key }
                                val to = (from + (dragOffset / rowHeightPx).roundToInt()).coerceIn(0, working.lastIndex)
                                if (to != from) {
                                    working = Reorder.move(working, from, to)
                                    dragOffset -= (to - from) * rowHeightPx
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}
