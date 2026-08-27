package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
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
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun ArtistPage(
    artistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAlbumClick: (Long) -> Unit,
    currentTrack: Track? = null,
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
            contentPadding = PaddingValues(bottom = 16.dp),
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
                                    title = track.title,
                                    titleColor = if (track.id == currentTrack?.id) {
                                        BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
                                    } else {
                                        BasicComponentDefaults.titleColor()
                                    },
                                    summary = "${track.artist} - ${track.album}",
                                    summaryColor = if (track.id == currentTrack?.id) {
                                        BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f))
                                    } else {
                                        BasicComponentDefaults.summaryColor()
                                    },
                                    startAction = {
                                        Text(
                                            text = (index + 1).toString(),
                                            modifier = Modifier.padding(end = 16.dp),
                                            fontSize = 14.sp,
                                            color = if (track.id == currentTrack?.id) MiuixTheme.colorScheme.primary
                                            else MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f)
                                        )
                                    }
                                )
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
    }
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
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        )
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
