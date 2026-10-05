package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.Track
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.cloudmusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.unit.DpSize

@Composable
fun PlaylistDetailPage(
    playlistDetail: PlaylistDetail?,
    isLoading: Boolean,
    error: String?,
    currentTrack: Track?,
    isCurrentTrackLiked: Boolean = false,
    initialCoverUrl: String = "",
    initialTitle: String = "",
    isCloudDisk: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    onShare: (Track) -> Unit = {},
    onLikeTrack: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDownloadCover: () -> Unit = {},
    onDownloadAndShare: () -> Unit = {},
    onDownloadCoverAndShare: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToComment: (String, String) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var showMoreSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    
    // 获取专辑歌手信息
    LaunchedEffect(selectedTrack?.albumId) {
        val albumIdStr = selectedTrack?.albumId?.removePrefix("nealbum_")
        val albumIdNum = albumIdStr?.toLongOrNull()
        if (albumIdNum != null) {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                com.cloudmusic.miuix.data.api.NeteaseApi.getAlbumDetail(albumIdNum.toString()).fold(
                    onSuccess = { (albumInfo, _) ->
                        albumArtist = albumInfo.artist.ifEmpty { null }
                        albumArtistId = if (albumInfo.artistId > 0) albumInfo.artistId else null
                    },
                    onFailure = {
                        albumArtist = null
                        albumArtistId = null
                    }
                )
            }
        } else {
            albumArtist = null
            albumArtistId = null
        }
    }
    
    BackHandler(enabled = isBackHandlerEnabled) {
        onBackClick()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = playlistDetail?.info?.title ?: "歌单详情",
                largeTitle = "歌单详情",
                defaultWindowInsetsPadding = false,
                scrollBehavior = scrollBehavior,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(24.dp)
                            .clickable { onBackClick() }
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = paddingValues.calculateTopPadding(),
                bottom = FeedBottomPads.Standard
            )
        ) {
            item {
                PlaylistHeader(
                    detail = playlistDetail,
                    initialCoverUrl = initialCoverUrl,
                    initialTitle = initialTitle,
                    onPlayAllClick = { onPlayAllClick(playlistDetail?.tracks ?: emptyList()) },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        InfiniteProgressIndicator()
                    }
                }
            } else if (error != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "加载失败",
                                fontSize = 16.sp,
                                color = MiuixTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = error,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            } else if (playlistDetail != null) {
                itemsIndexed(playlistDetail.tracks) { index, track ->
                    // 当滚动到接近底部时触发加载更多（云盘兜底；主路径已在 ViewModel 后台自动拉完）
                    if (isCloudDisk && !isLoadingMore && playlistDetail.tracks.isNotEmpty() &&
                        index >= playlistDetail.tracks.size - 3
                    ) {
                        LaunchedEffect(playlistDetail.tracks.size) {
                            onLoadMore()
                        }
                    }
                    TrackItem(
                        track = track,
                        isPlaying = track.id == currentTrack?.id,
                        onClick = { onTrackClick(track) },
                        onMoreClick = {
                            selectedTrack = track
                            showMoreSheet = true
                        }
                    )
                }
                
                // 加载更多的指示器
                if (isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            InfiniteProgressIndicator()
                        }
                    }
                }
            }
        }
        
        // 更多面板
        OverlayBottomSheet(
            show = showMoreSheet,
            title = "更多",
            insideMargin = DpSize(12.dp, 0.dp),
            startAction = null,
            endAction = {
                IconButton(
                    onClick = { showMoreSheet = false }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            onDismissRequest = { showMoreSheet = false }
        ) {
            LazyColumn(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                // 歌曲区域
                item { SmallTitle("歌曲") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        ArrowPreference(
                            title = "分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                selectedTrack?.let { onShare(it) }
                            }
                        )
                        ArrowPreference(
                            title = if (isCurrentTrackLiked) "取消收藏" else "收藏",
                            startAction = {
                                Icon(
                                    imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                if (isCurrentTrackLiked) {
                                    showUnlikeDialog = true
                                } else {
                                    onLikeTrack()
                                }
                            }
                        )
                        ArrowPreference(
                            title = "评论",
                            startAction = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                selectedTrack?.let { track ->
                                    onNavigateToComment(track.id, track.title)
                                }
                            }
                        )
                    }
                }

                // 歌手与专辑区域
                item { SmallTitle("歌手与专辑") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        selectedTrack?.let { track ->
                            // 显示所有歌手
                            val artistNames = track.artistNames
                            val artistIds = track.artistIds
                            android.util.Log.d("PlaylistDetailPage", "selectedTrack: ${track.title}, artistNames: $artistNames, artistIds: $artistIds")
                            if (artistNames.isNotEmpty()) {
                                artistNames.forEachIndexed { artistIndex, artistName ->
                                    val artistId = if (artistIndex < artistIds.size) artistIds[artistIndex] else ""
                                    ArrowPreference(
                                        title = "歌手：$artistName",
                                        onClick = {
                                            android.util.Log.d("PlaylistDetailPage", "Clicked artist: $artistName, id: $artistId")
                                            showMoreSheet = false
                                            val finalArtistId = if (artistId.isNotEmpty()) artistId else track.artistId
                                            if (finalArtistId.isNotEmpty()) {
                                                onNavigateToArtist(finalArtistId)
                                            }
                                        }
                                    )
                                }
                            } else {
                                ArrowPreference(
                                    title = "歌手：${track.artist}",
                                    onClick = {
                                        android.util.Log.d("PlaylistDetailPage", "Clicked artist: ${track.artist}, id: ${track.artistId}")
                                        showMoreSheet = false
                                        if (track.artistId.isNotEmpty()) {
                                            onNavigateToArtist(track.artistId)
                                        }
                                    }
                                )
                            }
                            // 显示专辑
                            ArrowPreference(
                                title = "专辑：${track.album}",
                                onClick = {
                                    showMoreSheet = false
                                    if (track.albumId.isNotEmpty()) {
                                        val albumIdStr = track.albumId.removePrefix("nealbum_")
                                        val albumId = albumIdStr.toLongOrNull()
                                        if (albumId != null) {
                                            onNavigateToAlbum(albumId)
                                        }
                                    }
                                }
                            )
                            // 显示专辑歌手
                            if (albumArtist != null) {
                                ArrowPreference(
                                    title = "专辑歌手：$albumArtist",
                                    onClick = {
                                        showMoreSheet = false
                                        if (albumArtistId != null) {
                                            onNavigateToArtist("neartist_$albumArtistId")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 下载区域
                item { SmallTitle("下载") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        ArrowPreference(
                            title = "下载此歌曲",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                onDownload()
                            }
                        )
                        ArrowPreference(
                            title = "下载此歌曲并分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                onDownloadAndShare()
                            }
                        )
                        ArrowPreference(
                            title = "下载封面图片",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                onDownloadCover()
                            }
                        )
                        ArrowPreference(
                            title = "下载封面图片并分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMoreSheet = false
                                onDownloadCoverAndShare()
                            }
                        )
                    }
                }
            }
        }
    }
    
    // 取消收藏确认对话框
    OverlayDialog(
        show = showUnlikeDialog,
        title = "取消收藏",
        summary = "确定要取消收藏这首歌曲吗？",
        onDismissRequest = { showUnlikeDialog = false },
        content = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TextButton(
                    text = "取消",
                    onClick = { showUnlikeDialog = false },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(20.dp))
                TextButton(
                    text = "确定",
                    onClick = {
                        showUnlikeDialog = false
                        onLikeTrack()
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun PlaylistHeader(
    detail: PlaylistDetail?,
    initialCoverUrl: String,
    initialTitle: String,
    onPlayAllClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val coverUrl = detail?.info?.coverImgUrl ?: initialCoverUrl
    val title = detail?.info?.title ?: initialTitle
    val trackCount = detail?.tracks?.size ?: 0

    with(sharedTransitionScope) {
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp)
            ) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .size(140.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "playlist_cover_${detail?.info?.id ?: initialCoverUrl}"),
                            animatedVisibilityScope = animatedVisibilityScope
                        ),
                    contentScale = ContentScale.Crop,
                    error = painterResource(android.R.drawable.ic_menu_gallery)
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = title,
                        style = MiuixTheme.textStyles.title3,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "$trackCount 首歌曲",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }

            Button(
                onClick = onPlayAllClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = ButtonDefaults.buttonColorsPrimary()
            ) {
                Icon(
                    imageVector = MiuixIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(text = "播放全部")
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                AsyncImage(
                    model = track.imgUrl,
                    contentDescription = track.title,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop,
                    error = painterResource(android.R.drawable.ic_menu_gallery)
                )
                Spacer(modifier = Modifier.width(12.dp))
            },
            endActions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(track.duration),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onMoreClick,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.More,
                            contentDescription = "更多",
                            modifier = Modifier.size(20.dp),
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            },
            onClick = onClick
        ) {
            Text(
                text = track.title,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = if (isPlaying) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (track.isVip) {
                    Text(
                        text = "VIP",
                        fontSize = 10.sp,
                        color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .border(
                                width = 1.dp,
                                color = MiuixTheme.colorScheme.primary,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = "${track.artist} - ${track.album}",
                    fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = if (isPlaying) MiuixTheme.colorScheme.primary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
