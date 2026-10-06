package app.lanceur.builtin.weather

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
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

@Composable
fun WeatherCard(state: WeatherViewState, size: WidgetSize, onOpen: () -> Unit, onChooseCity: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val base = modifier.fillMaxSize().background(cardBackground())
    when (state) {
        WeatherViewState.Loading -> Message(base, tr("Chargement de la météo…", "Loading weather…"))
        WeatherViewState.Unavailable -> Message(base, tr("Météo indisponible, réessaie plus tard", "Weather unavailable, try again later"))
        WeatherViewState.NoPosition -> Column(base.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(tr("Position indisponible", "Location unavailable"), style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onChooseCity) { Text(tr("Choisir une ville", "Choose a city")) }
        }
        is WeatherViewState.Ready -> {
            val f = state.forecast
            Column(base.clickable(onClick = onOpen).padding(horizontal = 22.dp, vertical = 14.dp)) {
                CardLabel(state.placeName)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(f.icon, fontSize = 36.sp)
                    Spacer(Modifier.width(12.dp))
                    Text("${f.temp}°", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(f.condition, style = MaterialTheme.typography.titleMedium)
                        Text("Min ${f.min}° · Max ${f.max}°", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
                    }
                }
                if (size != WidgetSize.SMALL) {
                    Text(
                        listOfNotNull(tr("Ressenti ", "Feels like ") + "${f.apparent}°", tr("Vent ", "Wind ") + "${f.wind} km/h", f.rain?.let { tr("Pluie ", "Rain ") + "$it %" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        f.hours.forEach { h ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(h.label, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
                                Text(h.icon)
                                Text("${h.temp}°", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                if (size == WidgetSize.LARGE) {
                    Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        f.days.forEach { d ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(d.label, Modifier.width(52.dp), style = MaterialTheme.typography.bodyMedium)
                                Text(d.icon, Modifier.width(32.dp))
                                Text(d.rain?.let { "$it %" }.orEmpty(), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                Text("${d.min}° / ${d.max}°", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(state.freshness, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Message(modifier: Modifier, text: String) {
    Column(modifier.padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        CardLabel(tr("Météo", "Weather"))
        Spacer(Modifier.height(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
