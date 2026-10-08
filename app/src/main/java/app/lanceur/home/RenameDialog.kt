package app.lanceur.home

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import app.lanceur.i18n.tr

/** Nom affiché d'une appli ; [original] : le nom donné par l'appli, qu'on peut rétablir. */
@Composable
fun RenameDialog(current: String, original: String, onSave: (String) -> Unit, onReset: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("Renommer", "Rename")) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text(tr("Nom affiché", "Displayed name")) },
                supportingText = { Text(tr("Nom d'origine : $original", "Original name: $original")) },
                modifier = Modifier.testTag("rename-field"),
            )
        },
        confirmButton = {
            Button(onClick = { onSave(name) }, enabled = name.isNotBlank()) { Text(tr("Enregistrer", "Save")) }
        },
        dismissButton = {
            if (current != original) TextButton(onClick = onReset) { Text(tr("Nom d'origine", "Original name")) }
            else TextButton(onClick = onDismiss) { Text(tr("Annuler", "Cancel")) }
        },
    )
}
