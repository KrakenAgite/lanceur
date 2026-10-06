package app.lanceur.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.launch

/**
 * Pages dans l'ordre choisi (1 à 3), démarrage sur l'accueil. Un geste qui commence sur l'alphabet ne fait jamais
 * tourner la page : la barre consomme le toucher dès qu'on la pose.
 */
@Composable
fun HomePager(
    pages: List<PageKind>,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onPageShown: (PageKind) -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
    newsPage: @Composable () -> Unit = {},
    /** `false` quand un écran est superposé (sélecteur…) : le geste retour lui appartient. */
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    // Changer les pages recrée l'état : on repart toujours de l'accueil
    key(pages) {
        val homeIndex = pages.indexOf(PageKind.HOME).coerceAtLeast(0)
        val state = rememberPagerState(initialPage = homeIndex) { pages.size }
        val scope = rememberCoroutineScope()
        val latestShown by rememberUpdatedState(onPageShown)

        // Seules les nouvelles demandes comptent (pas la valeur présente à la création), et toujours exécutées :
        // `currentPage` peut encore valoir l'accueil pendant un élan qui part vers une autre page
        var handledRequest by remember { mutableIntStateOf(homePageRequests) }
        var returningHome by remember { mutableStateOf(false) }
        LaunchedEffect(homePageRequests) {
            if (homePageRequests == handledRequest) return@LaunchedEffect
            handledRequest = homePageRequests
            returningHome = true
            try {
                state.animateScrollToPage(homeIndex)
            } finally {
                returningHome = false
            }
        }
        LaunchedEffect(state) {
            snapshotFlow { state.settledPage }.collect { index -> pages.getOrNull(index)?.takeIf { it != PageKind.HOME }?.let(latestShown) }
        }
        BackHandler(enabled = backEnabled && state.currentPage != homeIndex) {
            if (editMode && pages.getOrNull(state.currentPage) == PageKind.WIDGETS) onExitEdit()
            else scope.launch { state.animateScrollToPage(homeIndex) }
        }

        HorizontalPager(
            state = state,
            modifier = modifier.fillMaxSize().testTag("pager"),
            beyondViewportPageCount = 1,
            // Pas de doigt pour interrompre le retour à l'accueil
            userScrollEnabled = !editMode && !returningHome,
            key = { index -> pages[index].name },
        ) { index ->
            when (pages[index]) {
                PageKind.WIDGETS -> widgetPage()
                PageKind.NEWS -> newsPage()
                PageKind.HOME -> home()
            }
        }
    }
}

/** Ancienne forme : page de widgets à gauche de l'accueil, ou accueil seul. */
@Composable
fun HomePager(
    widgetsEnabled: Boolean,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onWidgetsShown: () -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
    backEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) = HomePager(
    pages = if (widgetsEnabled) listOf(PageKind.WIDGETS, PageKind.HOME) else listOf(PageKind.HOME),
    homePageRequests = homePageRequests,
    editMode = editMode,
    onExitEdit = onExitEdit,
    onPageShown = { if (it == PageKind.WIDGETS) onWidgetsShown() },
    widgetPage = widgetPage,
    home = home,
    backEnabled = backEnabled,
    modifier = modifier,
)
