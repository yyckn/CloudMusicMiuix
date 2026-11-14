package com.hypermusic.miuix.ui.pages

import com.hypermusic.miuix.ui.navigation.BackHandler
import com.hypermusic.miuix.ui.components.rememberPageContentSearch
import com.hypermusic.miuix.ui.components.PageContentSearchAction
import com.hypermusic.miuix.ui.components.PageContentSearchBar
import com.hypermusic.miuix.ui.components.matchesPageQuery
import com.hypermusic.miuix.ui.components.TrackMoreActions
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.sharedCover
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.model.PlaylistDetail
import com.hypermusic.miuix.data.model.Track
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.TiltFeedback
import top.yukonga.miuix.kmp.utils.pressable
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.unit.DpSize
import kotlinx.coroutines.launch

@Composable
fun PlaylistDetailPage(
    playlistDetail: PlaylistDetail?,
    isLoading: Boolean,
    error: String?,
    currentTrack: Track?,
    initialCoverUrl: String = "",
    initialTitle: String = "",
    sharedCoverKey: String? = null,
    isCloudDisk: Boolean = false,
    isLoadingMore: Boolean = false,
    onLoadMore: () -> Unit = {},
    onRefresh: suspend () -> Unit = {},
    onBackClick: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    onShuffleClick: (List<Track>) -> Unit,
    isTopChart: Boolean = false,
    showFavoritePlaylist: Boolean = false,
    favoritePlaylistCollected: Boolean = false,
    favoritePlaylistInProgress: Boolean = false,
    onFavoritePlaylist: () -> Unit = {},
    onShare: (Track) -> Unit = {},
    onCheckTrackLiked: suspend (Track) -> Result<Boolean>,
    onSetTrackLiked: (Track, Boolean) -> Unit,
    onDownload: (Track) -> Unit = {},
    onDownloadCover: (Track) -> Unit = {},


    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToComment: (String, String) -> Unit = { _, _ -> },
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val rawTitle = playlistDetail?.info?.title
    val displayTitle = if (com.hypermusic.miuix.data.api.PlatformManager.current == com.hypermusic.miuix.data.api.MusicPlatform.QQ) {
        com.hypermusic.miuix.data.model.displayQqPlaylistDetailTitle(rawTitle, initialTitle, com.hypermusic.miuix.data.api.QqMusicApi.accountName)
    } else rawTitle ?: initialTitle
    val scrollBehavior = MiuixScrollBehavior()
    val pageSearch = rememberPageContentSearch("playlist:$sharedCoverKey:$initialTitle")
    val visibleTracks = remember(playlistDetail?.tracks, pageSearch.query) {
        playlistDetail?.tracks.orEmpty().withIndex().filter {
            matchesPageQuery(pageSearch.query, it.value.title, it.value.artist, it.value.album)
        }
    }
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var selectedQqLiked by remember { mutableStateOf<Boolean?>(null) }
    var qqLikeCheckFailed by remember { mutableStateOf(false) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val displayedListState = if (pageSearch.active) searchListState else listState
    LaunchedEffect(pageSearch.query, pageSearch.active, playlistDetail?.tracks?.size, isLoadingMore, isLoading) {
        if (pageSearch.active && pageSearch.query.isNotBlank() && isCloudDisk && !isLoading && !isLoadingMore) onLoadMore()
    }
    LaunchedEffect(pageSearch.query) { searchListState.scrollToItem(0) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(showMoreSheet, selectedTrack?.id, selectedTrack?.source) {
        selectedQqLiked = null
        qqLikeCheckFailed = false
        val track = selectedTrack
        if (showMoreSheet && track != null) {
            onCheckTrackLiked(track).fold(
                onSuccess = { selectedQqLiked = it },
                onFailure = { qqLikeCheckFailed = true }
            )
        }
    }
    val selectedIsLiked = selectedQqLiked == true
    // 当前播放歌曲在本歌单中的位置（-1 表示不在）
    val currentTrackIndex = remember(visibleTracks, currentTrack) {
        visibleTracks.indexOfFirst { it.value.id == currentTrack?.id && it.value.source == currentTrack.source }
    }
    
    // 获取专辑歌手信息
    LaunchedEffect(selectedTrack?.source, selectedTrack?.albumId) {
        albumArtist = null
        albumArtistId = null
        val albumIdStr = selectedTrack?.albumId?.removePrefix("nealbum_")
        val albumIdNum = albumIdStr?.toLongOrNull()
        if (selectedTrack?.source == "netease" && albumIdNum != null) {
            com.hypermusic.miuix.data.api.NeteaseApi.getAlbumDetail(albumIdNum.toString()).onSuccess { (albumInfo, _) ->
                albumArtist = albumInfo.artist.ifEmpty { null }
                albumArtistId = albumInfo.artistId.takeIf { it > 0 }
            }
        }
    }
    
    BackHandler(enabled = isBackHandlerEnabled) {
        if (pageSearch.active) pageSearch.close() else onBackClick()
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = displayTitle.ifBlank { "歌单详情" },
                    largeTitle = if (isTopChart) (playlistDetail?.info?.title ?: initialTitle) else "歌单详情",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    defaultWindowInsetsPadding = false,
                    scrollBehavior = scrollBehavior,
                    actions = { PageContentSearchAction(pageSearch) },
                    bottomContent = { PageContentSearchBar(pageSearch) },
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .size(24.dp)
                                .clickable { if (pageSearch.active) pageSearch.close() else onBackClick() }
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            if (!pageSearch.active && !isLoading && error == null && currentTrackIndex >= 0) {
                FloatingActionButton(
                    onClick = {
                        scope.launch { displayedListState.animateScrollToItem(currentTrackIndex + 1) }
                    },
                    modifier = Modifier.padding(bottom = 80.dp)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Location,
                        contentDescription = "定位到当前歌曲",
                        tint = MiuixTheme.colorScheme.onPrimary
                    )
                }
            }
        }
    ) { paddingValues ->
        PullToRefresh(
            isRefreshing = isRefreshing && !pageSearch.active,
            onRefresh = {
                if (!pageSearch.active && !isLoading && !isRefreshing) {
                    isRefreshing = true
                    scope.launch {
                        try { onRefresh() } finally { isRefreshing = false }
                    }
                }
            },
            pullToRefreshState = pullToRefreshState,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = paddingValues.calculateTopPadding())
        ) {
        LazyColumn(
            state = displayedListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                bottom = FeedBottomPads.Standard
            )
        ) {
            if (pageSearch.active && pageSearch.query.isBlank()) return@LazyColumn
            if (!pageSearch.active) item {
                PlaylistHeader(
                    detail = playlistDetail,
                    initialCoverUrl = initialCoverUrl,
                    initialTitle = displayTitle,
                    sharedCoverKey = sharedCoverKey,
                    isTopChart = isTopChart,
                    showFavoritePlaylist = showFavoritePlaylist,
                    favoritePlaylistCollected = favoritePlaylistCollected,
                    favoritePlaylistInProgress = favoritePlaylistInProgress,
                    onFavoritePlaylist = onFavoritePlaylist,
                    onPlayAllClick = { onPlayAllClick(playlistDetail?.tracks ?: emptyList()) },
                    onShuffleClick = { onShuffleClick(playlistDetail?.tracks ?: emptyList()) },
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
                if (visibleTracks.isEmpty() && pageSearch.query.isNotBlank()) item {
                    Text("没有找到匹配的歌曲", modifier = Modifier.padding(16.dp),
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
                itemsIndexed(visibleTracks) { _, (index, track) ->
                    // 当滚动到接近底部时触发加载更多（云盘兜底；主路径已在 ViewModel 后台自动拉完）
                    if (isCloudDisk && !isLoadingMore && playlistDetail.tracks.isNotEmpty() &&
                        index >= playlistDetail.tracks.size - 3
                    ) {
                        LaunchedEffect(playlistDetail.tracks.size) {
                            onLoadMore()
                        }
                    }
                    SharedTrackRow(
                        track = track,
                        rank = if (isTopChart) index + 1 else null,
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
                item {
                    TrackMoreActions(liked = selectedIsLiked,
                        likeLabel = when {
                            qqLikeCheckFailed -> "收藏状态获取失败"
                            selectedQqLiked == null -> "正在获取收藏状态"
                            selectedIsLiked -> "取消收藏"
                            else -> "收藏"
                        },
                        likeEnabled = selectedTrack != null && selectedQqLiked != null,
                        onShare = { showMoreSheet = false; selectedTrack?.let { onShare(it) } },
                        onLike = {
                            showMoreSheet = false
                            if (selectedIsLiked) showUnlikeDialog = true else selectedTrack?.let { track ->
                                onSetTrackLiked(track, true)
                            }
                        },
                        onComment = { showMoreSheet = false; selectedTrack?.let { onNavigateToComment(it.id, it.title) } })
                }

                // 歌手与专辑区域
                item { AddTrackToPlaylistAction(selectedTrack) }
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
                                selectedTrack?.let(onDownload)
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
                                selectedTrack?.let(onDownloadCover)
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
                        selectedTrack?.let { track ->
                            onSetTrackLiked(track, false)
                        }
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
    sharedCoverKey: String?,
    isTopChart: Boolean,
    showFavoritePlaylist: Boolean,
    favoritePlaylistCollected: Boolean,
    favoritePlaylistInProgress: Boolean,
    onFavoritePlaylist: () -> Unit,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope
) {
    val coverUrl = detail?.info?.coverImgUrl?.ifBlank { initialCoverUrl } ?: initialCoverUrl
    val title = initialTitle.ifBlank { detail?.info?.title.orEmpty() }
    val count = detail?.tracks?.size ?: 0
    val summary = if (isTopChart && !detail?.info?.description.isNullOrBlank())
        "更新于${detail?.info?.description}，共${count}首歌曲" else "$count 首歌曲"
    with(sharedTransitionScope) {
        ResponsiveMediaHeader(title = title, details = listOf(summary), showFavorite = showFavoritePlaylist,
            favoriteCollected = favoritePlaylistCollected, favoriteEnabled = !favoritePlaylistInProgress, onFavorite = onFavoritePlaylist,
            onPlay = onPlayAllClick, onShuffle = onShuffleClick) { coverModifier ->
            AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER),
                model = coverUrl, contentDescription = title,
                modifier = coverModifier.clip(RoundedCornerShape(12.dp)).then(
                    if (isTopChart) Modifier.pressable(interactionSource = remember { MutableInteractionSource() }, indication = TiltFeedback())
                    else Modifier
                ).sharedCover(sharedCoverKey),
                contentScale = ContentScale.Crop, error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter())
        }
    }

}

/** 歌单、榜单与专辑共用的响应式头部。 */
@Composable
internal fun ResponsiveMediaHeader(
    title: String, details: List<String>, showFavorite: Boolean, favoriteCollected: Boolean = false, favoriteEnabled: Boolean = true,
    onFavorite: () -> Unit, onPlay: () -> Unit, onShuffle: () -> Unit,
    cover: @Composable (Modifier) -> Unit
) {
    val tablet = LocalConfiguration.current.screenWidthDp >= 600
    val info: @Composable (Boolean) -> Unit = { centered ->
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start) {
            Text(title, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface, textAlign = if (centered) TextAlign.Center else TextAlign.Start)
            Spacer(Modifier.height(8.dp))
            details.filter { it.isNotBlank() }.forEach { line ->
                Text(line, fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    textAlign = if (centered) TextAlign.Center else TextAlign.Start)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
    val actions: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onPlay, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                Icon(MiuixIcons.Play, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp)); Text("播放")
            }
            Button(onClick = onShuffle, modifier = Modifier.size(48.dp), minWidth = 0.dp, insideMargin = PaddingValues(0.dp)) {
                Icon(MiuixIcons.Merge, contentDescription = "随机播放", modifier = Modifier.size(24.dp))
            }
            if (showFavorite) Button(onClick = onFavorite, enabled = favoriteEnabled, modifier = Modifier.size(48.dp), minWidth = 0.dp, insideMargin = PaddingValues(0.dp)) {
                Icon(if (favoriteCollected) MiuixIcons.Delete else MiuixIcons.Add, contentDescription = if (favoriteCollected) "取消收藏" else "添加到个人收藏", modifier = Modifier.size(24.dp))
            }
        }
    }
    if (tablet) {
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), verticalAlignment = Alignment.Top) {
            cover(Modifier.size(140.dp))
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                info(false)
                Spacer(Modifier.height(8.dp))
                actions()
            }
        }
    } else {
        Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            cover(Modifier.size((LocalConfiguration.current.screenWidthDp * 0.65f).coerceIn(140f, 240f).dp))
            Spacer(Modifier.height(16.dp))
            info(true)
            Spacer(Modifier.height(12.dp))
            actions()
        }
    }
}

@Composable
private fun SharedTrackCover(track: Track) {
    AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL),
        model = track.imgUrl, contentDescription = track.title,
        modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
        contentScale = ContentScale.Crop, error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter())
}

@Composable
internal fun SharedTrackRow(
    track: Track,
    rank: Int? = null,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                if (rank != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(text = rank.toString(), modifier = Modifier.widthIn(min = 20.dp), style = MiuixTheme.textStyles.body2,
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        Spacer(Modifier.width(4.dp))
                        SharedTrackCover(track)
                    }
                } else {
                    SharedTrackCover(track)
                }
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
            }
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
