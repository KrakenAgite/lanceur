package app.lanceur.builtin

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.countdown.CountdownSettings

/** Feuille de réglages d'un widget configurable ; `initial` vaut `null` à l'ajout. Annuler n'enregistre rien. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltinSettingsSheet(kind: BuiltinKind, initial: String?, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        when (kind) {
            BuiltinKind.COUNTDOWN -> CountdownSettings(CountdownConfig.fromData(initial), onSave)
            else -> Unit
        }
    }
}
