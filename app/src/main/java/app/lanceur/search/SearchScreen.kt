package app.lanceur.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.lanceur.apps.AppKey
import app.lanceur.home.Swipe
import app.lanceur.home.SwipeAccumulator
import app.lanceur.ui.AppRow
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

class SearchActions(
    val queryChange: (String) -> Unit = {},
    val open: (SearchResult) -> Unit = {},
    val call: (String) -> Unit = {},
    val sms: (String) -> Unit = {},
    val requestPermissions: () -> Unit = {},
    val dismissHint: () -> Unit = {},
    val close: () -> Unit = {},
)

private val DAY = DateTimeFormatter.ofPattern("d", Locale.FRENCH)
private val DATE_TIME = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.FRENCH)
private val DATE_ONLY = DateTimeFormatter.ofPattern("EEE d MMM", Locale.FRENCH)

/** Résultats affichés de bas en haut : le meilleur est juste au-dessus du champ, à portée du pouce. */
@Composable
fun SearchScreen(
    query: String,
    results: List<SearchResult>,
    icon: @Composable (AppKey) -> Unit,
    actions: SearchActions,
    modifier: Modifier = Modifier,
) {
    val act by rememberUpdatedState(actions)
    val density = LocalDensity.current
    val swipe = remember(density) { SwipeAccumulator(with(density) { 64.dp.toPx() }) }
    val connection = remember(swipe) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput && swipe.add(available.y) == Swipe.DOWN) act.close()
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                swipe.reset()
                return Velocity.Zero
            }
        }
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .pointerInput(swipe) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    swipe.reset()
                }
            }
            .pointerInput(swipe) {
                detectVerticalDragGestures { _, dragAmount -> if (swipe.add(dragAmount) == Swipe.DOWN) act.close() }
            }
            .nestedScroll(connection)
            .systemBarsPadding()
            .imePadding()
            .padding(horizontal = 12.dp),
    ) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            reverseLayout = true,
            contentPadding = PaddingValues(vertical = 8.dp),
        ) {
            items(results, key = ::resultKey) { result -> ResultRow(result, icon, act) }
        }
        TextField(
            value = query,
            onValueChange = { act.queryChange(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .focusRequester(focus)
                .testTag("search-field"),
            placeholder = { Text("Applis, web, agenda, contacts…") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = {
                results.firstOrNull { it != SearchResult.PermissionHint }?.let { act.open(it) }
            }),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
    }
}

private fun resultKey(result: SearchResult): String = when (result) {
    is SearchResult.App -> "app:" + result.entry.key.encode()
    is SearchResult.Calc -> "calc"
    is SearchResult.Contact -> "contact:" + result.lookupUri
    is SearchResult.Event -> "event:${result.eventId}:${result.begin}"
    is SearchResult.Setting -> "setting:" + result.action
    is SearchResult.Web -> "web"
    SearchResult.PermissionHint -> "hint"
}

@Composable
private fun ResultRow(result: SearchResult, icon: @Composable (AppKey) -> Unit, actions: SearchActions) {
    when (result) {
        is SearchResult.App ->
            AppRow(result.entry, icon, menuItems = emptyList(), onClick = { actions.open(result) }, onMenu = {})
        is SearchResult.Calc -> ResultLine(
            badge = { TextBadge("=") },
            title = "= ${result.value}",
            subtitle = "${result.expression} · toucher pour copier",
            onClick = { actions.open(result) },
        )
        is SearchResult.Contact -> ResultLine(
            badge = { TextBadge(result.name.take(1).uppercase()) },
            title = result.name,
            subtitle = result.phone,
            onClick = { actions.open(result) },
        ) {
            result.phone?.let { phone ->
                IconButton(onClick = { actions.call(phone) }) { Icon(Icons.Default.Call, contentDescription = "Appeler") }
                IconButton(onClick = { actions.sms(phone) }) { Icon(Icons.Default.Email, contentDescription = "SMS") }
            }
        }
        is SearchResult.Event -> {
            val start = Instant.ofEpochMilli(result.begin).atZone(if (result.allDay) ZoneOffset.UTC else ZoneId.systemDefault())
            ResultLine(
                badge = { TextBadge(DAY.format(start)) },
                title = result.title.ifBlank { "(Sans titre)" },
                subtitle = listOfNotNull((if (result.allDay) DATE_ONLY else DATE_TIME).format(start), result.location).joinToString(" · "),
                onClick = { actions.open(result) },
            )
        }
        is SearchResult.Setting -> ResultLine(
            badge = { IconBadge(Icons.Default.Settings) },
            title = result.label,
            subtitle = "Réglages",
            onClick = { actions.open(result) },
        )
        is SearchResult.Web -> ResultLine(
            badge = { IconBadge(Icons.Default.Search) },
            title = "Rechercher « ${result.query} » sur le web",
            subtitle = null,
            onClick = { actions.open(result) },
        )
        SearchResult.PermissionHint -> ResultLine(
            badge = { IconBadge(Icons.Default.Lock) },
            title = "Autoriser l'accès à l'agenda et aux contacts",
            subtitle = "Pour les retrouver dans la recherche",
            onClick = actions.requestPermissions,
        ) {
            IconButton(onClick = actions.dismissHint) { Icon(Icons.Default.Clear, contentDescription = "Ne plus afficher") }
        }
    }
}

@Composable
private fun ResultLine(
    badge: @Composable () -> Unit,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        badge()
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing()
    }
}

@Composable
private fun TextBadge(text: String) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun IconBadge(icon: ImageVector) {
    Box(
        Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}
