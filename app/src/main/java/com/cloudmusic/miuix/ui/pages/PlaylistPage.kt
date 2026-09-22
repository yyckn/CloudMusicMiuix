package com.cloudmusic.miuix.ui.pages

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.AlbumInfo
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.displayPlaylistTitle
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
    onLoadCloudDiskCover: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var isRefreshing by remember { mutableStateOf(false) }
    val pullToRefreshState = rememberPullToRefreshState()

    // 自动加载歌单 + 云盘封面 + 收藏专辑
    LaunchedEffect(Unit) {
        if (isLoggedIn) {
            if (recommendState.userPlaylists.isEmpty()) {
                onRefresh()
            }
            onLoadCloudDiskCover()
        }
    }

    // 下拉刷新回调
    LaunchedEffect(isRefreshing) {
        if (isRefreshing && isLoggedIn) {
            onRefresh()
            onLoadCloudDiskCover()
            isRefreshing = false
        } else {
            isRefreshing = false
        }
    }

    // 个人收藏的音乐 / 其他人收藏的歌单（归到「歌单」）/ 自建歌单 / 收藏的专辑
    val likedPlaylists = recommendState.userPlaylists.filter {
        it.title.contains("喜欢") || it.title.contains("收藏的音乐")
    }.map { it.copy(title = displayPlaylistTitle(it.title)) }
    // 自建 + 收藏的他人歌单，统一放在「歌单」下
    val otherPlaylists = recommendState.userPlaylists.filter {
        !it.title.contains("喜欢") && !it.title.contains("收藏的音乐")
    }
    val userAlbums = recommendState.userAlbums
    val cloudDiskPlaylist = RecommendPlaylist(
        id = "cloud_disk",
        title = "音乐云盘",
        coverImgUrl = recommendState.cloudDiskCoverUrl,
        trackCount = 0,
        isCloudDisk = true
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = "我的收藏",
                largeTitle = "我的收藏",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false
            )
        }
    ) { paddingValues ->
        if (!isLoggedIn) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "登录后即可查看我的收藏",
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
                modifier = Modifier.padding(
                    top = paddingValues.calculateTopPadding(),
                    // 垫高底部，避免内容被播放条/导航栏挡住（与设置页同思路）
                    bottom = paddingValues.calculateBottomPadding()
                )
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .overScrollVertical()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    // 与设置页一致：不用 spacedBy；额外底部留白，保证最后一项可滚出
                    contentPadding = PaddingValues(bottom = 24.dp),
                    overscrollEffect = null
                ) {
                    item { Spacer(modifier = Modifier.size(12.dp)) }

                    // 个人收藏
                    item { SmallTitle("个人收藏") }

                    // XXX收藏的音乐
                    if (likedPlaylists.isEmpty()) {
                        item {
                            Text(
                                text = "暂无收藏的音乐",
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp),
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        items(likedPlaylists) { playlist ->
                            UserPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                            )
                        }
                    }

                    // 音乐云盘
                    item {
                        UserPlaylistCard(
                            playlist = cloudDiskPlaylist,
                            onClick = {
                                onPlaylistClick(
                                    cloudDiskPlaylist.id,
                                    cloudDiskPlaylist.coverImgUrl,
                                    cloudDiskPlaylist.title
                                )
                            },
                            modifier = Modifier
                                .padding(horizontal = 12.dp)
                                .padding(bottom = 12.dp)
                        )
                    }

                    // 歌单（含收藏的他人歌单）：无内容时隐藏标题
                    if (otherPlaylists.isNotEmpty()) {
                        item { SmallTitle("歌单") }
                        items(otherPlaylists) { playlist ->
                            UserPlaylistCard(
                                playlist = playlist,
                                onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
                            )
                        }
                    }

                    // 专辑：无内容时隐藏标题
                    if (userAlbums.isNotEmpty()) {
                        item { SmallTitle("专辑") }
                        items(userAlbums) { album ->
                            UserAlbumCard(
                                album = album,
                                onClick = { onNavigateToAlbum(album.id) },
                                modifier = Modifier
                                    .padding(horizontal = 12.dp)
                                    .padding(bottom = 12.dp)
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
private fun UserAlbumCard(
    album: AlbumInfo,
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
                model = album.picUrl,
                contentDescription = album.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = album.name,
                    style = MiuixTheme.textStyles.main,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (album.artist.isNotEmpty()) album.artist else "${album.size}首歌曲",
                    style = MiuixTheme.textStyles.subtitle,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
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
