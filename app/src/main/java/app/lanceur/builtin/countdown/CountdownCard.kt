package app.lanceur.builtin.countdown

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize
import java.time.ZonedDateTime

@Composable
fun CountdownCard(
    config: CountdownConfig?,
    size: WidgetSize,
    onSetUp: () -> Unit,
    modifier: Modifier = Modifier,
    now: ZonedDateTime = ZonedDateTime.now(),
) {
    val colors = MaterialTheme.colorScheme
    if (config == null) {
        Column(
            modifier.fillMaxSize().background(cardBackground()).padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CardLabel(tr("Compte à rebours", "Countdown"))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSetUp) { Text(tr("Régler le compte à rebours", "Set up the countdown")) }
            Spacer(Modifier.weight(1f))
        }
        return
    }
    val view = Countdown.of(config, now)
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 16.dp)) {
        CardLabel(config.title)
        Text(
            view.big,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(view.dateText, style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (size == WidgetSize.MEDIUM) {
            Spacer(Modifier.weight(1f))
            LinearProgressIndicator(progress = { view.progress }, modifier = Modifier.fillMaxWidth().height(6.dp))
        }
    }
}
