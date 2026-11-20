package com.hypermusic.miuix.ui.pages
import androidx.compose.material.icons.rounded.MyLocation
import com.hypermusic.miuix.data.cache.*
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.ui.components.pageNavigationIconInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import com.hypermusic.miuix.ui.navigation.BackHandler
import com.hypermusic.miuix.ui.components.rememberPageContentSearch
import com.hypermusic.miuix.ui.components.PageContentSearchAction
import com.hypermusic.miuix.ui.components.PageContentSearchBar
import com.hypermusic.miuix.ui.components.matchesPageQuery
import com.hypermusic.miuix.ui.components.TrackMoreActions
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.sharedCover
import com.hypermusic.miuix.ui.components.albumCoverKey
import com.hypermusic.miuix.ui.components.AsyncImage
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.ArtistInfo
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
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
import com.hypermusic.miuix.ui.FeedBottomPads
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.ui.unit.DpSize

@Composable
fun ArtistPage(
    artistId: String,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onAlbumClick: (Long, String) -> Unit,
    currentTrack: Track? = null,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    onShare: (Track) -> Unit = {},
    onLikeTrack: (Track) -> Unit = {},
    onDownload: (Track) -> Unit = {},
    onDownloadCover: (Track) -> Unit = {},


    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToComment: (Track) -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val isQq = artistId.startsWith("qqartist_")
    val isBodian = artistId.startsWith("bodianartist_")
    val isKugou = artistId.startsWith("kugouartist_")
    val isQishui = artistId.startsWith("qishuiartist_")
    val qqMid = artistId.removePrefix("qqartist_")
    val bodianId = artistId.removePrefix("bodianartist_")
    val kugouId = artistId.removePrefix("kugouartist_")
    val qishuiId = artistId.removePrefix("qishuiartist_")
    val context = androidx.compose.ui.platform.LocalContext.current
    val bodianCatalog = remember(context, isBodian) {
        if (isBodian) com.hypermusic.miuix.data.api.BodianCatalog(com.hypermusic.miuix.data.api.BodianClient(context)) else null
    }
    val kugouCatalog = remember(context, isKugou) {
        if (isKugou) com.hypermusic.miuix.data.api.KugouCatalog(com.hypermusic.miuix.data.api.KugouClient(context)) else null
    }
    val qishuiCatalog = remember(context, isQishui) {
        if (isQishui) com.hypermusic.miuix.data.api.QishuiCatalog(com.hypermusic.miuix.data.api.QishuiClient(context)) else null
    }
    var qishuiSongsLimited by remember(artistId) { mutableStateOf(false) }
    var qishuiAlbumsLimited by remember(artistId) { mutableStateOf(false) }
    suspend fun bodianInfo(): Result<ArtistInfo> = try { Result.success(com.hypermusic.miuix.data.api.BodianCatalog.parseArtist(requireNotNull(bodianCatalog).artistDetail(bodianId))) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun bodianSongs(offset: Int): Result<com.hypermusic.miuix.data.api.BodianCatalog.Page<Track>> = try {
        var cursor = offset
        var page = requireNotNull(bodianCatalog).artistSongs(bodianId, cursor, 50)
        var attempts = 0
        while (page.items.isEmpty() && page.hasMore) {
            check(page.nextOffset > cursor && attempts++ < 200) { "波点音乐歌曲分页异常" }
            cursor = page.nextOffset
            page = bodianCatalog.artistSongs(bodianId, cursor, 50)
        }
        Result.success(page)
    }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun bodianAlbums(offset: Int): Result<com.hypermusic.miuix.data.api.BodianCatalog.Page<AlbumInfo>> = try {
        var cursor = offset
        var page = requireNotNull(bodianCatalog).artistAlbums(bodianId, cursor, 50)
        var attempts = 0
        while (page.items.isEmpty() && page.hasMore) {
            check(page.nextOffset > cursor && attempts++ < 200) { "波点音乐专辑分页异常" }
            cursor = page.nextOffset
            page = bodianCatalog.artistAlbums(bodianId, cursor, 50)
        }
        Result.success(page)
    }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun kugouInfo(): Result<ArtistInfo> = try { Result.success(requireNotNull(kugouCatalog).artist(kugouId)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun kugouSongs(offset: Int): Result<List<Track>> = try { Result.success(requireNotNull(kugouCatalog).artistSongs(kugouId, offset, 50)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun kugouAlbums(offset: Int): Result<List<AlbumInfo>> = try { Result.success(requireNotNull(kugouCatalog).artistAlbums(kugouId, offset, 50)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    suspend fun qishuiInfo(): Result<ArtistInfo> = try { Result.success(com.hypermusic.miuix.data.api.QishuiCatalog.parseArtist(requireNotNull(qishuiCatalog).artistDetail(qishuiId))) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) { try { Result.success(com.hypermusic.miuix.data.api.QishuiArtistWorks.artist(qishuiId)) }
            catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (fallback: Exception) { Result.failure(fallback) } }
    suspend fun qishuiSongs(cursor: String): Result<com.hypermusic.miuix.data.api.QishuiArtistWorks.Page<Track>> = try { Result.success(com.hypermusic.miuix.data.api.QishuiArtistWorks.songs(qishuiId, cursor)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (cursor.isNotEmpty()) Result.failure(e) else try {
                val fallback = requireNotNull(qishuiCatalog).artistSongs(qishuiId, 0, 50)
                qishuiSongsLimited = true
                Result.success(com.hypermusic.miuix.data.api.QishuiArtistWorks.Page(fallback.items, null, false))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (fallback: Exception) { Result.failure(fallback) }
        }
    suspend fun qishuiAlbums(cursor: String): Result<com.hypermusic.miuix.data.api.QishuiArtistWorks.Page<AlbumInfo>> = try { Result.success(com.hypermusic.miuix.data.api.QishuiArtistWorks.albums(qishuiId, cursor)) }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (e: Exception) {
            if (cursor.isNotEmpty()) Result.failure(e) else try {
                val fallback = com.hypermusic.miuix.data.api.QishuiShareAlbum.artistAlbums(qishuiId)
                qishuiAlbumsLimited = true
                Result.success(com.hypermusic.miuix.data.api.QishuiArtistWorks.Page(fallback, null, false))
            } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
            catch (fallback: Exception) { Result.failure(fallback) }
        }
    val songsPageSize = if (isQq) 30 else 50
    val scrollBehavior = MiuixScrollBehavior()
    val pageSearch = rememberPageContentSearch("artist:$artistId")
    val pullToRefreshState = rememberPullToRefreshState()
    var isRefreshing by remember { mutableStateOf(false) }
    var artistInfo by remember { mutableStateOf<ArtistInfo?>(null) }
    var artistSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    var artistAlbums by remember { mutableStateOf<List<AlbumInfo>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var songsOffset by remember { mutableIntStateOf(0) }
    var qishuiSongsCursor by remember(artistId) { mutableStateOf("") }
    var qishuiAlbumsCursor by remember(artistId) { mutableStateOf("") }
    val qishuiSongCursors = remember(artistId) { mutableSetOf("") }
    val qishuiAlbumCursors = remember(artistId) { mutableSetOf("") }
    var albumsOffset by remember { mutableIntStateOf(0) }
    var hasMoreSongs by remember { mutableStateOf(true) }
    var hasMoreAlbums by remember { mutableStateOf(true) }
    var isLoadingMoreSongs by remember { mutableStateOf(false) }
    var isLoadingMoreAlbums by remember { mutableStateOf(false) }
    var moreSongsError by remember(artistId) { mutableStateOf(false) }
    var moreAlbumsError by remember(artistId) { mutableStateOf(false) }
    var songsLoadError by remember(artistId) { mutableStateOf(false) }
    var albumsLoadError by remember(artistId) { mutableStateOf(false) }
    var loadMoreSongsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var loadMoreAlbumsJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val coroutineScope = rememberCoroutineScope()
    var showMoreSheet by remember { mutableStateOf(false) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()
    val searchListState = rememberLazyListState()
    val displayedListState = if (pageSearch.active) searchListState else listState
    val visibleSongs = remember(artistSongs, pageSearch.query) {
        artistSongs.filter { matchesPageQuery(pageSearch.query, it.title, it.artist, it.album) }
    }
    val visibleAlbums = remember(artistAlbums, pageSearch.query) {
        artistAlbums.filter { matchesPageQuery(pageSearch.query, it.name, it.artist) }
    }
    LaunchedEffect(pageSearch.query, selectedTab) { searchListState.scrollToItem(0) }
    val currentTrackIndex = remember(visibleSongs, currentTrack) {
        visibleSongs.indexOfFirst { it.id == currentTrack?.id && it.source == currentTrack.source }
    }
    val avatarContext = LocalContext.current
    val avatarSize = with(LocalDensity.current) { 240.dp.roundToPx() }
    val artistAvatar = rememberAsyncImagePainter(
        model = remember(avatarContext, artistInfo?.picUrl, avatarSize) {
            ImageRequest.Builder(avatarContext).data(artistInfo?.picUrl).size(avatarSize).build()
        },
        imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(avatarContext, com.hypermusic.miuix.data.cache.ImageCaches.Kind.ARTIST),
        error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter(),
        fallback = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter()
    )

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

    // 加载更多单曲
    fun loadMoreSongs() {
        if (isLoading || isLoadingMoreSongs || !hasMoreSongs) return
        moreSongsError = false
        isLoadingMoreSongs = true
        loadMoreSongsJob = coroutineScope.launch {
            if (isQishui) {
                qishuiSongs(qishuiSongsCursor).fold(onSuccess = { page ->
                    qishuiSongCursors.add(qishuiSongsCursor)
                    artistSongs = (artistSongs + page.items).distinctBy { it.id }
                    hasMoreSongs = page.more && page.cursor != null && page.cursor !in qishuiSongCursors
                    qishuiSongsCursor = page.cursor.orEmpty()
                }, onFailure = { moreSongsError = true })
                isLoadingMoreSongs = false
                return@launch
            }
            if (isBodian) {
                bodianSongs(songsOffset).fold(onSuccess = { page ->
                    artistSongs = artistSongs + page.items
                    hasMoreSongs = page.hasMore && page.nextOffset > songsOffset
                    songsOffset = page.nextOffset
                }, onFailure = { moreSongsError = true })
                isLoadingMoreSongs = false
                return@launch
            }
            val result = if (isKugou) kugouSongs(songsOffset) else if (isQq) com.hypermusic.miuix.data.api.QqMusicApi.getArtistSongs(qqMid, offset = songsOffset)
                else NeteaseApi.getArtistSongs(artistId, offset = songsOffset)
            result.fold(
                onSuccess = { songs ->
                    if (songs.isEmpty()) {
                        hasMoreSongs = false
                    } else {
                        artistSongs = artistSongs + songs
                        songsOffset += songs.size
                        if (songs.size < songsPageSize) hasMoreSongs = false
                    }
                },
                onFailure = { moreSongsError = true }
            )
            isLoadingMoreSongs = false
        }
    }

    // 加载更多专辑
    fun loadMoreAlbums() {
        if (isLoading || isLoadingMoreAlbums || !hasMoreAlbums) return
        moreAlbumsError = false
        isLoadingMoreAlbums = true
        loadMoreAlbumsJob = coroutineScope.launch {
            if (isQishui) {
                qishuiAlbums(qishuiAlbumsCursor).fold(onSuccess = { page ->
                    qishuiAlbumCursors.add(qishuiAlbumsCursor)
                    artistAlbums = (artistAlbums + page.items).distinctBy { it.id }
                    hasMoreAlbums = page.more && page.cursor != null && page.cursor !in qishuiAlbumCursors
                    qishuiAlbumsCursor = page.cursor.orEmpty()
                }, onFailure = { moreAlbumsError = true })
                isLoadingMoreAlbums = false
                return@launch
            }
            if (isBodian) {
                bodianAlbums(albumsOffset).fold(onSuccess = { page ->
                    artistAlbums = artistAlbums + page.items
                    hasMoreAlbums = page.hasMore && page.nextOffset > albumsOffset
                    albumsOffset = page.nextOffset
                }, onFailure = { moreAlbumsError = true })
                isLoadingMoreAlbums = false
                return@launch
            }
            val result = if (isKugou) kugouAlbums(albumsOffset) else if (isQq) com.hypermusic.miuix.data.api.QqMusicApi.getArtistAlbums(qqMid, offset = albumsOffset)
                else NeteaseApi.getArtistAlbums(artistId, offset = albumsOffset)
            result.fold(
                onSuccess = { albums ->
                    if (albums.isEmpty()) {
                        hasMoreAlbums = false
                    } else {
                        artistAlbums = artistAlbums + albums
                        albumsOffset += albums.size
                        if (albums.size < 50) hasMoreAlbums = false
                    }
                },
                onFailure = { moreAlbumsError = true }
            )
            isLoadingMoreAlbums = false
        }
    }

    val cachePlatform = when { isQishui -> MusicPlatform.QISHUI; isKugou -> MusicPlatform.KUGOU; isBodian -> MusicPlatform.BODIAN; isQq -> MusicPlatform.QQ; else -> MusicPlatform.NETEASE }
    val pageCacheKey = PageCache.key(context, cachePlatform, "artist", artistId)
    var displayedCacheKey by remember { mutableStateOf<String?>(null) }
    var refreshGeneration by remember(artistId) { mutableIntStateOf(0) }
    var loadGeneration by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageCacheKey, refreshGeneration) {
        val generation = ++loadGeneration
        loadMoreSongsJob?.cancel(); loadMoreAlbumsJob?.cancel()
        isLoadingMoreSongs = false; isLoadingMoreAlbums = false
        moreSongsError = false; moreAlbumsError = false; songsLoadError = false; albumsLoadError = false
        isLoading = true
        if (displayedCacheKey != pageCacheKey) {
            artistInfo = null; artistSongs = emptyList(); artistAlbums = emptyList(); displayedCacheKey = pageCacheKey
            PageCache.read<CachedArtist>(context, pageCacheKey)?.let { cached ->
                artistInfo = cached.info; artistSongs = cached.songs; artistAlbums = cached.albums
                songsOffset = cached.songsOffset; albumsOffset = cached.albumsOffset
                hasMoreSongs = cached.moreSongs; hasMoreAlbums = cached.moreAlbums
                qishuiSongsCursor = cached.songsCursor; qishuiAlbumsCursor = cached.albumsCursor
            }
        }
        val oldSongsOffset = songsOffset; val oldAlbumsOffset = albumsOffset
        val oldSongsCursor = qishuiSongsCursor; val oldAlbumsCursor = qishuiAlbumsCursor
        val oldMoreSongs = hasMoreSongs; val oldMoreAlbums = hasMoreAlbums
        songsOffset = 0; albumsOffset = 0; qishuiSongsCursor = ""; qishuiAlbumsCursor = ""
        qishuiSongCursors.clear(); qishuiAlbumCursors.clear()
        qishuiSongsLimited = false; qishuiAlbumsLimited = false
        hasMoreSongs = true; hasMoreAlbums = true
        try {
            val info = if (isQishui) qishuiInfo() else if (isKugou) kugouInfo() else if (isBodian) bodianInfo()
                else if (isQq) com.hypermusic.miuix.data.api.QqMusicApi.getArtistInfo(qqMid) else NeteaseApi.getArtistInfo(artistId)
            info.onSuccess { artistInfo = it }
            try {
                val fresh = mutableListOf<Track>()
                var pages = 0
                do {
                    check(pages++ < 500) { "歌手单曲分页异常" }
                    val before = songsOffset
                    val page = if (isBodian) bodianSongs(songsOffset).getOrThrow().let { result ->
                        songsOffset = result.nextOffset; hasMoreSongs = result.hasMore && result.nextOffset > before; result.items
                    } else if (isQishui) qishuiSongs(qishuiSongsCursor).getOrThrow().let { result ->
                        qishuiSongCursors.add(qishuiSongsCursor)
                        hasMoreSongs = result.more && result.cursor != null && result.cursor !in qishuiSongCursors
                        qishuiSongsCursor = result.cursor.orEmpty(); result.items
                    } else {
                        val rows = (if (isKugou) kugouSongs(songsOffset) else if (isQq) com.hypermusic.miuix.data.api.QqMusicApi.getArtistSongs(qqMid, offset = songsOffset)
                            else NeteaseApi.getArtistSongs(artistId, offset = songsOffset)).getOrThrow()
                        songsOffset += rows.size; hasMoreSongs = rows.size >= songsPageSize; rows
                    }
                    fresh += page
                    check(!hasMoreSongs || page.isNotEmpty()) { "歌手单曲分页未前进" }
                } while (hasMoreSongs && fresh.size < artistSongs.size)
                artistSongs = reconcilePageItems(artistSongs, fresh.distinctBy { it.source to it.id }) { it.source to it.id }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                songsLoadError = true; songsOffset = oldSongsOffset; qishuiSongsCursor = oldSongsCursor
                hasMoreSongs = if (artistSongs.isEmpty()) false else oldMoreSongs
            }
            try {
                val fresh = mutableListOf<AlbumInfo>()
                var pages = 0
                do {
                    check(pages++ < 500) { "歌手专辑分页异常" }
                    val before = albumsOffset
                    val page = if (isBodian) bodianAlbums(albumsOffset).getOrThrow().let { result ->
                        albumsOffset = result.nextOffset; hasMoreAlbums = result.hasMore && result.nextOffset > before; result.items
                    } else if (isQishui) qishuiAlbums(qishuiAlbumsCursor).getOrThrow().let { result ->
                        qishuiAlbumCursors.add(qishuiAlbumsCursor)
                        hasMoreAlbums = result.more && result.cursor != null && result.cursor !in qishuiAlbumCursors
                        qishuiAlbumsCursor = result.cursor.orEmpty(); result.items
                    } else {
                        val rows = (if (isKugou) kugouAlbums(albumsOffset) else if (isQq) com.hypermusic.miuix.data.api.QqMusicApi.getArtistAlbums(qqMid, offset = albumsOffset)
                            else NeteaseApi.getArtistAlbums(artistId, offset = albumsOffset)).getOrThrow()
                        albumsOffset += rows.size; hasMoreAlbums = rows.size >= 50; rows
                    }
                    fresh += page
                    check(!hasMoreAlbums || page.isNotEmpty()) { "歌手专辑分页未前进" }
                } while (hasMoreAlbums && fresh.size < artistAlbums.size)
                artistAlbums = reconcilePageItems(artistAlbums, fresh.distinctBy { it.id }) { it.id }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) {
                albumsLoadError = true; albumsOffset = oldAlbumsOffset; qishuiAlbumsCursor = oldAlbumsCursor
                hasMoreAlbums = if (artistAlbums.isEmpty()) false else oldMoreAlbums
            }
        } finally { if (generation == loadGeneration) { isLoading = false; isRefreshing = false } }
    }
    LaunchedEffect(pageCacheKey, artistInfo, artistSongs, artistAlbums, isLoading, isLoadingMoreSongs, isLoadingMoreAlbums) {
        if (isLoading || isLoadingMoreSongs || isLoadingMoreAlbums || (artistInfo == null && artistSongs.isEmpty() && artistAlbums.isEmpty())) return@LaunchedEffect
        PageCache.write(context, pageCacheKey, CachedArtist(artistInfo, artistSongs, artistAlbums,
            songsOffset, albumsOffset, hasMoreSongs, hasMoreAlbums, qishuiSongsCursor, qishuiAlbumsCursor))
    }
    val songKeys = remember(visibleSongs) { stablePageItemKeys(visibleSongs) { it.source + ":" + it.id } }
    val albumKeys = remember(visibleAlbums) { stablePageItemKeys(visibleAlbums) { it.id.toString() } }

    LaunchedEffect(pageSearch.active, pageSearch.query, selectedTab, isLoading,
        artistSongs.size, artistAlbums.size, hasMoreSongs, hasMoreAlbums,
        isLoadingMoreSongs, isLoadingMoreAlbums, moreSongsError, moreAlbumsError) {
        if (pageSearch.active && pageSearch.query.isNotBlank() && !isLoading) {
            if (selectedTab == 0 && hasMoreSongs && !isLoadingMoreSongs && !moreSongsError) loadMoreSongs()
            if (selectedTab == 1 && hasMoreAlbums && !isLoadingMoreAlbums && !moreAlbumsError) loadMoreAlbums()
        }
    }
    val showCollapsedArtistAvatar = artistInfo != null && scrollBehavior.state.collapsedFraction >= 0.99f
    val blurBackdrop = rememberPageBlurBackdrop()

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                Box {
                    TopAppBar(
                        title = if (showCollapsedArtistAvatar) "" else artistInfo?.name.orEmpty(),
                        largeTitle = "歌手",
                        color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                        scrollBehavior = scrollBehavior,
                        actions = {
                            PageContentSearchAction(pageSearch)
                            com.hypermusic.miuix.ui.components.BatchDownloadMenu(artistSongs, enabled = !isLoading, loadAll = {
                                var pages = 0
                                while (hasMoreSongs) {
                                    check(pages++ < 1000) { "歌手歌曲分页过多，请重试" }
                                    val before = artistSongs.size
                                    if (isLoadingMoreSongs) loadMoreSongsJob?.join() else {
                                        loadMoreSongs()
                                        loadMoreSongsJob?.join()
                                    }
                                    check(!moreSongsError) { "加载歌手歌曲失败，请重试" }
                                    check(!hasMoreSongs || artistSongs.size > before) { "歌手歌曲分页没有返回新内容" }
                                }
                                artistSongs
                            })
                        },
                        bottomContent = { PageContentSearchBar(pageSearch, if (selectedTab == 0) "搜索歌手的单曲" else "搜索歌手的专辑") },
                        defaultWindowInsetsPadding = false,
                        navigationIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "返回",
                                modifier = Modifier
                                    .padding(start = pageNavigationIconInset())
                                    .clickable { if (pageSearch.active) pageSearch.close() else onBackClick() }
                            )

                        }
                    )
                    if (showCollapsedArtistAvatar) {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                                .padding(start = 68.dp, end = 112.dp)
                                .height(top.yukonga.miuix.kmp.basic.TopAppBarDefaults.CollapsedHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Image(painter = artistAvatar, contentDescription = "歌手头像",
                                modifier = Modifier.size(28.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                            Spacer(Modifier.width(8.dp))
                            Text(artistInfo?.name.orEmpty(), modifier = Modifier.weight(1f, fill = false),
                                fontSize = MiuixTheme.textStyles.title3.fontSize, fontWeight = FontWeight.Medium,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        floatingActionButton = {
            if (!pageSearch.active && !isLoading && selectedTab == 0 && artistSongs.isNotEmpty() && currentTrackIndex >= 0) {
                FloatingActionButton(
                    onClick = { coroutineScope.launch { displayedListState.animateScrollToItem(currentTrackIndex + 2) } },
                    modifier = Modifier.padding(bottom = 80.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MyLocation,
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
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding())),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = pageViewportTopInset(paddingValues.calculateTopPadding()))
        ) {
        LazyColumn(
            state = displayedListState,
            modifier = Modifier
                .fillMaxSize()
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(paddingValues.calculateTopPadding()), bottom = FeedBottomPads.Standard),
            overscrollEffect = null
        ) {
            // 歌手信息区域
            if (pageSearch.active && pageSearch.query.isBlank()) return@LazyColumn
            if (!pageSearch.active) item {
                ArtistInfoHeader(artistInfo = artistInfo, avatar = artistAvatar, showMvCount = !isQq)
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
            if (!pageSearch.active && !isLoading && ((selectedTab == 0 && qishuiSongsLimited) || (selectedTab == 1 && qishuiAlbumsLimited))) {
                item { Text("完整列表暂时不可用，当前显示部分作品", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)) }
            }

            // 内容区域
            when (selectedTab) {
                0 -> {
                    // 单曲列表
                    if (isLoading && artistSongs.isEmpty()) {
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
                    } else if (visibleSongs.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (songsLoadError) TextButton(text = "单曲加载失败，点击重试", onClick = { refreshGeneration++ })
                                else if (pageSearch.query.isNotBlank() && moreSongsError) TextButton(text = "搜索加载失败，点击重试", onClick = { loadMoreSongs() })
                                else Text(text = if (pageSearch.query.isNotBlank()) {
                                    if (hasMoreSongs) "正在搜索…" else "没有找到匹配的单曲"
                                } else "暂无单曲", color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        itemsIndexed(visibleSongs, key = { index, _ -> songKeys[index] }) { index, track ->
                            // 当滚动到接近底部时触发加载更多
                            if (!isLoading && index >= visibleSongs.size - 5 && hasMoreSongs && !isLoadingMoreSongs && !moreSongsError) {
                                LaunchedEffect(Unit) {
                                    loadMoreSongs()
                                }
                            }
                            Card(
                                modifier = Modifier.animateItem()
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 4.dp),
                                colors = CardDefaults.defaultColors(
                                    color = com.hypermusic.miuix.ui.components.songRowBackground(track.id == currentTrack?.id && track.source == currentTrack?.source)
                                ),
                                pressFeedbackType = PressFeedbackType.Sink,
                                showIndication = true,
                                onClick = { onTrackClick(track, artistSongs) }
                            ) {
                                BasicComponent(
                                    modifier = Modifier.fillMaxWidth(),
                                    startAction = {
                                        AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL), model = track.imgUrl,
                                            contentDescription = track.title,
                                            modifier = Modifier
                                                .size(48.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop,
                                            error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter()
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
                                        color = if (track.id == currentTrack?.id && track.source == currentTrack?.source) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
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
                                            color = if (track.id == currentTrack?.id && track.source == currentTrack?.source) MiuixTheme.colorScheme.primary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
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
                        if (moreSongsError) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                TextButton(text = "加载失败，点击重试", onClick = { loadMoreSongs() })
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
                    } else if (visibleAlbums.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (albumsLoadError) TextButton(text = "专辑加载失败，点击重试", onClick = { refreshGeneration++ })
                                else if (pageSearch.query.isNotBlank() && moreAlbumsError) TextButton(text = "搜索加载失败，点击重试", onClick = { loadMoreAlbums() })
                                else Text(text = if (pageSearch.query.isNotBlank()) {
                                    if (hasMoreAlbums) "正在搜索…" else "没有找到匹配的专辑"
                                } else "暂无专辑", color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                    } else {
                        itemsIndexed(visibleAlbums, key = { index, _ -> albumKeys[index] }) { index, album ->
                            // 当滚动到接近底部时触发加载更多
                            if (!isLoading && index >= visibleAlbums.size - 5 && hasMoreAlbums && !isLoadingMoreAlbums && !moreAlbumsError) {
                                LaunchedEffect(Unit) {
                                    loadMoreAlbums()
                                }
                            }
                            AlbumItem(
                                modifier = Modifier.animateItem(),
                                album = album,
                                onClick = { onAlbumClick(album.id, album.picUrl) },
                                isQq = isQq
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
                        if (moreAlbumsError) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                TextButton(text = "加载失败，点击重试", onClick = { loadMoreAlbums() })
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
                item {
                    TrackMoreActions(liked = selectedTrack?.id == currentTrack?.id && selectedTrack?.source == currentTrack?.source && isCurrentTrackLiked,
                        onShare = { showMoreSheet = false; selectedTrack?.let { onShare(it) } },
                        onLike = {
                            showMoreSheet = false
                            if (selectedTrack?.id == currentTrack?.id && selectedTrack?.source == currentTrack?.source && isCurrentTrackLiked) showUnlikeDialog = true
                            else selectedTrack?.let(onLikeTrack)
                        },
                        onComment = { showMoreSheet = false; selectedTrack?.let(onNavigateToComment) })
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
                            color = MiuixTheme.colorScheme.surfaceContainer
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
                            color = MiuixTheme.colorScheme.surfaceContainer
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
                        selectedTrack?.let(onLikeTrack)
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                )
            }
        },
    )
}

@Composable
private fun ArtistInfoHeader(artistInfo: ArtistInfo?, avatar: Painter, showMvCount: Boolean = true) {
    if (artistInfo == null) return

    val screenWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    if (screenWidth < 600) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = avatar, contentDescription = artistInfo.name,
                modifier = Modifier.size((screenWidth * 0.65f).coerceIn(140f, 240f).dp).clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(16.dp))
            Text(text = artistInfo.name, style = MiuixTheme.textStyles.title3, fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text(text = "${artistInfo.albumSize} 张专辑，${artistInfo.musicSize} 首单曲" + if (showMvCount) "，${artistInfo.mvSize} 部MV" else "",
                fontSize = 14.sp, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            if (artistInfo.briefDesc.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                Text(text = artistInfo.briefDesc, fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth(), maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 歌手图片
        Image(painter = avatar,
            contentDescription = artistInfo.name,
            modifier = Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(40.dp)),
            contentScale = ContentScale.Crop
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
                text = "${artistInfo.albumSize} 张专辑，${artistInfo.musicSize} 首单曲" +
                    if (showMvCount) "，${artistInfo.mvSize} 部MV" else "",
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
    onClick: () -> Unit,
    isQq: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(
            color = MiuixTheme.colorScheme.secondaryContainer
        ),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        BasicComponent(
            modifier = Modifier.fillMaxWidth(),
            startAction = {
                AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL), model = album.picUrl,
                    contentDescription = album.name,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .sharedCover(albumCoverKey(album.id, isQq)),
                    contentScale = ContentScale.Crop,
                    error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter()
                )
                Spacer(modifier = Modifier.width(12.dp))
            }
        ) {
            Text(
                text = album.name,
                fontSize = MiuixTheme.textStyles.headline1.fontSize,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${album.size} 首歌曲",
                fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
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
