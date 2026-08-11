package com.cloudmusic.miuix.ui.pages

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.viewmodel.MusicPlayerViewModel
import com.cloudmusic.miuix.viewmodel.RecommendState
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun RecommendPage(
    recommendState: RecommendState,
    onPlaylistClick: (String, String, String) -> Unit,
    viewModel: MusicPlayerViewModel,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val searchState = viewModel.searchState

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadRecommendPlaylists()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = "推荐",
                largeTitle = "推荐",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                bottomContent = {
                    SearchBar(
                        modifier = Modifier.padding(vertical = 8.dp),
                        inputField = {
                            InputField(
                                query = searchText,
                                onQueryChange = { searchText = it },
                                onSearch = {
                                    if (searchText.isNotEmpty()) {
                                        viewModel.searchSongs(searchText)
                                    }
                                },
                                expanded = expanded,
                                onExpandedChange = { expanded = it },
                                label = "搜索音乐"
                            )
                        },
                        expanded = expanded,
                        onExpandedChange = { expanded = it },
                        outsideEndAction = {
                            Text(
                                modifier = Modifier
                                    .padding(end = 12.dp)
                                    .clickable(
                                        interactionSource = null,
                                        indication = null
                                    ) {
                                        expanded = false
                                        searchText = ""
                                        viewModel.clearSearch()
                                    },
                                text = "取消",
                                color = MiuixTheme.colorScheme.primary
                            )
                        }
                    ) {
                        // 搜索结果内容 - 展开时覆盖全屏
                        if (searchState.isLoading) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                InfiniteProgressIndicator()
                            }
                        } else if (searchState.error != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "搜索失败: ${searchState.error}",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        } else if (searchState.results.isNotEmpty()) {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                items(searchState.results) { track ->
                                    TrackItem(
                                        track = track,
                                        isPlaying = track.id == viewModel.playerState.currentTrack?.id,
                                        onClick = {
                                            viewModel.currentPlaylist = searchState.results
                                            viewModel.playTrack(track)
                                        }
                                    )
                                }
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "输入关键词搜索音乐",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing,
            onRefresh = { isRefreshing = true },
            pullToRefreshState = pullToRefreshState,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf(
                "继续下拉以刷新",
                "松手后刷新",
                "正在刷新...",
                "刷新成功"
            ),
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
        ) {
            if (recommendState.isLoading && !isRefreshing) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    InfiniteProgressIndicator()
                }
            } else if (recommendState.error != null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载失败: ${recommendState.error}",
                        fontSize = MiuixTheme.textStyles.body2.fontSize,
                        color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalStaggeredGrid(
                    columns = StaggeredGridCells.Adaptive(160.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalItemSpacing = 12.dp
                ) {
                    items(recommendState.playlists) { playlist ->
                        RecommendPlaylistCard(
                            playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        title = track.title,
        titleColor = if (isPlaying) {
            BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
        } else {
            BasicComponentDefaults.titleColor()
        },
        summary = "${track.artist} - ${track.album}",
        summaryColor = if (isPlaying) {
            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f))
        } else {
            BasicComponentDefaults.summaryColor()
        },
        endActions = {
            if (track.isVip) {
                Text(
                    text = "VIP",
                    fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .background(
                            color = MiuixTheme.colorScheme.primary,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = formatDuration(track.duration),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
        },
        onClick = onClick
    )
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun RecommendPlaylistCard(
    playlist: RecommendPlaylist,
    onClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier
) {
    with(sharedTransitionScope) {
        Card(
            modifier = modifier.fillMaxWidth(),
            pressFeedbackType = PressFeedbackType.Tilt,
            showIndication = true,
            onClick = onClick
        ) {
            AsyncImage(
                model = playlist.coverImgUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "playlist_cover_${playlist.id}"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )

            Text(
                text = playlist.title,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            if (playlist.playCount > 0) {
                Text(
                    text = formatPlayCount(playlist.playCount),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        }
    }
}

private fun formatPlayCount(count: Long): String {
    return when {
        count >= 100_000_000 -> "%.1f亿播放".format(count / 100_000_000.0)
        count >= 10_000 -> "%.1f万播放".format(count / 10_000.0)
        else -> "$count 播放"
    }
}
