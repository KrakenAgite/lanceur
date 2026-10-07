package app.lanceur.home

import app.lanceur.i18n.tr
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import app.lanceur.alphabet.AlphabetBar
import app.lanceur.alphabet.LetterIndex
import app.lanceur.alphabet.Scrub
import app.lanceur.alphabet.ScrubInput
import app.lanceur.alphabet.ScrubPhase
import app.lanceur.alphabet.letterIndex
import app.lanceur.apps.AppEntry
import app.lanceur.apps.AppKey
import app.lanceur.folders.FolderColumn
import app.lanceur.folders.vector
import androidx.compose.material3.Icon
import app.lanceur.prefs.AlphabetSide
import app.lanceur.prefs.AppLists
import app.lanceur.ui.AppMenuAction
import app.lanceur.ui.AppRow
import kotlin.math.roundToInt

private val HEADER_HEIGHT = 168.dp
private val BAR_WIDTH = 36.dp

@Composable
fun HomeScreen(
    lists: AppLists,
    mode: ListMode,
    side: AlphabetSide,
    actions: HomeActions,
    icon: @Composable (AppKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sections = remember(lists.allVisible) { LetterIndex.build(lists.allVisible) }
    val enabled = remember(sections) { sections.map { !it.isEmpty } }
    val favoriteKeys = remember(lists.favorites) { lists.favorites.mapTo(HashSet()) { it.key } }
    val shown = when (mode) {
        ListMode.Favorites -> lists.favorites
        is ListMode.Letter -> sections.firstOrNull { it.letter == mode.letter }?.apps.orEmpty()
        is ListMode.Folder -> lists.folders.firstOrNull { it.folder.id == mode.id }?.apps.orEmpty()
    }
    val openFolder = lists.folders.firstOrNull { it.folder.id == (mode as? ListMode.Folder)?.id }?.folder

    val act by rememberUpdatedState(actions)
    val currentMode by rememberUpdatedState(mode)
    val view = LocalView.current
    val density = LocalDensity.current
    val swipe = remember(density) { SwipeAccumulator(with(density) { 64.dp.toPx() }) }

    var phase by remember { mutableStateOf<ScrubPhase>(ScrubPhase.Idle) }
    var barTop by remember { mutableFloatStateOf(0f) }
    var folderPhase by remember { mutableStateOf<ScrubPhase>(ScrubPhase.Idle) }
    var foldersTop by remember { mutableFloatStateOf(0f) }
    /** Dossier ouvert quand le doigt s'est posé : le toucher à nouveau le referme. */
    var openAtDown by remember { mutableStateOf<Int?>(null) }
    var fingerY by remember { mutableFloatStateOf(0f) }
    var highlighted by remember { mutableStateOf<AppKey?>(null) }
    val rowBounds = remember { mutableMapOf<AppKey, Rect>() }

    fun handleSwipe(direction: Swipe?) {
        when (direction) {
            Swipe.UP -> if (currentMode == ListMode.Favorites) act.openSearch()
            Swipe.DOWN -> act.openNotifications()
            null -> Unit
        }
    }

    // Glissements qui partent de la liste : ce que la liste ne fait pas défiler remonte ici
    val swipeConnection = remember(swipe) {
        object : NestedScrollConnection {
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) handleSwipe(swipe.add(available.y))
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                swipe.reset()
                return Velocity.Zero
            }
        }
    }

    /** Parcours commun à l'alphabet et aux dossiers : [open] ouvre l'élément survolé. */
    fun scrub(before: ScrubPhase, input: ScrubInput, top: Float, enabled: List<Boolean>, open: (Int) -> Unit): ScrubPhase {
        val next = Scrub.next(before, input, enabled)
        fingerY = top + input.y
        when (next) {
            is ScrubPhase.OnBar -> {
                highlighted = null
                if (next.index != before.letterIndex) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    open(next.index)
                }
            }
            is ScrubPhase.OnList -> highlighted = shown.firstOrNull { entry ->
                rowBounds[entry.key]?.let { fingerY >= it.top && fingerY <= it.bottom } == true
            }?.key
            ScrubPhase.Idle -> highlighted = null
        }
        return next
    }

    val onScrub: (ScrubInput) -> Unit = { input ->
        phase = scrub(phase, input, barTop, enabled) { act.changeMode(ListMode.Letter(LetterIndex.LETTERS[it])) }
    }

    val folderList by rememberUpdatedState(lists.folders)
    val onFolderScrub: (ScrubInput) -> Unit = { input ->
        if (folderPhase == ScrubPhase.Idle) openAtDown = (currentMode as? ListMode.Folder)?.id
        folderPhase = scrub(folderPhase, input, foldersTop, List(folderList.size) { true }) { i ->
            folderList.getOrNull(i)?.let { act.changeMode(ListMode.Folder(it.folder.id)) }
        }
    }

    val onFolderRelease: (Boolean, Boolean) -> Unit = { cancelled, tap ->
        val target = highlighted?.let { key -> shown.firstOrNull { it.key == key } }
        val tapped = (folderPhase as? ScrubPhase.OnBar)?.let { folderList.getOrNull(it.index)?.folder?.id }
        folderPhase = ScrubPhase.Idle
        highlighted = null
        when {
            cancelled -> Unit
            target != null -> act.launch(target)
            tap && tapped != null && tapped == openAtDown -> act.changeMode(ListMode.Favorites)
        }
    }

    val onRelease: (Boolean) -> Unit = { cancelled ->
        val target = highlighted?.let { key -> shown.firstOrNull { it.key == key } }
        phase = ScrubPhase.Idle
        highlighted = null
        if (target != null && !cancelled) act.launch(target)
    }

    // Les dossiers occupent l'angle, au-dessus de l'alphabet
    val bar: @Composable () -> Unit = {
        Column(Modifier.width(BAR_WIDTH).fillMaxHeight()) {
            FolderColumn(
                folders = lists.folders,
                openId = openFolder?.id,
                activeIndex = (folderPhase as? ScrubPhase.OnBar)?.index,
                side = side,
                onScrub = onFolderScrub,
                onRelease = onFolderRelease,
                onLongPress = { act.editFolder(it) },
                modifier = Modifier
                    .padding(top = 8.dp)
                    .height(HEADER_HEIGHT - 8.dp)
                    .fillMaxWidth()
                    .onGloballyPositioned { foldersTop = it.positionInRoot().y },
            )
            AlphabetBar(
                sections = sections,
                phase = phase,
                side = side,
                onScrub = onScrub,
                onRelease = onRelease,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { barTop = it.positionInRoot().y },
            )
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .pointerInput(swipe) {
                // Chaque nouveau geste repart de zéro
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    swipe.reset()
                }
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(0f to Color.Transparent, 1f to MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                ),
        )
        Row(Modifier.fillMaxSize().systemBarsPadding()) {
            if (side == AlphabetSide.LEFT) bar()
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { if (currentMode != ListMode.Favorites) act.changeMode(ListMode.Favorites) },
                            onDoubleTap = { act.lockScreen() },
                            onLongPress = { act.openSettings() },
                        )
                    }
                    .pointerInput(swipe) {
                        detectVerticalDragGestures(onDragStart = { swipe.reset() }) { change, dragAmount ->
                            change.consume()
                            handleSwipe(swipe.add(dragAmount))
                        }
                    }
                    .nestedScroll(swipeConnection),
            ) {
                Clock(
                    onClockTap = { act.openClock() },
                    onClockLongPress = { act.openVault() },
                    onDateTap = { act.openCalendar() },
                    modifier = Modifier.height(HEADER_HEIGHT),
                )
                if (lists.focusActive) app.lanceur.focus.FocusBanner(until = lists.focusUntil, onStop = { act.stopFocus() })
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.Bottom,
                    contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 24.dp),
                ) {
                    if (openFolder != null) {
                        item(key = "folder-title") {
                            Text(
                                openFolder.name,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (shown.isEmpty()) {
                            item {
                                Text(
                                    tr("Dossier vide : appui long sur une appli › Ranger dans un dossier", "Empty folder: long press an app › Add to folder"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(16.dp),
                                )
                            }
                        }
                    }
                    if (shown.isEmpty() && mode == ListMode.Favorites) {
                        item {
                            Text(
                                tr("Appui long sur une appli pour l'ajouter aux favoris", "Long press an app to add it to favorites"),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp),
                            )
                        }
                    }
                    items(shown, key = { it.key.encode() }) { entry ->
                        DisposableEffect(entry.key) { onDispose { rowBounds.remove(entry.key) } }
                        AppRow(
                            entry = entry,
                            icon = icon,
                            menuItems = menuFor(entry, favoriteKeys, inFolder = openFolder != null),
                            highlighted = entry.key == highlighted,
                            onClick = { act.launch(entry) },
                            onMenu = { act.menu(entry, it) },
                            modifier = Modifier
                                .animateItem()
                                .onGloballyPositioned { rowBounds[entry.key] = it.boundsInRoot() },
                        )
                    }
                }
            }
            if (side == AlphabetSide.RIGHT) bar()
        }
        val active = phase
        val activeFolder = (folderPhase as? ScrubPhase.OnBar)?.let { lists.folders.getOrNull(it.index)?.folder }
        if (active is ScrubPhase.OnBar || activeFolder != null) {
            Bubble(
                modifier = Modifier
                    .align(if (side == AlphabetSide.RIGHT) Alignment.TopEnd else Alignment.TopStart)
                    .offset {
                        IntOffset(
                            x = (if (side == AlphabetSide.RIGHT) -1 else 1) * 72.dp.roundToPx(),
                            y = (fingerY - 40.dp.toPx()).roundToInt().coerceAtLeast(0),
                        )
                    },
            ) {
                if (activeFolder != null) {
                    Icon(activeFolder.icon.vector, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(40.dp))
                } else if (active is ScrubPhase.OnBar) {
                    Text(LetterIndex.LETTERS[active.index].toString(), style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }
    }
}

@Composable
private fun Bubble(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Box(
        modifier
            .size(80.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private fun menuFor(entry: AppEntry, favorites: Set<AppKey>, inFolder: Boolean) = listOfNotNull(
    if (entry.key in favorites) AppMenuAction.REMOVE_FAVORITE else AppMenuAction.ADD_FAVORITE,
    AppMenuAction.FOLDER,
    if (inFolder) AppMenuAction.REMOVE_FROM_FOLDER else null,
    AppMenuAction.HIDE,
    AppMenuAction.INFO,
    AppMenuAction.UNINSTALL,
)
