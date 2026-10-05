package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.data.model.AlbumInfo
import com.cloudmusic.miuix.data.model.ArtistInfo
import com.cloudmusic.miuix.data.model.Track
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.cloudmusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.unit.DpSize

@Composable
fun ArtistPage(
    artistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    currentTrack: Track? = null,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    onShare: (Track) -> Unit = {},
    onLikeTrack: () -> Unit = {},
    onDownload: () -> Unit = {},
    onDownloadCover: () -> Unit = {},
    onDownloadAndShare: () -> Unit = {},
    onDownloadCoverAndShare: () -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToComment: (String, String) -> Unit = { _, _ -> },
    onPlayTrack: (Track) -> Unit = {},
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var artistInfo by remember { mutableStateOf<ArtistInfo?>(null) }
    var artistSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistAlbums by remember { mutableStateOf<List<AlbumInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var songsOffset by remember { mutableIntStateOf(0) }
    var albumsOffset by remember { mutableIntStateOf(0) }
    var hasMoreSongs by remember { mutableStateOf(true) }
    var hasMoreAlbums by remember { mutableStateOf(true) }
    var isLoadingMoreSongs by remember { mutableStateOf(false) }
    var isLoadingMoreAlbums by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    var showMoreSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
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

    // 加载更多单曲
    fun loadMoreSongs() {
        if (isLoadingMoreSongs || !hasMoreSongs) return
        isLoadingMoreSongs = true
        coroutineScope.launch {
            NeteaseApi.getArtistSongs(artistId, offset = songsOffset).fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) {
                        hasMoreSongs = false
                    } else {
                        artistSongs = artistSongs + songs
                        songsOffset += songs.size
                        if (songs.size < 50) hasMoreSongs = false
                    }
                },
                onFailure = { hasMoreSongs = false }
            )
            isLoadingMoreSongs = false
        }
    }

    // 加载更多专辑
    fun loadMoreAlbums() {
        if (isLoadingMoreAlbums || !hasMoreAlbums) return
        isLoadingMoreAlbums = true
        coroutineScope.launch {
            NeteaseApi.getArtistAlbums(artistId, offset = albumsOffset).fold(
                onSuccess = { albums ->
                    if (albums.isEmpty()) {
                        hasMoreAlbums = false
                    } else {
                        artistAlbums = artistAlbums + albums
                        albumsOffset += albums.size
                        if (albums.size < 50) hasMoreAlbums = false
                    }
                },
                onFailure = { hasMoreAlbums = false }
            )
            isLoadingMoreAlbums = false
        }
    }

    LaunchedEffect(artistId) {
        isLoading = true
        // 获取歌手信息
        NeteaseApi.getArtistInfo(artistId).fold(
            onSuccess = { info ->
                artistInfo = info
            },
            onFailure = { /* Handle error */ }
        )
        // 获取歌手单曲
        NeteaseApi.getArtistSongs(artistId).fold(
            onSuccess = { songs ->
                artistSongs = songs
                songsOffset = songs.size
                if (songs.size < 50) hasMoreSongs = false
            },
            onFailure = { hasMoreSongs = false }
        )
        // 获取歌手专辑
        NeteaseApi.getArtistAlbums(artistId).fold(
            onSuccess = { albums ->
                artistAlbums = albums
                albumsOffset = albums.size
                if (albums.size < 50) hasMoreAlbums = false
            },
            onFailure = { hasMoreAlbums = false }
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = artistInfo?.name ?: "",
                largeTitle = "歌手",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .clickable { onBackClick() }
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(bottom = FeedBottomPads.Standard),
            overscrollEffect = null
        ) {
            // 歌手信息区域
            item {
                ArtistInfoHeader(artistInfo = artistInfo)
            }

            // TabRow
            item {
                val tabTitles = listOf("单曲", "专辑")
                TabRow(
                    tabs = tabTitles,
                    selectedTabIndex = selectedTab,
                    onTabSelected = { selectedTab = it },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            // 内容区域
            when (selectedTab) {
                0 -> {
                    // 单曲列表
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "加载中...",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else if (artistSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无单曲",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(artistSongs) { index, track ->
                            // 当滚动到接近底部时触发加载更多
                            if (index >= artistSongs.size - 5 && hasMoreSongs && !isLoadingMoreSongs) {
                                LaunchedEffect(Unit) {
                                    loadMoreSongs()
                                }
                            }
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.defaultColors(
                                    color = MiuixTheme.colorScheme.secondaryContainer
                                ),
                                pressFeedbackType = PressFeedbackType.Tilt,
                                showIndication = true,
                                onClick = { onTrackClick(track, artistSongs) }
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
                                                onClick = {
                                                    selectedTrack = track
                                                    showMoreSheet = true
                                                },
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
                                        color = if (track.id == currentTrack?.id) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
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
                                            color = if (track.id == currentTrack?.id) MiuixTheme.colorScheme.primary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                        // 加载更多的指示器
                        if (isLoadingMoreSongs) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "加载中...",
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // 专辑列表
                    if (isLoading) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "加载中...",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else if (artistAlbums.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "暂无专辑",
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    } else {
                        itemsIndexed(artistAlbums) { index, album ->
                            // 当滚动到接近底部时触发加载更多
                            if (index >= artistAlbums.size - 5 && hasMoreAlbums && !isLoadingMoreAlbums) {
                                LaunchedEffect(Unit) {
                                    loadMoreAlbums()
                                }
                            }
                            AlbumItem(
                                album = album,
                                onClick = { onAlbumClick(album.id) }
                            )
                        }
                        // 加载更多的指示器
                        if (isLoadingMoreAlbums) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "加载中...",
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                    )
                                }
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
                            if (artistNames.isNotEmpty()) {
                                artistNames.forEachIndexed { artistIndex, artistName ->
                                    val artistId = if (artistIndex < artistIds.size) artistIds[artistIndex] else ""
                                    ArrowPreference(
                                        title = "歌手：$artistName",
                                        onClick = {
                                            showMoreSheet = false
                                            if (artistId.isNotEmpty()) {
                                                onNavigateToArtist(artistId)
                                            }
                                        }
                                    )
                                }
                            } else {
                                ArrowPreference(
                                    title = "歌手：${track.artist}",
                                    onClick = {
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
private fun ArtistInfoHeader(artistInfo: ArtistInfo?) {
    if (artistInfo == null) return
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌手图片
        AsyncImage(
            model = artistInfo.picUrl,
            contentDescription = artistInfo.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp)),
            contentScale = ContentScale.Crop,
            error = painterResource(android.R.drawable.ic_menu_gallery)
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        // 歌手信息
        Column {
            Text(
                text = artistInfo.name,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Text(
                text = "${artistInfo.albumSize} 张专辑，${artistInfo.musicSize} 首单曲，${artistInfo.mvSize} 部MV",
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }
    }
    
    // 歌手介绍
    if (artistInfo.briefDesc.isNotEmpty()) {
        Text(
            text = artistInfo.briefDesc,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumItem(
    album: AlbumInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        ),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 专辑封面
            AsyncImage(
                model = album.picUrl,
                contentDescription = album.name,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // 专辑信息
            Column {
                Text(
                    text = album.name,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(4.dp))
                
                Text(
                    text = "${album.size} 首歌曲",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
