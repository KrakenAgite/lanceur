package app.lanceur.builtin.clocks

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun WorldClocksCard(faces: List<ClockFace>, size: WidgetSize, modifier: Modifier = Modifier) {
    val shown = if (size == WidgetSize.SMALL) faces.take(2) else faces.take(WorldClocksConfig.MAX)
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 14.dp)) {
        CardLabel(tr("Horloges", "Clocks"))
        Spacer(Modifier.height(6.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.SpaceEvenly) {
            shown.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    row.forEach { FaceCell(it, Modifier.weight(1f)) }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun FaceCell(face: ClockFace, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier) {
        Text(face.city.name, style = MaterialTheme.typography.labelLarge, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(face.time, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, color = colors.onSurface)
        Text(
            listOfNotNull("${if (face.isDay) "☀" else "☾"} ${face.offset}", face.dayHint).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            color = colors.onSurfaceVariant,
            maxLines = 1,
        )
    }
}
