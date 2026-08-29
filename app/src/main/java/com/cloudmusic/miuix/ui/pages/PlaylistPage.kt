package com.cloudmusic.miuix.ui.pages

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.viewmodel.RecommendState
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun PlaylistPage(
    recommendState: RecommendState,
    onPlaylistClick: (String, String, String) -> Unit,
    onRefresh: () -> Unit,
    isLoggedIn: Boolean,
    onLoginClick: () -> Unit = {},
    onIntelligenceMode: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // 自动加载歌单
    LaunchedEffect(Unit) {
        if (isLoggedIn && recommendState.userPlaylists.isEmpty()) {
            onRefresh()
        }
    }

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && isLoggedIn) {
            onRefresh()
            isRefreshing = false
        } else {
            isRefreshing = false
        }
    }

    // 按我喜欢的音乐、云盘、其他歌单排序
    val likedPlaylists = recommendState.userPlaylists.filter { it.title.contains("喜欢") }
    val otherPlaylists = recommendState.userPlaylists.filter { !it.title.contains("喜欢") }
    val cloudDiskPlaylist = RecommendPlaylist(
        id = "cloud_disk",
        title = "云盘",
        coverImgUrl = "",
        trackCount = 0,
        isCloudDisk = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = "歌单",
                largeTitle = "歌单",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false
            )
        }
    ) { paddingValues ->
        if (!isLoggedIn) {
            // 未登录状态：显示提示文字
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "登录后即可查看个人歌单",
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
        } else {
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
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 云盘歌单（第一位）
                    item {
                        UserPlaylistCard(
                            playlist = cloudDiskPlaylist,
                            onClick = { onPlaylistClick(cloudDiskPlaylist.id, cloudDiskPlaylist.coverImgUrl, cloudDiskPlaylist.title) }
                        )
                    }

                    // 心动模式
                    if (likedPlaylists.isNotEmpty()) {
                        val likedPlaylist = likedPlaylists.first()
                        val firstTrackId = recommendState.dailySongs.firstOrNull()?.id ?: ""
                        if (firstTrackId.isNotEmpty()) {
                            item {
                                IntelligenceModeCard(
                                    onClick = { onIntelligenceMode(firstTrackId, likedPlaylist.id) }
                                )
                            }
                        }
                    }

                    // 我喜欢的音乐歌单
                    if (likedPlaylists.isEmpty()) {
                        item {
                            Text(
                                text = "暂无喜欢的音乐",
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        items(likedPlaylists) { playlist ->
                            UserPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }
                            )
                        }
                    }

                    // 其他歌单
                    if (otherPlaylists.isEmpty()) {
                        item {
                            Text(
                                text = "暂无歌单",
                                modifier = Modifier.padding(vertical = 16.dp),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        items(otherPlaylists) { playlist ->
                            UserPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UserPlaylistCard(
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = playlist.coverImgUrl,
                contentDescription = playlist.title,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = playlist.title,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (!playlist.isCloudDisk) {
                    Text(
                        text = "${playlist.trackCount}首歌曲",
                        style = MiuixTheme.textStyles.subtitle,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

@Composable
private fun IntelligenceModeCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MiuixTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "❤",
                    fontSize = 24.sp,
                    color = MiuixTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = "心动模式",
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "根据当前喜爱歌曲实时推荐",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
