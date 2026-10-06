package app.lanceur.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.addOutline
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.lanceur.home.PageKind
import app.lanceur.home.PageLayout
import kotlin.math.roundToInt

private val GAP = 12.dp
private val PREVIEW_SHAPE = RoundedCornerShape(20.dp)

/**
 * Aperçus fantômes des pages, de gauche à droite : appui long puis glisser pour changer l'ordre,
 * œil en bas pour afficher ou masquer (sauf l'Accueil).
 */
@Composable
fun PagePreviews(
    order: List<PageKind>,
    isShown: (PageKind) -> Boolean,
    onToggle: (PageKind, Boolean) -> Unit,
    onMove: (PageKind, Int) -> Unit,
) {
    val pages = PageLayout.normalize(order)
    // Ordre de travail pendant le glissement ; recalé sur le réglage quand on ne glisse pas
    var working by remember { mutableStateOf(pages) }
    var dragging by remember { mutableStateOf<PageKind?>(null) }
    var startIndex by remember { mutableStateOf(0) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var slotPx by remember { mutableFloatStateOf(1f) }
    val latestOnMove by rememberUpdatedState(onMove)
    LaunchedEffect(pages) { if (dragging == null) working = pages }
    val gapPx = with(LocalDensity.current) { GAP.toPx() }

    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(GAP)) {
        working.forEachIndexed { index, kind ->
            key(kind) {
                val isDragging = kind == dragging
                val shown = isShown(kind)
                val scale by animateFloatAsState(if (isDragging) 1.06f else 1f, label = "scale")
                Column(
                    Modifier
                        .weight(1f)
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationX = if (isDragging) dragOffset else 0f
                            scaleX = scale; scaleY = scale
                        },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.5f)
                            .onSizeChanged { slotPx = it.width + gapPx }
                            .clip(PREVIEW_SHAPE)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .border(
                                1.dp,
                                if (isDragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                                PREVIEW_SHAPE,
                            )
                            .testTag("page-preview-${kind.name}")
                            .semantics {
                                contentDescription = "Page ${kind.label}, position ${index + 1}"
                                customActions = listOfNotNull(
                                    if (index > 0) CustomAccessibilityAction("Déplacer à gauche") { onMove(kind, -1); true } else null,
                                    if (index < working.lastIndex) CustomAccessibilityAction("Déplacer à droite") { onMove(kind, +1); true } else null,
                                )
                            }
                            .pointerInput(kind) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        dragging = kind; dragOffset = 0f
                                        startIndex = working.indexOf(kind)
                                    },
                                    onDragEnd = {
                                        val delta = working.indexOf(kind) - startIndex
                                        dragging = null; dragOffset = 0f
                                        if (delta != 0) latestOnMove(kind, delta)
                                    },
                                    onDragCancel = { dragging = null; dragOffset = 0f; working = pages },
                                ) { change, amount ->
                                    change.consume()
                                    dragOffset += amount.x
                                    val from = working.indexOf(kind)
                                    val to = (from + (dragOffset / slotPx).roundToInt()).coerceIn(0, working.lastIndex)
                                    if (to != from) {
                                        working = Reorder.move(working, from, to)
                                        dragOffset -= (to - from) * slotPx
                                    }
                                }
                            },
                    ) {
                        Box(Modifier.fillMaxSize().padding(8.dp).alpha(if (shown) 1f else 0.35f)) {
                            Ghost(kind, dashed = !shown)
                        }
                        if (kind == PageKind.HOME) {
                            Text("🏠", Modifier.align(Alignment.BottomCenter).padding(bottom = 10.dp))
                        } else {
                            IconButton(
                                onClick = { onToggle(kind, !shown) },
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 4.dp)
                                    .testTag("page-eye-${kind.name}")
                                    .semantics { contentDescription = (if (shown) "Masquer " else "Afficher ") + kind.label },
                            ) {
                                Box(
                                    Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (shown) MaterialTheme.colorScheme.primary
                                            else MaterialTheme.colorScheme.surfaceContainerHighest,
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    EyeIcon(
                                        crossed = !shown,
                                        color = if (shown) MaterialTheme.colorScheme.onPrimary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        kind.label,
                        style = MaterialTheme.typography.labelLarge,
                        textAlign = TextAlign.Center,
                        color = if (shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Œil dessiné à la main (les icônes de base n'ont pas de « visibilité »), barré quand la page est masquée. */
@Composable
private fun EyeIcon(crossed: Boolean, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = w * 0.09f, cap = StrokeCap.Round)
        val eye = Path().apply {
            moveTo(w * 0.06f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.05f, w * 0.94f, h * 0.5f)
            quadraticTo(w * 0.5f, h * 0.95f, w * 0.06f, h * 0.5f)
            close()
        }
        drawPath(eye, color, style = stroke)
        drawCircle(color, radius = w * 0.13f, center = Offset(w * 0.5f, h * 0.5f))
        if (crossed) {
            drawLine(color, Offset(w * 0.15f, h * 0.12f), Offset(w * 0.85f, h * 0.88f), strokeWidth = w * 0.09f, cap = StrokeCap.Round)
        }
    }
}

/** Formes grises qui évoquent le contenu de la page sans le montrer. */
@Composable
private fun BoxScope.Ghost(kind: PageKind, dashed: Boolean) {
    val tone = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val outline = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
    @Composable
    fun Bone(modifier: Modifier, shape: RoundedCornerShape = RoundedCornerShape(6.dp)) {
        Box(
            if (dashed) modifier.dashedBorder(outline, shape) else modifier.clip(shape).background(tone),
        )
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        when (kind) {
            PageKind.HOME -> {
                Bone(Modifier.fillMaxWidth(0.6f).height(10.dp))
                Bone(Modifier.fillMaxWidth().height(34.dp), RoundedCornerShape(10.dp))
                Spacer(Modifier.weight(1f))
                repeat(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(4) { Bone(Modifier.size(12.dp), CircleShape) }
                    }
                }
                Spacer(Modifier.height(30.dp))
            }
            PageKind.WIDGETS -> {
                Bone(Modifier.fillMaxWidth().height(40.dp), RoundedCornerShape(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Bone(Modifier.weight(1f).height(30.dp), RoundedCornerShape(10.dp))
                    Bone(Modifier.weight(1f).height(30.dp), RoundedCornerShape(10.dp))
                }
                Bone(Modifier.fillMaxWidth().height(24.dp), RoundedCornerShape(10.dp))
            }
            PageKind.NEWS -> {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { Bone(Modifier.width(12.dp).height(7.dp), CircleShape) }
                }
                Bone(Modifier.fillMaxWidth().height(36.dp), RoundedCornerShape(10.dp))
                Bone(Modifier.fillMaxWidth(0.85f).height(6.dp))
                Bone(Modifier.fillMaxWidth().height(20.dp), RoundedCornerShape(8.dp))
                Bone(Modifier.fillMaxWidth(0.7f).height(6.dp))
            }
        }
    }
}

private fun Modifier.dashedBorder(color: Color, shape: RoundedCornerShape): Modifier = drawBehind {
    val path = Path().apply { addOutline(shape.createOutline(size, layoutDirection, this@drawBehind)) }
    drawPath(path, color, style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))))
}
