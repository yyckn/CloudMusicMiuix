package com.hypermusic.miuix.ui.pages

import com.hypermusic.miuix.ui.navigation.BackHandler
import com.hypermusic.miuix.ui.components.rememberPageContentSearch
import com.hypermusic.miuix.ui.components.PageContentSearchAction
import com.hypermusic.miuix.ui.components.PageContentSearchBar
import com.hypermusic.miuix.ui.components.matchesPageQuery
import com.hypermusic.miuix.ui.components.TrackMoreActions
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.sharedCover
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.model.AlbumInfo
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
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.rememberPullToRefreshState
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Share
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import com.hypermusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.unit.DpSize

@Composable
fun AlbumPage(
    albumId: Long,
    isQq: Boolean = false,
    initialCoverUrl: String = "",
    sharedCoverKey: String? = null,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    onShuffleClick: (List<Track>) -> Unit,
    onAlbumFavorited: () -> Unit = {},
    currentTrack: Track? = null,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    onShare: (Track) -> Unit = {},
    onCheckTrackLiked: suspend (Track) -> Result<Boolean>,
    onSetTrackLiked: (Track, Boolean) -> Unit,
    onDownload: (Track) -> Unit = {},
    onDownloadCover: (Track) -> Unit = {},


    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToComment: (Track) -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val pageSearch = rememberPageContentSearch("album:$albumId")
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var albumInfo by remember { mutableStateOf<AlbumInfo?>(null) }
    var albumSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    val visibleSongs = remember(albumSongs, pageSearch.query) {
        albumSongs.filter { matchesPageQuery(pageSearch.query, it.title, it.artist, it.album) }
    }
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var selectedLiked by remember { mutableStateOf<Boolean?>(null) }
    var likeCheckFailed by remember { mutableStateOf(false) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val displayedListState = if (pageSearch.active) searchListState else listState
    LaunchedEffect(pageSearch.query) { searchListState.scrollToItem(0) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(showMoreSheet, selectedTrack?.source, selectedTrack?.id) {
        selectedLiked = null
        likeCheckFailed = false
        val track = selectedTrack
        if (showMoreSheet && track != null) onCheckTrackLiked(track).fold(
            onSuccess = { selectedLiked = it }, onFailure = { likeCheckFailed = true })
    }
    val isBodian = com.hypermusic.miuix.data.api.PlatformManager.current == com.hypermusic.miuix.data.api.MusicPlatform.BODIAN
    val isKugou = com.hypermusic.miuix.data.api.PlatformManager.current == com.hypermusic.miuix.data.api.MusicPlatform.KUGOU
    val isQishui = com.hypermusic.miuix.data.api.PlatformManager.current == com.hypermusic.miuix.data.api.MusicPlatform.QISHUI
    val kugouLibrary = remember(context, isKugou) {
        if (isKugou) {
            val client = com.hypermusic.miuix.data.api.KugouClient(context)
            com.hypermusic.miuix.data.api.KugouLibrary(client, com.hypermusic.miuix.data.api.KugouAuth(context, client))
        } else null
    }
    val bodianLibrary = remember(context, isBodian) {
        if (isBodian) {
            val client = com.hypermusic.miuix.data.api.BodianClient(context)
            com.hypermusic.miuix.data.api.BodianLibrary(client, com.hypermusic.miuix.data.api.BodianAuth(context, client), com.hypermusic.miuix.data.api.BodianCatalog(client))
        } else null
    }
    val accountKey = if (isQishui) null else if (isBodian) {
        val client = com.hypermusic.miuix.data.api.BodianClient(context)
        com.hypermusic.miuix.data.api.BodianAuth(context, client).currentAccount()?.uid
    } else if (isKugou) {
        val client = com.hypermusic.miuix.data.api.KugouClient(context)
        com.hypermusic.miuix.data.api.KugouAuth(context, client).account()?.userId
    } else if (isQq) QqMusicApi.dataAccountId else NeteaseApi.getCurrentUserId()?.toString()
    var albumCollected by remember(albumId, isQq, accountKey) { mutableStateOf<Boolean?>(null) }
    var collectingAlbum by remember(albumId, isQq, accountKey) { mutableStateOf(false) }
    LaunchedEffect(albumId, isQq, isBodian, isKugou, isQishui, accountKey) {
        if (isQishui) {
            albumCollected = null
        } else if (accountKey == null) {
            albumCollected = false
        } else if (isBodian) {
            try { albumCollected = requireNotNull(bodianLibrary).isCollected(albumId.toString(), album = true) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { albumCollected = null }
        } else if (isKugou) {
            try { albumCollected = requireNotNull(kugouLibrary).isAlbumCollected(albumId) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { albumCollected = null }
        } else if (isQq) {
            QqMusicApi.getFavoriteAlbums().onSuccess { albums -> albumCollected = albums.any { it.id == albumId } }
        } else {
            var offset = 0
            var found = false
            var failed = false
            do {
                val result = NeteaseApi.getUserAlbums(limit = 100, offset = offset)
                val albums = result.getOrNull()
                if (albums == null) { failed = true; break }
                found = albums.any { it.id == albumId }
                offset += albums.size
            } while (!found && albums.size == 100)
            if (!failed) albumCollected = found
        }
    }
    fun collectAlbum() {
        if (collectingAlbum || albumCollected == null) return
        val favorite = albumCollected != true
        if (accountKey == null) {
            android.widget.Toast.makeText(context, "请先登录后收藏专辑", android.widget.Toast.LENGTH_SHORT).show()
            return
        }
        collectingAlbum = true
        val kugouGeneration = if (isKugou) com.hypermusic.miuix.data.api.KugouAuth(context, com.hypermusic.miuix.data.api.KugouClient(context)).sessionGeneration() else null
        val bodianRevision = if (isBodian) com.hypermusic.miuix.data.api.BodianAuth(context, com.hypermusic.miuix.data.api.BodianClient(context)).sessionRevision() else null
        scope.launch {
            val result = if (isBodian) try {
                val client = com.hypermusic.miuix.data.api.BodianClient(context)
                check(com.hypermusic.miuix.data.api.BodianAuth(context, client).currentAccount()?.uid == accountKey) { "波点音乐账号已切换，请重试" }
                requireNotNull(bodianLibrary).collect(albumId.toString(), source = 6, expectedRevision = bodianRevision, favorite = favorite)
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            else if (isKugou) try {
                requireNotNull(kugouLibrary).collectAlbum(requireNotNull(albumInfo), expectedUserId = accountKey, expectedGeneration = kugouGeneration, favorite = favorite)
                Result.success(Unit)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            else if (isQq) QqMusicApi.favoriteAlbum(albumId, favorite).map { Unit } else NeteaseApi.favoriteAlbum(albumId, favorite).map { Unit }
            val currentAccount = if (isBodian) {
                val client = com.hypermusic.miuix.data.api.BodianClient(context)
                com.hypermusic.miuix.data.api.BodianAuth(context, client).currentAccount()?.uid
            } else if (isKugou) {
                val client = com.hypermusic.miuix.data.api.KugouClient(context)
                com.hypermusic.miuix.data.api.KugouAuth(context, client).account()?.userId
            } else if (isQq) QqMusicApi.dataAccountId else NeteaseApi.getCurrentUserId()?.toString()
            if (currentAccount == accountKey) {
                result.fold(onSuccess = {
                    albumCollected = favorite
                    if (isBodian) com.hypermusic.miuix.data.api.AdditionalLibraryChanges.changed(com.hypermusic.miuix.data.api.MusicPlatform.BODIAN)
                    if (isKugou) com.hypermusic.miuix.data.api.AdditionalLibraryChanges.changed(com.hypermusic.miuix.data.api.MusicPlatform.KUGOU)
                    onAlbumFavorited()
                    android.widget.Toast.makeText(context, if (favorite) "已添加到个人收藏" else "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
                }, onFailure = { android.widget.Toast.makeText(context, it.message ?: "收藏专辑失败", android.widget.Toast.LENGTH_SHORT).show() })
                collectingAlbum = false
            }
        }
    }
    val currentTrackIndex = remember(visibleSongs, currentTrack) {
        visibleSongs.indexOfFirst { it.id == currentTrack?.id && it.source == currentTrack.source }
    }

    BackHandler(enabled = isBackHandlerEnabled) {
        if (pageSearch.active) pageSearch.close() else onBackClick()
    }

    var refreshGeneration by remember(albumId, isQq) { mutableStateOf(0) }
    LaunchedEffect(albumId, isQq, isBodian, isKugou, isQishui, refreshGeneration) {
        isLoading = true
        loadError = null
        albumInfo = null
        albumSongs = emptyList()
        val result = if (isQishui) try {
            Result.success(com.hypermusic.miuix.data.api.QishuiShareAlbum.load(albumId))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        else if (isBodian) try {
            val catalog = com.hypermusic.miuix.data.api.BodianCatalog(com.hypermusic.miuix.data.api.BodianClient(context))
            val info = com.hypermusic.miuix.data.api.BodianCatalog.parseAlbum(catalog.albumDetail(albumId.toString()))
            val tracks = mutableListOf<Track>(); var offset = 0; var more = true
            while (more && offset < 20_000) {
                val page = catalog.albumSongs(albumId.toString(), offset, 100)
                tracks += page.items; more = page.hasMore
                check(!more || page.nextOffset > offset) { "波点音乐专辑分页异常" }; offset = page.nextOffset
            }
            check(!more) { "波点音乐专辑过长" }
            Result.success(info to tracks.toList())
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        else if (isKugou) try {
            Result.success(com.hypermusic.miuix.data.api.KugouCatalog(com.hypermusic.miuix.data.api.KugouClient(context)).album(albumId))
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { Result.failure(e) }
        else if (isQq) QqMusicApi.getAlbumDetail(albumId) else NeteaseApi.getAlbumDetail(albumId.toString())
        result.fold(
            onSuccess = { (info, songs) ->
                albumInfo = info
                albumSongs = songs
            },
            onFailure = { loadError = it.message ?: "专辑加载失败" }
        )
        isLoading = false
        isRefreshing = false
    }

    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                TopAppBar(
                    title = albumInfo?.name ?: "专辑详情",
                    largeTitle = "专辑详情",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    actions = { PageContentSearchAction(pageSearch) },
                    bottomContent = { PageContentSearchBar(pageSearch) },
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { if (pageSearch.active) pageSearch.close() else onBackClick() }
                        )
                    }
                )
            }
        },
        floatingActionButton = {
            if (!pageSearch.active && !isLoading && albumSongs.isNotEmpty() && currentTrackIndex >= 0) {
                FloatingActionButton(
                    onClick = { scope.launch {
                        val hasNotice = isQishui && (albumInfo?.size ?: 0) > albumSongs.size
                        displayedListState.animateScrollToItem(currentTrackIndex + if (hasNotice) 2 else 1)
                    } },
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
            onRefresh = { if (!pageSearch.active && !isLoading) { isRefreshing = true; refreshGeneration++ } },
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = FeedBottomPads.Standard),
            overscrollEffect = null
        ) {
            // 专辑头部信息
            if (pageSearch.active && pageSearch.query.isBlank()) return@LazyColumn
            if (!pageSearch.active) item {
                AlbumHeader(
                    albumInfo = albumInfo,
                    initialCoverUrl = initialCoverUrl,
                    sharedCoverKey = sharedCoverKey,
                    trackCount = albumSongs.size,
                    onPlayAllClick = { onPlayAllClick(albumSongs) },
                    onShuffleClick = { onShuffleClick(albumSongs) },
                    showFavorite = albumInfo != null && albumCollected != null,
                    favoriteCollected = albumCollected == true,
                    favoriteEnabled = !collectingAlbum,
                    onFavorite = { collectAlbum() }
                )
            }

            if (!pageSearch.active && !isLoading && isQishui && albumInfo != null && albumInfo!!.size > albumSongs.size) {
                item {
                    Text("当前显示 ${albumSongs.size} 首，专辑标注 ${albumInfo!!.size} 首；部分歌曲暂不可用",
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp),
                        style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                }
            }

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
            } else if (loadError != null || visibleSongs.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = loadError ?: if (pageSearch.query.isNotBlank()) "没有找到匹配的歌曲" else "暂无歌曲",
                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            } else {
                itemsIndexed(visibleSongs) { index, track ->
                    SharedTrackRow(
                        track = track,
                        isPlaying = track.id == currentTrack?.id && track.source == currentTrack.source,
                        onClick = { onTrackClick(track, albumSongs) },
                        onMoreClick = { selectedTrack = track; showMoreSheet = true }
                    )
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
                    TrackMoreActions(liked = selectedLiked == true,
                        likeLabel = when { likeCheckFailed -> "收藏状态获取失败"; selectedLiked == null -> "正在获取收藏状态"; selectedLiked == true -> "取消收藏"; else -> "收藏" },
                        likeEnabled = selectedTrack != null && selectedLiked != null,
                        onShare = { showMoreSheet = false; selectedTrack?.let { onShare(it) } },
                        onLike = {
                            showMoreSheet = false
                            if (selectedLiked == true) showUnlikeDialog = true
                            else selectedTrack?.let { onSetTrackLiked(it, true) }
                        },
                        onComment = { showMoreSheet = false; selectedTrack?.let(onNavigateToComment) })
                }

                // 歌手区域
                item { AddTrackToPlaylistAction(selectedTrack) }
                item { SmallTitle("歌手") }
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
                            val artistNames = track.artistNames
                            val artistIds = track.artistIds
                            if (artistNames.isNotEmpty()) {
                                artistNames.forEachIndexed { artistIndex, artistName ->
                                    val artistId = if (artistIndex < artistIds.size) artistIds[artistIndex] else ""
                                    ArrowPreference(
                                        title = "歌手：$artistName",
                                        onClick = {
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
                                        showMoreSheet = false
                                        if (track.artistId.isNotEmpty()) {
                                            onNavigateToArtist(track.artistId)
                                        }
                                    }
                                )
                            }
                        }
                        // 显示专辑歌手
                        val currentAlbumInfo = albumInfo
                        if (currentAlbumInfo != null && currentAlbumInfo.artist.isNotEmpty() && currentAlbumInfo.artistId > 0) {
                            ArrowPreference(
                                title = "专辑歌手：${currentAlbumInfo.artist}",
                                onClick = {
                                    showMoreSheet = false
                                    onNavigateToArtist("neartist_${currentAlbumInfo.artistId}")
                                }
                            )
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
                        selectedTrack?.let { onSetTrackLiked(it, false) }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun AlbumHeader(
    albumInfo: AlbumInfo?,
    initialCoverUrl: String,
    sharedCoverKey: String?,
    trackCount: Int,
    onPlayAllClick: () -> Unit,
    onShuffleClick: () -> Unit,
    showFavorite: Boolean,
    favoriteCollected: Boolean,
    favoriteEnabled: Boolean,
    onFavorite: () -> Unit
) {
    val details = buildList {
        if (!albumInfo?.artist.isNullOrEmpty()) add("歌手：${albumInfo?.artist}")
        if (albumInfo != null && albumInfo.publishTime > 0) add("发行时间：${formatPublishTime(albumInfo.publishTime)}")
        add("$trackCount 首歌曲")
    }
    ResponsiveMediaHeader(title = albumInfo?.name.orEmpty(), details = details, showFavorite = showFavorite,
        favoriteCollected = favoriteCollected, favoriteEnabled = favoriteEnabled,
        onFavorite = onFavorite, onPlay = onPlayAllClick, onShuffle = onShuffleClick) { coverModifier ->
        AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER),
            model = albumInfo?.picUrl?.ifBlank { initialCoverUrl } ?: initialCoverUrl,
            contentDescription = albumInfo?.name,
            modifier = coverModifier.clip(RoundedCornerShape(12.dp)).sharedCover(sharedCoverKey),
            contentScale = ContentScale.Crop, error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter())
    }

}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "--:--"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun formatPublishTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
    return format.format(date)
}
