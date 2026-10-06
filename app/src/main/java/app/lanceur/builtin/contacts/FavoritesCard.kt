package app.lanceur.builtin.contacts

import app.lanceur.i18n.tr
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.lanceur.ui.CardLabel
import app.lanceur.ui.cardBackground
import app.lanceur.widgets.WidgetSize

class FavoritesActions(
    val call: (String) -> Unit = {},
    val sms: (String) -> Unit = {},
    val open: (String) -> Unit = {},
    val requestPermission: () -> Unit = {},
)

private const val PER_ROW = 4

@Composable
fun FavoritesCard(state: FavoritesState, size: WidgetSize, actions: FavoritesActions, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize().background(cardBackground()).padding(horizontal = 18.dp, vertical = 12.dp)) {
        CardLabel(tr("Favoris", "Favorites"), Modifier.padding(start = 4.dp))
        Spacer(Modifier.height(6.dp))
        when (state) {
            FavoritesState.NoPermission -> TextButton(onClick = actions.requestPermission) { Text(tr("Autoriser les contacts", "Allow contacts")) }
            is FavoritesState.Loaded -> if (state.contacts.isEmpty()) {
                Text(tr("Ajoute des favoris ⭐ dans Contacts", "Add favorites ⭐ in Contacts"), style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp))
            } else {
                val rows = if (size == WidgetSize.SMALL) 1 else 2
                state.contacts.take(rows * PER_ROW).chunked(PER_ROW).forEach { row ->
                    Row(Modifier.fillMaxWidth()) {
                        row.forEach { Bubble(it, actions, Modifier.weight(1f)) }
                        repeat(PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(contact: FavoriteContact, actions: FavoritesActions, modifier: Modifier) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = { contact.phone?.let(actions.call) ?: actions.open(contact.lookupUri) },
                onLongClick = { contact.phone?.let(actions.sms) ?: actions.open(contact.lookupUri) },
            )
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(colors.primaryContainer), contentAlignment = Alignment.Center) {
            if (contact.photo != null) Image(contact.photo, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            else Text(contact.initial, style = MaterialTheme.typography.titleLarge, color = colors.onPrimaryContainer)
        }
        Text(contact.firstName, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
