package app.lanceur.builtin.shortcuts

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground

class ShortcutActions(
    val toggleTorch: (Boolean) -> Unit = {},
    val internet: () -> Unit = {},
    val bluetooth: () -> Unit = {},
    val sound: () -> Unit = {},
    val doNotDisturb: () -> Unit = {},
)

/** Seule la lampe change d'état ici ; les autres ouvrent le panneau d'Android (une appli ne peut pas les changer). */
@Composable
fun ShortcutsCard(torch: TorchState, actions: ShortcutActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 12.dp, vertical = 12.dp)) {
        CardLabel("Raccourcis", Modifier.padding(start = 10.dp))
        Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Shortcut("🔦", "Lampe", active = torch.on, enabled = torch.available) { actions.toggleTorch(!torch.on) }
            Shortcut("🌐", "Internet", onClick = actions.internet)
            Shortcut("🔵", "Bluetooth", onClick = actions.bluetooth)
            Shortcut("🔊", "Son", onClick = actions.sound)
            Shortcut("🌙", "Ne pas déranger", onClick = actions.doNotDisturb)
        }
    }
}

@Composable
private fun Shortcut(emoji: String, label: String, active: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Column(
        Modifier
            .size(width = 64.dp, height = 76.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.4f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(if (active) colors.primary else colors.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) { Text(emoji, fontSize = 20.sp) }
        Text(label, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, lineHeight = 12.sp)
    }
}
