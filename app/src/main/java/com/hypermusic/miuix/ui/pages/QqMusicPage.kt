package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import coil.compose.AsyncImage
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
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun QqMusicPage(tab: Int, viewModel: MusicPlayerViewModel, onPlaylistClick: (String, String, String) -> Unit,
                onLoginClick: () -> Unit, onSearchClick: () -> Unit = {},
                onFeatureClick: (com.hypermusic.miuix.data.model.PlaylistDetail) -> Unit = {}) {
    var favoritePlaylists by remember(QqMusicApi.dataAccountId) { mutableStateOf<List<RecommendPlaylist>>(emptyList()) }
    var favoriteError by remember(QqMusicApi.dataAccountId) { mutableStateOf<String?>(null) }
    var favoriteLoading by remember(QqMusicApi.dataAccountId) { mutableStateOf(false) }
    var favoriteReload by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val pullToRefreshState = rememberPullToRefreshState()
    val blurBackdrop = rememberPageBlurBackdrop()
    LaunchedEffect(tab, QqMusicApi.dataAccountId) {
        if (tab == 0) viewModel.loadQqHome()
    }
    LaunchedEffect(tab, favoriteReload, QqMusicApi.dataAccountId) {
        if (tab != 0) {
            favoriteLoading = true
            val result = if (QqMusicApi.isLoggedIn()) QqMusicApi.getUserPlaylists() else Result.success(emptyList())
            result.fold({ favoritePlaylists = it; favoriteError = null }, { favoriteError = it.message })
            favoriteLoading = false
        }
    }
    val playlists = if (tab == 0) viewModel.qqHomePlaylists else favoritePlaylists
    val features = viewModel.qqHomeFeatures
    val error = if (tab == 0) viewModel.qqHomeError else favoriteError
    val loading = if (tab == 0) viewModel.qqHomeLoading else favoriteLoading
    Scaffold(topBar = {
        BlurredBar(backdrop = blurBackdrop) {
            TopAppBar(title = if (tab == 0) "推荐" else "我的收藏", largeTitle = if (tab == 0) "推荐" else "我的收藏",
                actions = { com.hypermusic.miuix.ui.components.SearchPlatformActions(onSearchClick, tab != 0 || scrollBehavior.state.collapsedFraction >= 0.99f) }, scrollBehavior = scrollBehavior,
                color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = false)
        }
    }) { padding ->
        PullToRefresh(
            isRefreshing = loading,
            onRefresh = { if (tab == 0) viewModel.loadQqHome(force = true) else favoriteReload++ },
            pullToRefreshState = pullToRefreshState,
            topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = padding.calculateTopPadding()).pageLayerBackdrop(blurBackdrop)
        ) {
        LazyColumn(modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = if (tab == 0) PaddingValues(top = 8.dp, bottom = FeedBottomPads.More)
                else PaddingValues(start = 12.dp, end = 12.dp, bottom = FeedBottomPads.More),
            overscrollEffect = null) {
            if (tab == 0) {
                item { SearchEntry(onSearchClick) }
                item { SmallTitle("专属推荐") }
                item {
                    LazyRow(modifier = Modifier.padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(QqMusicApi.HomeFeature.entries) { feature ->
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
                                if (!cover.isNullOrBlank()) AsyncImage(model = cover, contentDescription = feature.title,
                                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                                else Box(Modifier.fillMaxSize())
                            }
                        }
                    }
                }
                if (playlists.isNotEmpty()) {
                    item {
                        HomeSection(title = "大家都在听") {
                            items(playlists) { playlist ->
                                CoverCard(
                                    coverUrl = playlist.coverImgUrl,
                                    title = playlist.title,
                                    subtitle = if (playlist.playCount > 0) formatRecommendPlayCount(playlist.playCount) else "",
                                    onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }
                                )
                            }
                        }
                    }
                }
            } else if (!QqMusicApi.isLoggedIn()) {
                item { Card(onClick = onLoginClick, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) { BasicComponent(title = "登录QQ音乐", summary = "登录后查看个人歌单") } }
            }
            if (loading) item { Text("正在加载…", modifier = Modifier.padding(16.dp)) }
            error?.let { message -> item { Text(message, modifier = Modifier.padding(16.dp)) } }
            if (tab != 0) items(playlists) { playlist ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    onClick = { onPlaylistClick(playlist.id, playlist.coverImgUrl, playlist.title) }) {
                    BasicComponent(title = playlist.title, summary = "${playlist.trackCount} 首", startAction = {
                        if (playlist.coverImgUrl.isNotBlank()) {
                            AsyncImage(model = playlist.coverImgUrl, contentDescription = null,
                                modifier = Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)))
                            Spacer(Modifier.width(12.dp))
                        }
                    })
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
    var qr by remember { mutableStateOf<QqMusicApi.QrCode?>(null) }
    var status by remember { mutableStateOf("正在获取二维码...") }
    var statusCode by remember { mutableIntStateOf(0) }
    var generation by remember { mutableIntStateOf(0) }
    var selectedLogin by remember { mutableIntStateOf(0) }
    var refreshJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val checkMutex = remember(generation, selectedLogin) { kotlinx.coroutines.sync.Mutex() }
    val bitmap = remember(qr) { qr?.let { BitmapFactory.decodeByteArray(it.image, 0, it.image.size) } }

    suspend fun checkStatus(current: QqMusicApi.QrCode): Boolean {
        checkMutex.lock()
        try {
            if (qr !== current || statusCode == 800 || statusCode == 803) return true
            val result = QqMusicApi.checkQrCode(current)
            if (qr !== current) return true
            if (result.isFailure) {
                status = "检查登录状态失败: ${result.exceptionOrNull()?.message}"
                return false
            }
            when (result.getOrThrow()) {
                0 -> { statusCode = 803; status = "登录成功"; onSuccess(); return true }
                65 -> { statusCode = 800; status = "二维码已过期，请刷新"; return true }
                68 -> { statusCode = 800; status = "已取消授权，请刷新二维码重试"; return true }
                66 -> { statusCode = 801; status = if (current.loginType == 1) "请使用微信扫描二维码" else "请使用QQ手机版扫码二维码" }
                67 -> { statusCode = 802; status = "请在手机上确认登录" }
                else -> { statusCode = 800; status = "扫码状态异常，请刷新二维码"; return true }
            }
            return false
        } finally {
            checkMutex.unlock()
        }
    }

    LaunchedEffect(generation, selectedLogin) {
        qr = null
        statusCode = 0
        status = "正在获取二维码..."
        (if (selectedLogin == 0) QqMusicApi.createQrCode() else QqMusicApi.createWechatQrCode()).fold(
            onSuccess = { qr = it },
            onFailure = { status = "获取二维码失败: ${it.message}" }
        )
        val current = qr ?: return@LaunchedEffect
        statusCode = 801
        status = if (selectedLogin == 0) "请使用QQ手机版扫码二维码" else "请使用微信扫描二维码"
        while (true) {
            delay(1800)
            if (checkStatus(current)) break
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
        TabRow(tabs = listOf("QQ登录", "微信登录"), selectedTabIndex = selectedLogin,
            onTabSelected = { index ->
                if (selectedLogin != index) {
                    refreshJob?.cancel()
                    qr = null
                    statusCode = 0
                    selectedLogin = index
                }
            }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
        QrLoginLayout(
            isLandscape = configuration.screenHeightDp < configuration.screenWidthDp,
            qrBitmap = bitmap, statusText = status, statusCode = statusCode,
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
    androidx.activity.compose.BackHandler(enabled = isBackHandlerEnabled) { onBack() }
    Scaffold(modifier = modifier, topBar = { BlurredBar(backdrop = blurBackdrop) {
        TopAppBar(title = "《${title}》歌曲评论", largeTitle = "歌曲评论", scrollBehavior = scrollBehavior,
            color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
            navigationIcon = { IconButton(onClick = onBack) {
                Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            } })
    } }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()).pageLayerBackdrop(blurBackdrop)
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = FeedBottomPads.Standard),
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
    androidx.activity.compose.BackHandler { onBack() }
    Scaffold(topBar = { SmallTopAppBar(title = if (albumId != null) "QQ音乐专辑" else "QQ音乐歌手", navigationIcon = {
        Text("返回", modifier = Modifier.clickable { onBack() }.padding(12.dp))
    }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 140.dp)) {
            if (loading) item { Text("正在加载…", modifier = Modifier.padding(16.dp)) }
            error?.let { item { Text(it, modifier = Modifier.padding(16.dp)) } }
            if (!loading && error == null && songs.isEmpty()) item { Text("暂无歌曲", modifier = Modifier.padding(16.dp)) }
            items(songs) { track ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), onClick = { viewModel.playTrackWithMode(track, songs) }) {
                    BasicComponent(title = track.title, summary = "${track.artist} · ${track.album}")
                }
            }
        }
    }
}
