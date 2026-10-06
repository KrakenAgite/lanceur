package app.lanceur.builtin

import app.lanceur.i18n.tr
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import app.lanceur.builtin.clocks.Cities
import app.lanceur.builtin.clocks.WorldClocksConfig
import app.lanceur.builtin.clocks.WorldClocksSettings
import app.lanceur.builtin.countdown.CountdownConfig
import app.lanceur.builtin.countdown.CountdownSettings
import app.lanceur.builtin.rss.FeedCheck
import app.lanceur.builtin.rss.RssData
import app.lanceur.builtin.rss.RssSettings
import app.lanceur.builtin.weather.WeatherData
import app.lanceur.builtin.weather.WeatherSettings

/** Ce dont les feuilles ont besoin hors d'elles-mêmes (réseau, autorisation de localisation). */
class BuiltinSettingsServices(
    val searchPlaces: suspend (String) -> List<app.lanceur.builtin.weather.Place> = { emptyList() },
    val locationGranted: Boolean = false,
    val requestLocation: () -> Unit = {},
    val checkFeed: suspend (String) -> FeedCheck = { FeedCheck.Failed(tr("Indisponible", "Unavailable")) },
)

/** Feuille de réglages d'un widget configurable ; `initial` vaut `null` à l'ajout. Annuler n'enregistre rien. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BuiltinSettingsSheet(
    kind: BuiltinKind,
    initial: String?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
    services: BuiltinSettingsServices = BuiltinSettingsServices(),
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        when (kind) {
            BuiltinKind.COUNTDOWN -> CountdownSettings(CountdownConfig.fromData(initial), onSave)
            BuiltinKind.WORLD_CLOCKS -> WorldClocksSettings(WorldClocksConfig.fromData(initial), Cities.home(), onSave)
            BuiltinKind.WEATHER -> WeatherSettings(WeatherData.config(initial), services.searchPlaces, services.locationGranted, services.requestLocation, onSave)
            BuiltinKind.RSS -> RssSettings(RssData.config(initial), services.checkFeed, onSave)
            else -> Unit
        }
    }
}
