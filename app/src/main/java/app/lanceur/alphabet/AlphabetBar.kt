package app.lanceur.alphabet

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.prefs.AlphabetSide
import kotlin.math.abs

/**
 * Colonne de lettres de même hauteur. Elle dessine la vague : les lettres proches du doigt grossissent et se
 * décalent vers l'intérieur de l'écran. Le geste est géré avec les dossiers, par la colonne entière (`BarScrub`).
 */
@Composable
fun AlphabetBar(
    sections: List<LetterSection>,
    phase: ScrubPhase,
    side: AlphabetSide,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val shiftPx = with(density) { WaveMath.MAX_SHIFT_DP.dp.toPx() }

    var lastUnits by remember { mutableFloatStateOf(0f) }
    SideEffect { if (phase is ScrubPhase.OnBar) lastUnits = phase.fingerUnits }
    val intensity by animateFloatAsState(
        targetValue = if (phase is ScrubPhase.OnBar) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "vague",
    )
    val activeIndex = (phase as? ScrubPhase.OnBar)?.index
    val direction = if (side == AlphabetSide.RIGHT) -1f else 1f
    val colors = MaterialTheme.colorScheme

    Column(
        modifier.testTag("alphabet"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        sections.forEachIndexed { i, section ->
            val color = when {
                i == activeIndex -> colors.primary
                section.isEmpty -> colors.onSurface.copy(alpha = 0.3f)
                else -> colors.onSurface.copy(alpha = 0.85f)
            }
            Text(
                text = section.letter.toString(),
                color = color,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (i == activeIndex) FontWeight.Bold else null,
                modifier = Modifier
                    .weight(1f)
                    .wrapContentHeight()
                    .graphicsLayer {
                        val f = WaveMath.factor(abs(i - lastUnits)) * intensity
                        val s = 1f + WaveMath.MAX_EXTRA_SCALE * f
                        scaleX = s
                        scaleY = s
                        translationX = direction * shiftPx * f
                    },
            )
        }
    }
}
