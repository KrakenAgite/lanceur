package app.lanceur.builtin.storage

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

@Composable
fun StorageCard(reading: StorageReading, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(cardBackground()).clickable(onClick = onClick).padding(horizontal = 22.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        CardLabel(tr("Stockage", "Storage"))
        GaugeRow(tr("Stockage", "Storage"), StorageInfo.text(reading.storage, decimals = 0), reading.storage)
        GaugeRow(tr("Mémoire vive", "RAM"), StorageInfo.text(reading.memory, decimals = 1), reading.memory)
    }
}

@Composable
private fun GaugeRow(label: String, value: String, gauge: Gauge) {
    val colors = MaterialTheme.colorScheme
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.onSurface)
            Spacer(Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
        }
        LinearProgressIndicator(
            progress = { gauge.fraction },
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(6.dp),
            color = if (gauge.warning) Color(0xFFF9AB00) else colors.primary,
        )
    }
}
