package app.lanceur.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.home.ClockFont
import app.lanceur.home.ClockSize
import app.lanceur.home.ClockStyle
import app.lanceur.i18n.tr
import app.lanceur.ui.cardBackground

private val CHOICE = RoundedCornerShape(20.dp)

/** Style de l'horloge de l'accueil : police (aperçu dans chaque case), taille, deux lignes, date. */
@Composable
fun ClockSection(style: ClockStyle, onChange: (ClockStyle) -> Unit) {
    val colors = MaterialTheme.colorScheme
    Text(tr("Horloge", "Clock"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 16.dp))
    FlowRow(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 3,
    ) {
        ClockFont.entries.forEach { font ->
            val selected = font == style.font
            Column(
                Modifier
                    .weight(1f)
                    .clip(CHOICE)
                    .background(if (selected) colors.primaryContainer else cardBackground())
                    .clickable { onChange(style.copy(font = font)) }
                    .testTag("clock-font-${font.name}")
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "12:34",
                    style = ClockStyle(font = font).timeStyle(MaterialTheme.typography.headlineMedium).copy(fontSize = 28.sp, lineHeight = 32.sp),
                    color = if (selected) colors.onPrimaryContainer else colors.onSurface,
                )
                Text(
                    font.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Row(Modifier.padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ClockSize.entries.forEach { size ->
            FilterChip(
                selected = size == style.size,
                onClick = { onChange(style.copy(size = size)) },
                label = { Text(size.label) },
                modifier = Modifier.testTag("clock-size-${size.name}"),
            )
        }
    }
    SwitchRow(
        title = tr("Sur deux lignes", "On two lines"),
        subtitle = tr("Les heures au-dessus des minutes", "Hours above minutes"),
        checked = style.stacked,
        tag = "clock-stacked",
    ) { onChange(style.copy(stacked = it)) }
    SwitchRow(
        title = tr("Afficher la date", "Show the date"),
        subtitle = tr("Sous l'heure ; la toucher ouvre l'agenda", "Below the time; tap it to open the calendar"),
        checked = style.showDate,
        tag = "clock-date",
    ) { onChange(style.copy(showDate = it)) }
}
