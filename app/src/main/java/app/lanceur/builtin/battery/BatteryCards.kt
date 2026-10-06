package app.lanceur.builtin.battery

import app.lanceur.i18n.tr
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

internal fun BatteryTone.color(): Color = when (this) {
    BatteryTone.GOOD -> Color(0xFF5BB974)
    BatteryTone.MEDIUM -> Color(0xFFF9AB00)
    BatteryTone.LOW -> Color(0xFFEE675C)
}

/** En charge, la couleur respire doucement ; sinon elle reste pleine. */
@Composable
private fun chargePulse(info: BatteryInfo): Float {
    if (info.status != ChargeStatus.CHARGING) return 1f
    val transition = rememberInfiniteTransition(label = "charge")
    val alpha by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "pulsation")
    return alpha
}

@Composable
fun BatteryRingCard(info: BatteryInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tone = info.tone.color()
    val track = colors.surfaceContainerHighest
    val sweep by animateFloatAsState(info.level * 3.6f, label = "niveau")
    val pulse = chargePulse(info)
    Row(
        modifier
            .fillMaxSize()
            .background(cardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(104.dp).drawBehind {
                val stroke = 10.dp.toPx()
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(stroke / 2, stroke / 2)
                drawArc(track, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(stroke))
                drawArc(
                    tone.copy(alpha = pulse), -90f, sweep, useCenter = false, topLeft = topLeft, size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            },
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${info.level} %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (info.status == ChargeStatus.CHARGING) Text("⚡", style = MaterialTheme.typography.labelLarge)
            }
        }
        Spacer(Modifier.width(20.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CardLabel(tr("Batterie", "Battery"))
            Text(info.statusText, style = MaterialTheme.typography.titleMedium)
            info.temperatureText?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant) }
            Text(info.powerSaveText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
fun BatteryBarCard(info: BatteryInfo, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val fill by animateFloatAsState(info.level / 100f, label = "niveau")
    val pulse = chargePulse(info)
    Row(
        modifier
            .fillMaxSize()
            .background(cardBackground())
            .clickable(onClick = onClick)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Pile dessinée : corps bordé, remplissage à la couleur du niveau, petit téton à droite
        Box(
            Modifier
                .size(width = 64.dp, height = 28.dp)
                .border(2.dp, colors.onSurfaceVariant, RoundedCornerShape(7.dp))
                .padding(4.dp),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fill.coerceIn(0.04f, 1f))
                    .clip(RoundedCornerShape(3.dp))
                    .background(info.tone.color().copy(alpha = pulse)),
            )
        }
        Box(Modifier.padding(start = 2.dp).size(width = 4.dp, height = 12.dp).clip(RoundedCornerShape(2.dp)).background(colors.onSurfaceVariant))
        Spacer(Modifier.width(16.dp))
        Text("${info.level} %", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        info.shortChargeText?.let { Text(it, style = MaterialTheme.typography.titleMedium, color = colors.onSurfaceVariant) }
    }
}
