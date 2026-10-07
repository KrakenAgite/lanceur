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
import app.lanceur.alphabet.BarGeometry
import app.lanceur.alphabet.BarPhase
import app.lanceur.alphabet.BarScrub
import app.lanceur.folders.folderPitch
import app.lanceur.alphabet.LetterIndex
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
private val FOLDERS_TOP = 8.dp

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

    var barPhase by remember { mutableStateOf<BarPhase>(BarPhase.Idle) }
    var barTop by remember { mutableFloatStateOf(0f) }
    var alphabetHeight by remember { mutableFloatStateOf(0f) }
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

    val folderList by rememberUpdatedState(lists.folders)
    val currentSide by rememberUpdatedState(side)
    val currentEnabled by rememberUpdatedState(enabled)

    fun geometry(width: Float) = with(density) {
        BarGeometry(
            width = width,
            folderCount = folderList.size,
            folderTop = FOLDERS_TOP.toPx(),
            folderPitch = folderPitch(HEADER_HEIGHT - FOLDERS_TOP, folderList.size).toPx(),
            alphabetTop = HEADER_HEIGHT.toPx(),
            alphabetHeight = alphabetHeight,
            side = currentSide,
            inwardThresholdPx = 24.dp.toPx(),
        )
    }

    // Un seul parcours pour les dossiers et l'alphabet : le doigt passe de l'un à l'autre sans se lever
    fun scrubTo(x: Float, y: Float, width: Float) {
        val before = barPhase
        val previous = (before as? BarPhase.OnList)?.from ?: before
        val next = BarScrub.next(before, x, y, geometry(width), currentEnabled)
        fingerY = barTop + y
        when (next) {
            is BarPhase.OnFolder -> {
                highlighted = null
                if (next != previous) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    folderList.getOrNull(next.index)?.let { act.changeMode(ListMode.Folder(it.folder.id)) }
                }
            }
            is BarPhase.OnLetter -> {
                highlighted = null
                if ((previous as? BarPhase.OnLetter)?.index != next.index) {
                    view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                    act.changeMode(ListMode.Letter(LetterIndex.LETTERS[next.index]))
                }
            }
            is BarPhase.OnList -> highlighted = shown.firstOrNull { entry ->
                rowBounds[entry.key]?.let { fingerY >= it.top && fingerY <= it.bottom } == true
            }?.key
            BarPhase.Idle -> highlighted = null
        }
        barPhase = next
    }

    /** `cancelled` : geste annulé par le système (écran éteint, appel…) ou appui long, rien ne doit s'ouvrir. */
    fun release(cancelled: Boolean, tap: Boolean) {
        val target = highlighted?.let { key -> shown.firstOrNull { it.key == key } }
        val tapped = (barPhase as? BarPhase.OnFolder)?.let { folderList.getOrNull(it.index)?.folder?.id }
        barPhase = BarPhase.Idle
        highlighted = null
        when {
            cancelled -> Unit
            target != null -> act.launch(target)
            tap && tapped != null && tapped == openAtDown -> act.changeMode(ListMode.Favorites)
        }
    }

    // Des lambdas et non des références (`::scrubTo`) : deux références à la même fonction locale sont égales,
    // rememberUpdatedState garderait la première, avec la liste affichée au premier rendu
    val latestScrub by rememberUpdatedState { x: Float, y: Float, width: Float -> scrubTo(x, y, width) }
    val latestRelease by rememberUpdatedState { cancelled: Boolean, tap: Boolean -> release(cancelled, tap) }
    val latestGeometry by rememberUpdatedState { width: Float -> geometry(width) }

    // Les dossiers occupent l'angle, au-dessus de l'alphabet
    val bar: @Composable () -> Unit = {
        Column(
            Modifier
                .width(BAR_WIDTH)
                .fillMaxHeight()
                .onGloballyPositioned { barTop = it.positionInRoot().y }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val width = size.width.toFloat()
                        if (!BarScrub.startsOnBar(down.position.y, latestGeometry(width))) return@awaitEachGesture
                        down.consume()
                        val onFolders = down.position.y < HEADER_HEIGHT.toPx()
                        openAtDown = (currentMode as? ListMode.Folder)?.id
                        latestScrub(down.position.x, down.position.y, width)
                        var moved = false
                        var longPressed = false
                        var cancelled = true
                        while (true) {
                            // Appui long sans bouger sur un dossier : on le modifie
                            val event = if (onFolders && !moved && !longPressed) {
                                withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                longPressed = true
                                (barPhase as? BarPhase.OnFolder)?.let { folderList.getOrNull(it.index) }?.let { act.editFolder(it.folder) }
                                continue
                            }
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) {
                                // Une annulation arrive comme un lever déjà consommé
                                cancelled = change.isConsumed || longPressed
                                break
                            }
                            change.consume()
                            if (longPressed) continue
                            if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            latestScrub(change.position.x, change.position.y, width)
                        }
                        latestRelease(cancelled, !moved && !longPressed)
                    }
                },
        ) {
            FolderColumn(
                folders = lists.folders,
                openId = openFolder?.id,
                activeIndex = BarScrub.folderIndex(barPhase),
                modifier = Modifier
                    .padding(top = FOLDERS_TOP)
                    .height(HEADER_HEIGHT - FOLDERS_TOP)
                    .fillMaxWidth(),
            )
            AlphabetBar(
                sections = sections,
                phase = BarScrub.letterPhase(barPhase),
                side = side,
                modifier = Modifier
                    .padding(bottom = 24.dp)
                    .weight(1f)
                    .fillMaxWidth()
                    .onGloballyPositioned { alphabetHeight = it.size.height.toFloat() },
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
        val active = barPhase
        val activeFolder = (active as? BarPhase.OnFolder)?.let { lists.folders.getOrNull(it.index)?.folder }
        if (active is BarPhase.OnLetter || activeFolder != null) {
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
                } else if (active is BarPhase.OnLetter) {
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
