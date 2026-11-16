package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageNavigationIconInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.lyrics.LyricMatches
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.navigation.BackHandler
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun LyricUpgradeManagePage(onBackClick: () -> Unit, modifier: Modifier = Modifier,
    onEntryRemoved: (String) -> Unit = {}) {
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    BackHandler { onBackClick() }
    Scaffold(topBar = {
        BlurredBar(backdrop = backdrop) {
            SmallTopAppBar(title = "管理歌词匹配", color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                scrollBehavior = scrollBehavior, defaultWindowInsetsPadding = false,
                navigationIcon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", Modifier.padding(start = pageNavigationIconInset()).clickable { onBackClick() }) })
        }
    }) { padding ->
        LazyColumn(modifier = modifier.fillMaxSize().padding(top = pageViewportTopInset(padding.calculateTopPadding())).overScrollVertical()
            .nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()) + 12.dp, bottom = FeedBottomPads.None)) {
            items(LyricMatches.entries, key = { "${it.original.source}:${it.original.id}" }) { entry ->
                LyricTrackRow(entry.original, onDelete = { LyricMatches.remove(entry.original); onEntryRemoved(entry.original.id) })
            }
        }
    }
}
