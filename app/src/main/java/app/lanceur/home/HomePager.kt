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
 * Page de widgets à gauche, accueil à droite ; une seule page si les widgets sont désactivés. Un geste qui commence
 * sur l'alphabet ne fait jamais tourner la page : la barre consomme le toucher dès qu'on la pose.
 */
@Composable
fun HomePager(
    widgetsEnabled: Boolean,
    homePageRequests: Int,
    editMode: Boolean,
    onExitEdit: () -> Unit,
    onWidgetsShown: () -> Unit,
    widgetPage: @Composable () -> Unit,
    home: @Composable () -> Unit,
    /** `false` quand un écran est superposé (sélecteur…) : le geste retour lui appartient. */
    backEnabled: Boolean = true,
) {
    // Changer le nombre de pages recrée l'état : on repart toujours de l'accueil
    key(widgetsEnabled) {
        val pageCount = if (widgetsEnabled) 2 else 1
        val homeIndex = pageCount - 1
        val state = rememberPagerState(initialPage = homeIndex) { pageCount }
        val scope = rememberCoroutineScope()
        val latestShown by rememberUpdatedState(onWidgetsShown)

        // Seules les nouvelles demandes comptent (pas la valeur présente à la création), et toujours exécutées :
        // `currentPage` peut encore valoir l'accueil pendant un élan qui part vers les widgets
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
            snapshotFlow { state.settledPage }.collect { if (widgetsEnabled && it == 0) latestShown() }
        }
        BackHandler(enabled = backEnabled && widgetsEnabled && state.currentPage == 0) {
            if (editMode) onExitEdit() else scope.launch { state.animateScrollToPage(homeIndex) }
        }

        HorizontalPager(
            state = state,
            modifier = Modifier.fillMaxSize().testTag("pager"),
            beyondViewportPageCount = 1,
            // Pas de doigt pour interrompre le retour à l'accueil
            userScrollEnabled = !editMode && !returningHome,
            key = { page -> if (widgetsEnabled && page == 0) "widgets" else "home" },
        ) { page ->
            if (widgetsEnabled && page == 0) widgetPage() else home()
        }
    }
}
