package app.lanceur.builtin.note

import app.lanceur.i18n.tr
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import kotlinx.coroutines.delay

/** Note modifiable sur place ; enregistrée `saveDelayMs` après la dernière frappe. */
@Composable
fun NoteCard(initial: String, onSave: (String) -> Unit, modifier: Modifier = Modifier, saveDelayMs: Long = 500) {
    var text by remember { mutableStateOf(initial) }
    val latestSave by rememberUpdatedState(onSave)
    LaunchedEffect(text) {
        if (text != initial) {
            delay(saveDelayMs)
            latestSave(text)
        }
    }
    // Carte qui disparaît avant la fin du délai (Accueil, mode édition) : on enregistre tout de suite
    val latestText by rememberUpdatedState(text)
    DisposableEffect(Unit) {
        onDispose { if (latestText != initial) latestSave(latestText) }
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 22.dp, vertical = 18.dp)) {
        CardLabel(tr("Note", "Note"))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxSize().testTag("note-field"),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                cursorBrush = SolidColor(colors.primary),
            )
            if (text.isEmpty()) {
                Text(tr("Touche pour écrire…", "Tap to write…"), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
            }
        }
    }
}
