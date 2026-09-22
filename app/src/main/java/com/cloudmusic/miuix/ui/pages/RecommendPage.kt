package com.cloudmusic.miuix.ui.pages

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.viewmodel.MusicPlayerViewModel
import com.cloudmusic.miuix.viewmodel.RecommendState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.InputField
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SearchBar
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import android.content.Context

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RecommendPage(
    recommendState: RecommendState,
    onPlaylistClick: (String, String, String) -> Unit,
    viewModel: MusicPlayerViewModel,
    isLoggedIn: Boolean,
    isPlayerPageVisible: Boolean = false,
    onOpenPlayerPage: () -> Unit = {},
    onIntelligenceMode: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var searchText by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()
    val searchState = viewModel.searchState
    val context = LocalContext.current
    val searchHistory = remember { mutableStateListOf<String>() }
    val prefs = context.getSharedPreferences("search_history", Context.MODE_PRIVATE)

    // 当播放页显示时，关闭搜索页
    LaunchedEffect(isPlayerPageVisible) {
        if (isPlayerPageVisible && expanded) {
            expanded = false
        }
    }

    // 加载搜索历史
    LaunchedEffect(Unit) {
        val history = prefs.getStringSet("history", emptySet()) ?: emptySet()
        searchHistory.clear()
        searchHistory.addAll(history)
    }

    // 保存搜索历史
    fun saveSearchHistory(query: String) {
        if (query.isBlank()) return
        val history = prefs.getStringSet("history", mutableSetOf()) ?: mutableSetOf()
        val newHistory = history.toMutableSet()
        newHistory.add(query)
        prefs.edit().putStringSet("history", newHistory).apply()
        searchHistory.clear()
        searchHistory.addAll(newHistory)
    }

    // 清除搜索历史
    fun clearSearchHistory() {
        prefs.edit().remove("history").apply()
        searchHistory.clear()
    }

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing) {
            viewModel.loadRecommendPlaylists()
            viewModel.loadDailyRecommendSongs()
            viewModel.loadUserPlaylists()
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
                                        saveSearchHistory(searchText)
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
                            // 显示搜索历史
                            if (searchHistory.isNotEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(16.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "搜索历史",
                                            style = MiuixTheme.textStyles.main,
                                            color = MiuixTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "清除",
                                            modifier = Modifier.clickable { clearSearchHistory() },
                                            color = MiuixTheme.colorScheme.primary
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    FlowRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        searchHistory.forEach { history ->
                                            TextButton(
                                                text = history,
                                                onClick = {
                                                    searchText = history
                                                    viewModel.searchSongs(history)
                                                }
                                            )
                                        }
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
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(160.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .overScrollVertical()
                            .nestedScroll(scrollBehavior.nestedScrollConnection),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 每日推荐、随身听FM、心动模式 横向滚动卡片
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            FeatureCardsRow(
                                dailySongs = recommendState.dailySongs,
                                userPlaylists = recommendState.userPlaylists,
                                onDailyRecommendClick = {
                                    onPlaylistClick("daily_recommend", "", "每日推荐")
                                },
                                onFmClick = {
                                    if (isLoggedIn) {
                                        if (viewModel.isFMMode) {
                                            android.widget.Toast.makeText(context, "已经在随身听模式中", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            viewModel.enterFMMode()
                                            onOpenPlayerPage()
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "请登录后使用", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                },
                                onIntelligenceModeClick = { trackId, playlistId ->
                                    if (isLoggedIn) {
                                        if (viewModel.isIntelligenceMode) {
                                            android.widget.Toast.makeText(context, "已经在心动模式中", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            onIntelligenceMode(trackId, playlistId)
                                        }
                                    } else {
                                        android.widget.Toast.makeText(context, "请登录后使用", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        }

                        // 推荐歌单
                        items(recommendState.playlists) { playlist ->
                            RecommendPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }
                            )
                        }
                    }
                }
        }
    }
}

@Composable
private fun RecommendPlaylistCard(
    playlist: RecommendPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
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
                .aspectRatio(1f),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_gallery)
        )

        // 固定两行高度的标题区域
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = playlist.title,
                style = MiuixTheme.textStyles.body2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

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

@Composable
private fun FeatureCardsRow(
    dailySongs: List<Track>,
    userPlaylists: List<RecommendPlaylist> = emptyList(),
    onDailyRecommendClick: () -> Unit,
    onFmClick: () -> Unit,
    onIntelligenceModeClick: (String, String) -> Unit,
    modifier: Modifier = Modifier
) {
    // 「我喜欢的音乐」歌单 id：优先标题含「喜欢」，否则取第一项
    val likedPlaylistId = userPlaylists.firstOrNull { it.title.contains("喜欢") }?.id
        ?: userPlaylists.firstOrNull()?.id
        ?: ""
    val heartSeedId = dailySongs.firstOrNull()?.id ?: ""
    val canShowHeartMode = heartSeedId.isNotEmpty() || likedPlaylistId.isNotEmpty()

    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(horizontal = 0.dp)
    ) {
        // 每日推荐
        item {
            FeatureCard(
                title = "每日推荐",
                coverUrl = dailySongs.firstOrNull()?.imgUrl ?: "",
                onClick = onDailyRecommendClick
            )
        }

        // 随身听FM
        item {
            FeatureCard(
                title = "随身听FM",
                coverUrl = "",
                onClick = onFmClick,
                isFmCard = true
            )
        }

        // 心动模式：种子歌曲可空，由 ViewModel 再解析；歌单 ID 必须传入「我喜欢的音乐」
        if (canShowHeartMode) {
            item {
                FeatureCard(
                    title = "心动模式",
                    coverUrl = dailySongs.firstOrNull()?.imgUrl ?: "",
                    onClick = { onIntelligenceModeClick(heartSeedId, likedPlaylistId) },
                    isIntelligenceCard = true
                )
            }
        }
    }
}

@Composable
private fun FeatureCard(
    title: String,
    coverUrl: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isFmCard: Boolean = false,
    isIntelligenceCard: Boolean = false
) {
    Card(
        modifier = modifier.width(140.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.Start
        ) {
            // 封面图片
            Box(
                modifier = Modifier
                    .size(116.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isFmCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFE0E0E0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📻",
                            fontSize = 48.sp
                        )
                    }
                } else if (isIntelligenceCard) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFFFFCDD2)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "❤",
                            fontSize = 40.sp,
                            color = Color(0xFFE53935)
                        )
                    }
                } else {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 标题靠左
            Text(
                text = title,
                style = MiuixTheme.textStyles.body2,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
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
