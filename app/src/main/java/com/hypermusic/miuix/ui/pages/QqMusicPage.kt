package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import com.hypermusic.miuix.ui.components.AsyncImage
import android.graphics.BitmapFactory
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.model.RecommendPlaylist
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.PlatformSwitch
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.sharedCover
import com.hypermusic.miuix.ui.components.playlistCoverKey
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun QqMusicPage(tab: Int, viewModel: MusicPlayerViewModel, onPlaylistClick: (String, String, String) -> Unit,
                onLoginClick: () -> Unit, onAlbumClick: (Long, String) -> Unit = { _, _ -> }, onSearchClick: () -> Unit = {}, showSearchAction: Boolean = true,
                onTopChartClick: (QqMusicApi.TopChart) -> Unit = {},
                onFeatureClick: (com.hypermusic.miuix.data.model.PlaylistDetail) -> Unit = {},
                onLocalMusicClick: () -> Unit = {}) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val blurBackdrop = rememberPageBlurBackdrop()
    val tabActive = com.hypermusic.miuix.ui.components.LocalMainTabActive.current
    LaunchedEffect(tab, QqMusicApi.dataAccountId, tabActive) {
        if (tabActive && tab == 0) viewModel.loadQqHome()
    }
    LaunchedEffect(tab, QqMusicApi.dataAccountId, tabActive) {
        if (tabActive && tab != 0) { viewModel.loadQqFavorites(); viewModel.loadQqFavoriteAlbums() }
    }
    val playlists = if (tab == 0) viewModel.qqHomePlaylists else viewModel.qqFavoritePlaylists
    val features = viewModel.qqHomeFeatures
    val error = if (tab == 0) viewModel.qqHomeError else viewModel.qqFavoriteError
    val loading = if (tab == 0) viewModel.qqHomeLoading else viewModel.qqFavoriteLoading
    Scaffold(topBar = {
        BlurredBar(backdrop = blurBackdrop) {
            TopAppBar(title = if (tab == 0) "推荐" else "我的收藏", largeTitle = if (tab == 0) "推荐" else "我的收藏",
                actions = { com.hypermusic.miuix.ui.components.SearchPlatformActions(onSearchClick, showSearchAction && (tab != 0 || scrollBehavior.state.collapsedFraction >= 0.99f)) }, scrollBehavior = scrollBehavior,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = false)
        }
    }) { padding ->
        PullToRefresh(
            isRefreshing = loading,
            onRefresh = { if (tab == 0) viewModel.loadQqHome(force = true) else { viewModel.loadQqFavorites(force = true); viewModel.loadQqFavoriteAlbums(force = true) } },
            pullToRefreshState = pullToRefreshState,
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding())),
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = pageViewportTopInset(padding.calculateTopPadding())).pageLayerBackdrop(blurBackdrop)
        ) {
        LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = if (tab == 0) PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()) + 8.dp, bottom = FeedBottomPads.More)
                else PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()) + 12.dp, bottom = FeedBottomPads.More),
            overscrollEffect = null) {
            if (tab == 1) item { com.hypermusic.miuix.ui.components.LocalMusicCard(onLocalMusicClick) }
            if (tab == 0) {
                if (showSearchAction) item { SearchEntry(onSearchClick) }
                item { SmallTitle("专属推荐") }
                item {
                    LazyRow(modifier = Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(listOf(QqMusicApi.HomeFeature.RADAR, QqMusicApi.HomeFeature.DAILY,
                            QqMusicApi.HomeFeature.NEW_SONGS, QqMusicApi.HomeFeature.GUESS)) { feature ->
                            val result = features[feature]
                            val detail = result?.getOrNull()
                            val cover = detail?.tracks?.firstOrNull()?.imgUrl
                            PlayModeCard(
                                title = feature.title,
                                subtitle = when {
                                    result == null -> "正在加载..."
                                    result.isFailure -> "点击重试"
                                    feature == QqMusicApi.HomeFeature.RADAR -> "为你发现更多好音乐"
                                    feature == QqMusicApi.HomeFeature.GUESS -> "发现你喜欢的歌曲"
                                    feature == QqMusicApi.HomeFeature.DAILY -> "每天为你精选"
                                    else -> "发现最新好歌"
                                },
                                gradientColor = Color(0xFF52666A),
                                backgroundCoverUrl = cover,
                                plainLoadingBackground = true,
                                onClick = {
                                    if (detail != null) onFeatureClick(detail)
                                    else if (result == null) android.widget.Toast.makeText(context, "正在加载${feature.title}", android.widget.Toast.LENGTH_SHORT).show()
                                    else if (feature == QqMusicApi.HomeFeature.DAILY && !QqMusicApi.isLoggedIn()) onLoginClick()
                                    else {
                                        android.widget.Toast.makeText(context, result.exceptionOrNull()?.message ?: "加载失败", android.widget.Toast.LENGTH_SHORT).show()
                                        viewModel.loadQqHome(force = true)
                                    }
                                }
                            ) {
                                if (!cover.isNullOrBlank()) AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(androidx.compose.ui.platform.LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER), model = cover, contentDescription = feature.title,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                item {
                    HomeSection(title = "热榜", lastCardTitle = viewModel.qqHomeCharts.lastOrNull()?.title) {
                        items(viewModel.qqHomeCharts, key = { it.id }) { chart ->
                            CoverCard(modifier = Modifier.animateItem(), coverUrl = chart.cover, title = chart.title,
                                sharedCoverKey = playlistCoverKey("qqtop:${chart.id}"),
                                subtitle = chart.updateDate, onClick = { onTopChartClick(chart) })
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        HomeSection(title = "大家都在听", lastCardTitle = playlists.lastOrNull()?.title) {
                            items(playlists, key = { it.id }) { playlist ->
                                CoverCard(modifier = Modifier.animateItem(), coverUrl = playlist.coverImgUrl, title = playlist.title,
                                    sharedCoverKey = playlistCoverKey(playlist.id),
                                    subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                    onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) })
                            }
                        }
                    }
                }
            } else if (!QqMusicApi.isLoggedIn()) {
                item { Card(onClick = onLoginClick, pressFeedbackType = PressFeedbackType.Sink, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) { BasicComponent(title = "登录QQ音乐", summary = "登录后查看个人歌单") } }
            }
            if (loading) item { Text("正在加载…", modifier = Modifier.padding(16.dp)) }
            error?.let { message -> item { Text(message, modifier = Modifier.padding(16.dp)) } }
            if (tab != 0 && QqMusicApi.isLoggedIn()) {
                val personal = playlists.filter { it.isPersonalCollection }.map { playlist ->
                    playlist.copy(title = com.hypermusic.miuix.data.model.displayQqLikedPlaylistTitle(playlist.title, QqMusicApi.accountName))
                }
                val others = playlists.filterNot { it.isPersonalCollection }
                item { SmallTitle("个人收藏") }
                if (personal.isEmpty() && !loading) {
                    item { Text("暂无收藏的音乐", modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)) }
                }
                items(personal, key = { "playlist:${it.id}" }) { playlist ->
                    UserPlaylistCard(playlist = playlist,
                        onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                        modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))
                }
                if (others.isNotEmpty()) {
                    item { SmallTitle("歌单") }
                    items(others, key = { "playlist:${it.id}" }) { playlist ->
                        UserPlaylistCard(playlist = playlist,
                            onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) },
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))
                    }
                }
                if (viewModel.qqFavoriteAlbums.isNotEmpty()) {
                    item { SmallTitle("专辑") }
                    items(viewModel.qqFavoriteAlbums, key = { "album:${it.id}" }) { album ->
                        UserAlbumCard(album = album, isQq = true,
                            onClick = { onAlbumClick(album.id, album.picUrl) },
                            modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp))
                    }
                }
            }
        }
        }
    }
}

@Composable
fun QqLoginPage(onBack: () -> Unit, onSuccess: () -> Unit) {
    var showImport by remember { mutableStateOf(false) }
    val initialCredentialImport = remember { com.hypermusic.miuix.ui.scan.CredentialImportController.completed }
    val credentialImport = com.hypermusic.miuix.ui.scan.CredentialImportController.completed
    LaunchedEffect(credentialImport) {
        if (credentialImport != null && credentialImport != initialCredentialImport && credentialImport.second == "qq") onSuccess()
    }
    var qr by remember { mutableStateOf<QqMusicApi.UnifiedQr?>(null) }
    var status by remember { mutableStateOf("正在获取二维码...") }
    var statusCode by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var refreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val checkMutex = remember(generation) { kotlinx.coroutines.sync.Mutex() }
    val bitmap = remember(qr) { qr?.let { BitmapFactory.decodeByteArray(it.image, 0, it.image.size) } }

    suspend fun checkStatus(current: QqMusicApi.UnifiedQr): Boolean {
        checkMutex.lock()
        try {
            if (qr !== current || statusCode == 800 || statusCode == 803) return true
            val result = QqMusicApi.nextUnifiedQrEvent(current)
            if (qr !== current) return true
            if (result.isFailure) {
                status = "检查登录状态失败: ${result.exceptionOrNull()?.message}"
                statusCode = 800
                return true
            }
            when (result.getOrThrow()) {
                0 -> { statusCode = 803; status = "登录成功"; onSuccess(); return true }
                65 -> { statusCode = 800; status = "二维码已过期，请刷新"; return true }
                68 -> { statusCode = 800; status = "已取消授权，请刷新二维码重试"; return true }
                66 -> { statusCode = 801; status = "支持QQ手机版、微信、QQ音乐扫码。注意：QQ账号和微信账号数据不互通，属于两个独立账号。" }
                67 -> { statusCode = 802; status = "请在手机上确认登录" }
                69 -> Unit
                else -> { statusCode = 800; status = "扫码状态异常，请刷新二维码"; return true }
            }
            return false
        } finally {
            checkMutex.unlock()
        }
    }

    LaunchedEffect(generation) {
        qr = null
        statusCode = 0
        status = "正在获取二维码..."
        QqMusicApi.createUnifiedQr().fold(
            onSuccess = { qr = it },
            onFailure = { status = "获取二维码失败: ${it.message}" }
        )
        val current = qr ?: return@LaunchedEffect
        statusCode = 801
        status = "支持QQ手机版、微信、QQ音乐扫码。注意：QQ账号和微信账号数据不互通，属于两个独立账号。"
        try {
            while (true) {
                if (checkStatus(current)) break
            }
        } finally {
            current.close()
        }
    }

    val refresh = {
        val current = qr
        if (current == null || statusCode == 800) {
            generation++
        } else {
            refreshJob?.cancel()
            refreshJob = scope.launch {
                status = "正在检查登录状态..."
                checkStatus(current)
            }
        }
        Unit
    }
    LoginPageFrame(onBackClick = onBack, onRefreshClick = refresh) {
        QrLoginLayout(
            isLandscape = configuration.screenHeightDp < configuration.screenWidthDp,
            qrBitmap = bitmap, qqBackground = true, statusText = status, statusCode = statusCode,
            context = context,
            onImportClick = { showImport = true },
            onTimeout = { generation++ }
        )
        LoginCredentialSheet(
            show = showImport,
            description = "请粘贴QQ音乐登录凭据 JSON（需包含 musicid、musickey 和 encryptUin），或选择凭据文件。",
            onDismiss = { showImport = false },
            onImport = { QqMusicApi.importCredential(it) },
            onSuccess = { onSuccess() }
        )
    }
}

@Composable
fun QqCommentPage(songId: Long, title: String, onBack: () -> Unit, modifier: Modifier = Modifier,
                  isBackHandlerEnabled: Boolean = true) {
    var comments by remember(songId) { mutableStateOf<List<com.hypermusic.miuix.data.model.Comment>>(emptyList()) }
    var error by remember(songId) { mutableStateOf<String?>(null) }
    var loading by remember(songId) { mutableStateOf(true) }
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    LaunchedEffect(songId) {
        QqMusicApi.getComments(songId).fold({ comments = it }, { error = it.message })
        loading = false
    }
    com.hypermusic.miuix.ui.navigation.BackHandler(enabled = isBackHandlerEnabled) { onBack() }
    Scaffold(modifier = modifier, topBar = { BlurredBar(backdrop = blurBackdrop) {
        TopAppBar(title = "《${title}》歌曲评论", largeTitle = "歌曲评论", scrollBehavior = scrollBehavior,
            color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            } })
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pageViewportTopInset(padding.calculateTopPadding())).pageLayerBackdrop(blurBackdrop)
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pageScrollTopInset(padding.calculateTopPadding()) + 8.dp, bottom = FeedBottomPads.Standard),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (loading) item { Text("正在加载评论…") }
            error?.let { item { Text(it, modifier = Modifier.padding(16.dp)) } }
            if (!loading && error == null && comments.isEmpty()) item { Text("暂无评论", modifier = Modifier.padding(16.dp)) }
            items(comments) { comment ->
                QqCommentItem(comment)
            }
        }
    }
}

@Composable
fun QqArtistPage(artistMid: String, viewModel: MusicPlayerViewModel, onBack: () -> Unit, albumId: Long? = null) {
    var songs by remember(artistMid, albumId) { mutableStateOf<List<com.hypermusic.miuix.data.model.Track>>(emptyList()) }
    var error by remember(artistMid, albumId) { mutableStateOf<String?>(null) }
    var loading by remember(artistMid, albumId) { mutableStateOf(true) }
    LaunchedEffect(artistMid, albumId) {
        val result = if (albumId != null) QqMusicApi.getAlbumSongs(albumId) else QqMusicApi.getArtistSongs(artistMid)
        result.fold({ songs = it }, { error = it.message })
        loading = false
    }
    com.hypermusic.miuix.ui.navigation.BackHandler { onBack() }
    Scaffold(topBar = { SmallTopAppBar(title = if (albumId != null) "QQ音乐专辑" else "QQ音乐歌手", navigationIcon = {
        Text("返回", modifier = Modifier.clickable { onBack() }.padding(12.dp))
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = pageViewportTopInset(padding.calculateTopPadding())),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = pageScrollTopInset(padding.calculateTopPadding()), bottom = 140.dp)) {
            if (loading) item { Text("正在加载…", modifier = Modifier.padding(16.dp)) }
            error?.let { item { Text(it, modifier = Modifier.padding(16.dp)) } }
            if (!loading && error == null && songs.isEmpty()) item { Text("暂无歌曲", modifier = Modifier.padding(16.dp)) }
            items(songs) { track ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), pressFeedbackType = PressFeedbackType.Sink, onClick = { viewModel.playTrackWithMode(track, songs) }) {
                    BasicComponent(title = track.title, summary = "${track.artist} · ${track.album}")
                }
            }
        }
    }
}
