package com.hypermusic.miuix.ui.components

import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map

internal val LocalMainTabActive = staticCompositionLocalOf { true }

/** Recommendation, library and settings are sibling pages, never a push/pop stack. */
@Composable
@OptIn(androidx.compose.animation.ExperimentalSharedTransitionApi::class)
internal fun MainTabPager(selectedTab: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier,
    content: @Composable (Int) -> Unit) {
    val pager = rememberPagerState(initialPage = selectedTab.coerceIn(0, 2), pageCount = { 3 })
    val currentSelected by rememberUpdatedState(selectedTab)
    val changeSelected by rememberUpdatedState(onSelected)
    val stateHolder = rememberSaveableStateHolder()
    val coverTransition = LocalCoverTransitionScope.current
    var synchronizing by remember { mutableStateOf(false) }
    LaunchedEffect(selectedTab) {
        synchronizing = true
        try {
            if (pager.settledPage != selectedTab || pager.isScrollInProgress)
                pager.animateScrollToPage(selectedTab.coerceIn(0, 2), animationSpec = tween(300))
        } finally { synchronizing = false }
    }
    LaunchedEffect(pager) {
        snapshotFlow { Triple(pager.settledPage, pager.isScrollInProgress, synchronizing) }
            .filter { !it.second && !it.third }
            .map { it.first }.distinctUntilChanged().collect { page ->
                if (page != currentSelected) changeSelected(page)
            }
    }
    HorizontalPager(state = pager, modifier = modifier.fillMaxSize().testTag("main-tab-pager"),
        key = { it }, beyondViewportPageCount = 0) { page ->
        // Prefetched pages must not register a second source for the same playlist-cover key.
        CompositionLocalProvider(LocalMainTabActive provides (page == pager.currentPage),
            LocalCoverTransitionScope provides if (page == pager.currentPage) coverTransition else null) {
            stateHolder.SaveableStateProvider(page) { content(page) }
        }
    }
}
