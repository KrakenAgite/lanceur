package app.lanceur.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

/** Un écran superposé ne bloque pas les touchers tout seul : celui-ci les capte sans les consommer. */
fun Modifier.blockTouchesBelow(): Modifier = pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) awaitPointerEvent()
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
    )
}

@Composable
fun HintText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** Forme et fond des cartes de Lanceur (résumé du jour, widgets intégrés) : translucides, aux couleurs Material You. */
val CardShape = RoundedCornerShape(28.dp)

@Composable
fun cardBackground(): Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.82f)

/** Petit titre de carte : majuscules espacées, couleur primaire (« LUNDI », « BATTERIE »). */
@Composable
fun CardLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Text(
        text.uppercase(Locale.FRENCH),
        modifier = modifier,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 2.sp,
        maxLines = 1,
    )
}
