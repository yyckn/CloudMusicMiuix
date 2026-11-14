package com.hypermusic.miuix.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas as ComposeCanvas
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import com.hypermusic.miuix.ui.theme.KeyboardShortcutSettings
import com.hypermusic.miuix.ui.theme.ShortcutAction
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.ClickableText
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.components.TrackMoreActions
import com.hypermusic.miuix.R
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.LyricsView
import com.hypermusic.miuix.ui.theme.ThemeSettings
import com.hypermusic.miuix.ui.theme.rememberPlayerDark
import com.hypermusic.miuix.viewmodel.LyricState
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import com.hypermusic.miuix.viewmodel.PlayerState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SliderDefaults
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.ThemeController
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.NotesFill
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Replace
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment

@Composable
fun PlayerPage(
    viewModel: MusicPlayerViewModel,
    playerState: PlayerState,
    lyricState: LyricState,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    isFMMode: Boolean = false,
    isIntelligenceMode: Boolean = false,
    isVipAccount: Boolean = false, // 是否是VIP账号
    onBackClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDownload: () -> Unit = {},
    onDownloadCover: () -> Unit = {},


    onShare: () -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    onLoadLyrics: () -> Unit = {},
    onNavigateToSoundEffects: () -> Unit = {},
    onOpenSystemUI: () -> Unit = {},
    onLikeTrack: () -> Unit = {},
    onDrawerStateChanged: (Boolean) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToComment: (String, String) -> Unit = { _, _ -> },
    playMode: Int = 0,
    onSwitchPlayMode: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val track = playerState.currentTrack
    // 与「等待歌曲」一致：加载中播放按钮转圈，并禁用进度/上一首/下一首/播放顺序/播放列表
    val controlsLocked = playerState.isLoading
    val trackTitle = track?.title ?: if (controlsLocked) "正在加载歌曲" else "未知歌曲"
    val qualityLabel = getQualityText(playerState.bitrate,
        if (controlsLocked && track == null) "正在加载" else playerState.audioQuality,
        isFMMode, isIntelligenceMode, ThemeSettings.showQuality, viewModel.fmModeLabel)
    val controlIconAlpha = if (controlsLocked) 0.35f else 1f
    val onPreviousSafe: () -> Unit = { if (!controlsLocked) onPrevious() }
    val onNextSafe: () -> Unit = { if (!controlsLocked) onNext() }
    val onSwitchPlayModeSafe: () -> Unit = { if (!controlsLocked) onSwitchPlayMode() }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    val moreListState = rememberLazyListState()
    var scrollToArtists by remember { mutableStateOf(false) }
    LaunchedEffect(showMoreSheet, scrollToArtists) {
        if (showMoreSheet && scrollToArtists) {
            kotlinx.coroutines.delay(100)
            moreListState.scrollToItem(5)
            scrollToArtists = false
        }
    }
    var showLyricUpgradeSheet by remember { mutableStateOf(false) }
    var showAddTrackSheet by remember { mutableStateOf(false) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedInput by remember { mutableStateOf("") }
    val playlistListState = rememberLazyListState()
    val isPureMusic = lyricState.lyrics?.isPureMusic == true
    var waitingForLyrics by remember(lyricState.loadingStartedAt) {
        mutableStateOf(com.hypermusic.miuix.data.lyrics.initialLyricWaitRemaining(
            lyricState.loadingStartedAt, android.os.SystemClock.elapsedRealtime()) > 0)
    }
    LaunchedEffect(lyricState.loadingStartedAt) {
        val remaining = com.hypermusic.miuix.data.lyrics.initialLyricWaitRemaining(
            lyricState.loadingStartedAt, android.os.SystemClock.elapsedRealtime())
        if (remaining > 0) kotlinx.coroutines.delay(remaining)
        waitingForLyrics = false
    }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current

    // 播放页深浅色：0=跟随深浅色设置, 1=浅色流光, 2=深色流光
    val playerDark = rememberPlayerDark()
    val playerText = if (playerDark) Color.White else Color.Black
    val playerDim = if (playerDark) Color(0x40000000) else Color(0x80FFFFFF)

    // Apple Music 歌词引擎自带左右内边距，外层保留 16dp
    val lyricHorizontalPadding = 16.dp

    // 歌词引擎逐帧读取的实时播放位置，避免使用 100ms 刷新的 playerState 而造成卡顿
    val liveLyricsPositionProvider = remember { { viewModel.getPlaybackPositionMs() } }
    val lyricPosition = playerState.currentPosition + playerState.lyricOffsetMs
    val onLyricSeek: (Long) -> Unit = { position ->
        onSeekTo((position - playerState.lyricOffsetMs).coerceIn(0L, playerState.duration.coerceAtLeast(0L)))
    }

    // 封面只联网拉取一次，之后所有位置复用同一张 Bitmap 进行渲染
    var coverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var coverBitmapKey by remember { mutableStateOf<String?>(null) }
    val trackCoverKey = track?.let { "${it.source}:${it.id}:${it.imgUrl}" }
    LaunchedEffect(trackCoverKey) {
        val loadingKey = trackCoverKey
        val url = track?.imgUrl?.let { cover ->
            if (track.source == "qqmusic" && cover.startsWith("https://y.gtimg.cn/music/photo_new/T002R"))
                cover.replace(Regex("T002R(?:300x300|800x800)M000"), "T002R1500x1500M000")
            else cover
        }
        if (url.isNullOrEmpty()) {
            coverBitmap = null
            coverBitmapKey = loadingKey
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(if (track?.source == "qqmusic") 1500 else 1080)
            .memoryCacheKey("player_cover_$url")
            .diskCacheKey(url)
            .build()
        val firstResult = com.hypermusic.miuix.data.cache.ImageCaches.get(context, com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER).execute(request)
        val result = if (firstResult !is SuccessResult && track?.source == "qqmusic" && url.contains("T002R1500x1500M000")) {
            val fallbackUrl = url.replace("T002R1500x1500M000", "T002R800x800M000")
            com.hypermusic.miuix.data.cache.ImageCaches.get(context, com.hypermusic.miuix.data.cache.ImageCaches.Kind.COVER).execute(ImageRequest.Builder(context).data(fallbackUrl).size(800)
                .memoryCacheKey("player_cover_$fallbackUrl").diskCacheKey(fallbackUrl).build())
        } else firstResult
        when (result) {
            is SuccessResult -> {
                val drawable = result.drawable
                val bitmap = when (drawable) {
                    is BitmapDrawable -> drawable.bitmap
                    else -> {
                        val w = drawable.intrinsicWidth.coerceAtLeast(1)
                        val h = drawable.intrinsicHeight.coerceAtLeast(1)
                        Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { bmp ->
                            val canvas = Canvas(bmp)
                            drawable.setBounds(0, 0, canvas.width, canvas.height)
                            drawable.draw(canvas)
                        }
                    }
                }
                coverBitmap = bitmap.asImageBitmap()
            }
            else -> { coverBitmap = null }
        }
        coverBitmapKey = loadingKey
    }
    
    // 音质选项随当前平台变化。
    val qualityOptions = com.hypermusic.miuix.data.model.AudioQuality.options(com.hypermusic.miuix.data.api.PlatformManager.current)

    val onArtistTextClick: (Int) -> Unit = { index ->
        val current = track
        if (current != null) {
            if (isPlayerArtistGroup(current)) {
                scrollToArtists = true
                showMoreSheet = true
            } else {
                val id = current.artistIds.getOrNull(index).orEmpty().ifBlank { current.artistId }
                if (id.isNotBlank()) onNavigateToArtist(id)
            }
        }
    }
    val onAlbumTextClick: () -> Unit = {
        track?.albumId?.removePrefix("nealbum_")?.toLongOrNull()?.let(onNavigateToAlbum)
    }

    // 获取专辑歌手信息
    LaunchedEffect(track?.source, track?.albumId) {
        albumArtist = null
        albumArtistId = null
        if (track?.source == "netease" && track.albumId.isNotEmpty()) {
            val albumIdStr = track.albumId.removePrefix("nealbum_")
            val albumId = albumIdStr.toLongOrNull()
            if (albumId != null) {
                com.hypermusic.miuix.data.api.NeteaseApi.getAlbumDetail(albumId.toString()).onSuccess { (albumInfo, _) ->
                    albumArtist = albumInfo.artist.ifEmpty { null }
                    albumArtistId = albumInfo.artistId.takeIf { it > 0 }
                }
            }
        }
    }

    // 通知外部抽屉状态变化
    LaunchedEffect(showPlaylistSheet, showMoreSheet, showLyricUpgradeSheet, showAddTrackSheet) {
        onDrawerStateChanged(showPlaylistSheet || showMoreSheet || showLyricUpgradeSheet || showAddTrackSheet)
    }

    // 始终有2页（封面+歌词），纯音乐时歌词页显示"纯音乐，请欣赏"
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    val vinylSwipeSongs = ThemeSettings.vinylSwipeAction == 0
    
    // 全屏封面模式下，歌词显示状态
    var showLyricsInFullScreen by remember { mutableStateOf(false) }
    val coverStyleIndex = ThemeSettings.smallScreenCoverStyle // 小屏: 0=卡片, 1=全屏渐变
    val largeScreenCoverStyleSetting = ThemeSettings.largeScreenCoverStyle // 大屏: 0=卡片, 1=全屏渐变, 2=车机
    val showCastButton = ThemeSettings.showCastButton
    
    // 歌词和蒙版动画
    val lyricsAlpha by animateFloatAsState(
        targetValue = if (showLyricsInFullScreen) 1f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "lyrics_alpha"
    )
    val maskAlpha by animateFloatAsState(
        targetValue = if (showLyricsInFullScreen) 0.6f else 0f,
        animationSpec = tween(durationMillis = 500),
        label = "mask_alpha"
    )

    // 封面旋转动画
    val vinylLayout = if (LocalConfiguration.current.screenWidthDp > 600) largeScreenCoverStyleSetting == 3 else coverStyleIndex == 3
    val useRoundCover = ThemeSettings.useRoundCover
    val coverRotationIndex = if (vinylLayout) 1 else ThemeSettings.coverRotationIndex
    val isPlaying = playerState.isPlaying
    
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(useRoundCover, vinylLayout, coverRotationIndex, isPlaying) {
        if ((!useRoundCover && !vinylLayout) || coverRotationIndex == 0) {
            rotation.snapTo(0f)
        } else if (coverRotationIndex == 2 || (coverRotationIndex == 1 && isPlaying)) {
            while (true) {
                val remainingMillis = ((360f - rotation.value) / 360f * 8000f).toInt().coerceAtLeast(1)
                rotation.animateTo(360f, tween(durationMillis = remainingMillis, easing = LinearEasing))
                rotation.snapTo(0f)
            }
        }
    }
    val coverRotation = rotation.value

    // 封面形状
    val coverShape = if (useRoundCover) CircleShape else RoundedCornerShape(16.dp)

    // 打开播放列表时自动定位到当前歌曲
    LaunchedEffect(showPlaylistSheet) {
        if (showPlaylistSheet) {
            val currentIndex = currentPlaylist.indexOfFirst { 
                it.id == playerState.currentTrack?.id 
            }
            if (currentIndex >= 0) {
                playlistListState.scrollToItem(currentIndex)
            }
        }
    }

    // 加载歌词
    LaunchedEffect(playerState.currentTrack?.id) {
        if (playerState.currentTrack != null) {
            onLoadLyrics()
        }
    }

    // 屏幕常亮设置 - 仅在播放页显示时生效
    androidx.compose.runtime.DisposableEffect(ThemeSettings.keepScreenOn) {
        val activity = context as? android.app.Activity
        val window = activity?.window
        if (ThemeSettings.keepScreenOn) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            // 离开播放页时移除屏幕常亮标志
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    BackHandler {
        onBackClick()
    }

    val shortcutFocusRequester = remember { FocusRequester() }
    val keyboardShortcutsEnabled = KeyboardShortcutSettings.enabled
    LaunchedEffect(keyboardShortcutsEnabled) {
        if (keyboardShortcutsEnabled) {
            shortcutFocusRequester.requestFocus()
        }
    }

    // 弹窗/抽屉（如输入播放速度的输入框）会抢走焦点，关闭后把焦点还给播放页，
    // 否则键盘快捷键会失效。
    LaunchedEffect(showSpeedDialog, showUnlikeDialog, showMoreSheet, showPlaylistSheet, showLyricUpgradeSheet, showAddTrackSheet) {
        if (!showSpeedDialog && !showUnlikeDialog && !showMoreSheet && !showPlaylistSheet && !showLyricUpgradeSheet && !showAddTrackSheet) {
            shortcutFocusRequester.requestFocus()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(shortcutFocusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                if (!KeyboardShortcutSettings.enabled) return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                val action = ShortcutAction.entries.firstOrNull {
                    KeyboardShortcutSettings.matches(it, event.nativeKeyEvent.keyCode, event.nativeKeyEvent.metaState)
                } ?: return@onPreviewKeyEvent false
                if (event.nativeKeyEvent.repeatCount > 0 && action !in listOf(ShortcutAction.SeekBackward, ShortcutAction.SeekForward)) return@onPreviewKeyEvent true
                when (action) {
                    ShortcutAction.PlayPause -> {
                        onTogglePlayPause()
                        true
                    }
                    ShortcutAction.Previous -> {
                        onPrevious()
                        true
                    }
                    ShortcutAction.Next -> {
                        onNext()
                        true
                    }
                    ShortcutAction.SeekBackward -> {
                        onSeekTo((playerState.currentPosition - 5_000L).coerceAtLeast(0L))
                        true
                    }
                    ShortcutAction.SeekForward -> {
                        val target = playerState.currentPosition + 5_000L
                        onSeekTo(
                            if (playerState.duration > 0L) target.coerceAtMost(playerState.duration)
                            else target
                        )
                        true
                    }
                    ShortcutAction.Like -> {
                        onLikeTrack()
                        true
                    }
                    ShortcutAction.More -> { showMoreSheet = true; true }
                }
            }
    ) {
        Scaffold(
        topBar = {
            // 空的TopBar，只保留状态栏间距
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
            )
        }
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val screenWidth = configuration.screenWidthDp.dp
        val topPadding = paddingValues.calculateTopPadding()
        // 判断是否是平板（宽度大于600dp）
        val isTablet = screenWidth > 600.dp
        // 大屏“全屏渐变”布局在宽高比小于 4:3（更接近正方形/竖屏）时强制回退到卡片布局
        // Configuration 的 dp 尺寸可能扣除系统栏，使用窗口边界判断实际比例。
        val windowBounds = context.getSystemService(android.view.WindowManager::class.java).currentWindowMetrics.bounds
        val screenAspectRatio = windowBounds.width().toFloat() / windowBounds.height().coerceAtLeast(1)
        val windowHeight = with(LocalDensity.current) { windowBounds.height().toDp() }
        val largeScreenCoverStyle = resolveLargeScreenPlayerStyle(largeScreenCoverStyleSetting, screenAspectRatio)
        // 预留底部控件高度：Slider区域 + 按钮区域 + 底部间距
        val bottomReserved = if (isTablet) 186.dp else 186.dp
        // 标题区域高度（歌曲名称 + 歌手专辑 + 间距）
        val titleAreaHeight = 80.dp
        // 封面可用高度 = 屏幕高度 - 顶部padding - 底部预留 - 标题区域高度 - 额外间距
        val availableHeight = screenHeight - topPadding - bottomReserved - titleAreaHeight - 48.dp
        // 封面大小取高度和宽度的最小值，确保1:1比例不被裁剪
        val imageSize = minOf(availableHeight, screenWidth - 48.dp, screenHeight - topPadding - 240.dp).coerceAtLeast(0.dp)
        // 小屏模式：封面尺寸小于64dp时，隐藏封面，铺满屏幕
        val isSmallScreen = imageSize < 64.dp

        Box(modifier = Modifier.fillMaxSize()) {
            // 背景图片（封面放大铺满，模糊，压暗）
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .align(Alignment.BottomCenter)
            ) {
                // 封面背景图片（使用Crossfade实现切换动画）
                Crossfade(
                    targetState = coverBitmap,
                    animationSpec = tween(durationMillis = 500),
                    label = "bg_cover_crossfade"
                ) { bmp ->
                    CoverImage(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = 1.15f,
                                scaleY = 1.15f
                            )
                            .blur(radius = 100.dp),
                        contentScale = ContentScale.Crop
                    )
                }
                // 压暗层 #000000 25%不透明度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(playerDim)
                )
            }
            
            if (isTablet) {
                // 平板布局
                if (largeScreenCoverStyle == 1) {
                    // 全屏渐变封面样式（平板）
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 封面层（覆盖全屏高度，包括状态栏）
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.55f)
                                .align(Alignment.CenterStart)
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Black,
                                                Color.Black,
                                                Color.Transparent
                                            ),
                                            startX = size.width * 0.6f,
                                            endX = size.width
                                        ),
                                        blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                    )
                                }
                        ) {
                            // 静态封面作为底层
                            CoverImage(
                                bitmap = coverBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }

                        // 右侧内容区域（50%宽度）
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.5f)
                                .align(Alignment.CenterEnd)
                                .padding(top = topPadding)
                        ) {
                            Column(modifier = Modifier.fillMaxSize()) {
                                // 歌曲标题和信息
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp)
                                        .padding(top = 16.dp, bottom = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = trackTitle, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = playerText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(4.dp))
                                        PlayerArtistAlbumText(track, playerText, 14.sp, onArtistTextClick, onAlbumTextClick, modifier = Modifier.fillMaxWidth())
                                    }
                                    if (showCastButton) {
                                        IconButton(onClick = onOpenSystemUI) {
                                            Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "音频输出", tint = playerText)
                                        }
                                    }
                                }
                                
                                // 歌词区域（中间）
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (isPureMusic) {
                                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                                            Text(text = "纯音乐，请欣赏", fontSize = 16.sp, color = playerText.copy(alpha = 0.5f))
                                        }
                                    } else {
                                        LyricsView(lyrics = lyricState.lyrics, waitingForLyrics = waitingForLyrics, currentPosition = lyricPosition, onSeekTo = onLyricSeek, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = lyricHorizontalPadding), source = null, sourceColor = playerText.copy(alpha = 0.5f), sourceStartPadding = 24.dp - lyricHorizontalPadding, showSourceHelp = true, onSourceClick = { showLyricUpgradeSheet = true }, positionProvider = liveLyricsPositionProvider)
                                    }
                                }
                                
                                // 控制按钮（底部）
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                                    var seekPosition by remember { mutableStateOf<Long?>(null) }
                                    val displayPosition = seekPosition ?: playerState.currentPosition
                                    val progress = if (playerState.duration > 0) displayPosition.toFloat() / playerState.duration.toFloat() else 0f
                                    PlayerProgressSlider(value = progress, color = playerText, onValueChange = { newValue -> seekPosition = (newValue * playerState.duration).toLong() }, onValueChangeFinished = { seekPosition?.let { onSeekTo(it) }; seekPosition = null }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(bottom = 2.dp))
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = formatDuration(displayPosition), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                        Text(text = qualityLabel, fontSize = 12.sp, color = playerText)
                                        Text(text = formatDuration(playerState.duration), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                    }
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一首", tint = playerText, modifier = Modifier.size(32.dp)) }
                                            Spacer(modifier = Modifier.width(24.dp))
                                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                                if (playerState.isLoading) InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
                                                else IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) { Icon(imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play, contentDescription = if (playerState.isPlaying) "暂停" else "播放", tint = playerText, modifier = Modifier.size(40.dp)) }
                                            }
                                            Spacer(modifier = Modifier.width(24.dp))
                                            IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一首", tint = playerText, modifier = Modifier.size(32.dp)) }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            if (!isFMMode && !isIntelligenceMode) {
                                                Spacer(modifier = Modifier.width(16.dp))
                                                IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) { Icon(imageVector = when (playMode) { 0 -> MiuixIcons.Sort; 1 -> MiuixIcons.Merge; 2 -> MiuixIcons.Replace; else -> MiuixIcons.Sort }, contentDescription = when (playMode) { 0 -> "顺序播放"; 1 -> "乱序播放"; 2 -> "单曲循环"; else -> "顺序播放" }, tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            if (!isFMMode && !isIntelligenceMode) {
                                                IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.Playlist, contentDescription = "播放列表", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            if (isFMMode || isIntelligenceMode) {
                                                FmLikeButton(
                                                    isLiked = isCurrentTrackLiked,
                                                    onClick = {
                                                        if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                                    }
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                PlayerShareButton(playerText) { onShare() }
                                                Spacer(modifier = Modifier.width(16.dp))
                                                PlayerCommentButton(playerText) {
                                                    playerState.currentTrack?.let { onNavigateToComment(it.id, it.title) }
                                                }
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            if (!isFMMode && !isIntelligenceMode) {
                                                IconButton(onClick = { if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack() }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites, contentDescription = if (isCurrentTrackLiked) "取消收藏" else "收藏", tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.More, contentDescription = "更多", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (largeScreenCoverStyle == 2) {
                    // 车机布局：标题、封面与歌词、播放控制依次排列，内容宽度不受比例限制。
                    Column(
                        modifier = Modifier.fillMaxSize().padding(top = topPadding)
                            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                            .padding(horizontal = 24.dp)
                    ) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(trackTitle, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                                    color = playerText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                PlayerArtistAlbumText(track, playerText, 14.sp, onArtistTextClick, onAlbumTextClick)
                            }
                            if (showCastButton) {
                                IconButton(onClick = onOpenSystemUI) {
                                    Icon(MiuixIcons.ScreenMirroring, contentDescription = "音频输出", tint = playerText)
                                }
                            }
                        }
                        Row(Modifier.fillMaxWidth().weight(1f).padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            BoxWithConstraints(Modifier.weight(1f).fillMaxHeight().padding(8.dp),
                                contentAlignment = Alignment.Center) {
                                val coverSize = minOf(maxWidth, maxHeight).coerceAtLeast(0.dp)
                                Card(modifier = Modifier.size(coverSize), cornerRadius = 24.dp,
                                    colors = CardDefaults.defaultColors(color = Color.Transparent, contentColor = playerText),
                                    pressFeedbackType = PressFeedbackType.Tilt, onClick = {}) {
                                    CoverImage(bitmap = coverBitmap, contentDescription = track?.title,
                                        modifier = Modifier.fillMaxSize().clip(coverShape).graphicsLayer { rotationZ = coverRotation },
                                        contentScale = ContentScale.Crop)
                                }
                            }
                            Box(Modifier.weight(1f).fillMaxHeight().padding(start = 16.dp), contentAlignment = Alignment.Center) {
                                if (isPureMusic) {
                                    Text("纯音乐，请欣赏", fontSize = 16.sp, color = playerText.copy(alpha = 0.5f))
                                } else {
                                    LyricsView(lyrics = lyricState.lyrics, waitingForLyrics = waitingForLyrics, currentPosition = lyricPosition,
                                        onSeekTo = onLyricSeek, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize(),
                                        source = null, sourceColor = playerText.copy(alpha = 0.5f),
                                        showSourceHelp = true, onSourceClick = { showLyricUpgradeSheet = true },
                                        positionProvider = liveLyricsPositionProvider)
                                }
                            }
                        }
                        var seekPosition by remember { mutableStateOf<Long?>(null) }
                        val displayPosition = seekPosition ?: playerState.currentPosition
                        val progress = if (playerState.duration > 0) (displayPosition.toFloat() / playerState.duration).coerceIn(0f, 1f) else 0f
                        PlayerProgressSlider(value = progress, color = playerText, onValueChange = { seekPosition = (it * playerState.duration).toLong() },
                            onValueChangeFinished = { seekPosition?.let(onSeekTo); seekPosition = null },
                            enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth())
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(formatDuration(displayPosition), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                            Text(qualityLabel,
                                fontSize = 12.sp, color = playerText)
                            Text(formatDuration(playerState.duration), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                        }
                        if (windowHeight > 800.dp) {
                            // 控制按钮
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 第一行：上一首、播放/暂停、下一首
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronBackward,
                                            contentDescription = "上一首",
                                            tint = playerText,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    // 播放/暂停按钮
                                    Box(
                                        modifier = Modifier.size(72.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (playerState.isLoading) {
                                            InfiniteProgressIndicator(
                                                modifier = Modifier.size(40.dp)
                                            )
                                        } else {
                                            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                                                Icon(
                                                    imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                                    tint = playerText,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = "下一首",
                                            tint = playerText,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // 第二行：关闭、播放模式、播放列表、更多
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = "关闭",
                                            tint = playerText.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (!isFMMode && !isIntelligenceMode) {
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                            Icon(
                                                imageVector = when (playMode) {
                                                    0 -> MiuixIcons.Sort
                                                    1 -> MiuixIcons.Merge
                                                    2 -> MiuixIcons.Replace
                                                    else -> MiuixIcons.Sort
                                                },
                                                contentDescription = when (playMode) {
                                                    0 -> "顺序播放"
                                                    1 -> "乱序播放"
                                                    2 -> "单曲循环"
                                                    else -> "顺序播放"
                                                },
                                                tint = playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                            Icon(
                                                imageVector = MiuixIcons.Playlist,
                                                contentDescription = "播放列表",
                                                tint = playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    if (isFMMode || isIntelligenceMode) {
                                        FmLikeButton(
                                            isLiked = isCurrentTrackLiked,
                                            onClick = {
                                                if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        PlayerShareButton(playerText) { onShare() }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        PlayerCommentButton(playerText) {
                                            playerState.currentTrack?.let { onNavigateToComment(it.id, it.title) }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    if (!isFMMode && !isIntelligenceMode) {
                                        IconButton(
                                            onClick = {
                                                if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                                contentDescription = if (isCurrentTrackLiked) "取消收藏" else "收藏",
                                                tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.More,
                                            contentDescription = "更多",
                                            tint = playerText.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        } else {
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp).height(72.dp),
                            contentAlignment = Alignment.Center) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                                Icon(MiuixIcons.Close, contentDescription = "关闭", tint = playerText.copy(alpha = 0.6f))
                            }
                            if (!isFMMode && !isIntelligenceMode) {
                                IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                    Icon(when (playMode) { 1 -> MiuixIcons.Merge; 2 -> MiuixIcons.Replace; else -> MiuixIcons.Sort },
                                        contentDescription = when (playMode) { 1 -> "随机播放"; 2 -> "单曲循环"; else -> "顺序播放" },
                                        tint = playerText.copy(alpha = 0.6f))
                                }
                            }
                            }
                            Spacer(Modifier.width(16.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                Icon(MiuixIcons.ChevronBackward, contentDescription = "上一首", tint = playerText, modifier = Modifier.size(32.dp))
                            }
                            Spacer(Modifier.width(24.dp))
                            Box(Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                if (playerState.isLoading) InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
                                else IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                                    Icon(if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                        contentDescription = if (playerState.isPlaying) "暂停" else "播放", tint = playerText,
                                        modifier = Modifier.size(40.dp))
                                }
                            }
                            Spacer(Modifier.width(24.dp))
                            IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                Icon(MiuixIcons.ChevronForward, contentDescription = "下一首", tint = playerText, modifier = Modifier.size(32.dp))
                            }
                            }
                            Spacer(Modifier.width(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                            if (!isFMMode && !isIntelligenceMode) {
                                IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                    Icon(MiuixIcons.Playlist, contentDescription = "播放列表", tint = playerText.copy(alpha = 0.6f))
                                }
                            }
                            IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                Icon(MiuixIcons.More, contentDescription = "更多", tint = playerText.copy(alpha = 0.6f))
                            }
                            }
                            }
                        }
                        }
                    }
                } else {
                // 卡片封面样式（原有平板布局）
                // 计算最大宽度，小屏模式下铺满屏幕，否则保证长宽比不超过5:4
                val maxContentWidth = if (isSmallScreen) {
                    screenWidth
                } else {
                    (screenHeight * 1.25f).coerceAtMost(screenWidth)
                }
                
                Box(
                    modifier = Modifier
                        .padding(top = topPadding)
                        .fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Row(
                        modifier = Modifier
                            .width(maxContentWidth)
                            .fillMaxHeight()
                    ) {
                        // 左侧：歌曲信息 + 封面 + 控制按钮
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        // 歌曲标题和信息
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp, bottom = 24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = trackTitle,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = playerText,
                                    maxLines = if (isTablet) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                PlayerArtistAlbumText(track, playerText, 14.sp, onArtistTextClick, onAlbumTextClick, modifier = Modifier.fillMaxWidth())
                            }
                            
                            // 投屏按钮
                            if (showCastButton) {
                                IconButton(onClick = onOpenSystemUI) {
                                    Icon(
                                        imageVector = MiuixIcons.ScreenMirroring,
                                        contentDescription = "音频输出",
                                        tint = playerText
                                    )
                                }
                            }
                        }

                        // 封面（小屏模式下隐藏）
                        if (!isSmallScreen) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                val constrainedCoverSize = minOf(imageSize, maxWidth, maxHeight).coerceAtLeast(0.dp)
                                if (largeScreenCoverStyle == 3) {
                                    VinylRecordCover(coverBitmap.takeIf { coverBitmapKey == trackCoverKey }, track?.title, constrainedCoverSize, coverRotation, isPlaying,
                                        trackCoverKey, coverBitmapKey == trackCoverKey, true, onPreviousSafe, onNextSafe,
                                        onShowLyrics = null)
                                } else Card(
                                    modifier = Modifier
                                        .size(constrainedCoverSize)
                                        .aspectRatio(1f),
                                    cornerRadius = 24.dp,
                                    colors = CardDefaults.defaultColors(
                                        color = Color.Transparent,
                                        contentColor = playerText
                                    ),
                                    pressFeedbackType = PressFeedbackType.Tilt,
                                    onClick = {}
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        // 静态封面
                                        CoverImage(
                                            bitmap = coverBitmap,
                                            contentDescription = track?.title,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(coverShape)
                                                .graphicsLayer {
                                                    rotationZ = coverRotation
                                                },
                                            contentScale = ContentScale.Crop
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                            // 播放进度条
                            var seekPosition by remember { mutableStateOf<Long?>(null) }
                            val displayPosition = seekPosition ?: playerState.currentPosition
                            val progress = if (playerState.duration > 0) {
                                displayPosition.toFloat() / playerState.duration.toFloat()
                            } else {
                                0f
                            }

                            PlayerProgressSlider(
                                value = progress, color = playerText,
                                onValueChange = { newValue ->
                                    seekPosition = (newValue * playerState.duration).toLong()
                                },
                                onValueChangeFinished = {
                                    seekPosition?.let { onSeekTo(it) }
                                    seekPosition = null
                                },
                                enabled = !playerState.isLoading,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 2.dp)
                            )

                            // 时间显示和音质
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(displayPosition),
                                    fontSize = 12.sp,
                                    color = playerText.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = qualityLabel,
                                    fontSize = 12.sp,
                                    color = playerText
                                )
                                Text(
                                    text = formatDuration(playerState.duration),
                                    fontSize = 12.sp,
                                    color = playerText.copy(alpha = 0.6f)
                                )
                            }

                            // 控制按钮
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // 第一行：上一首、播放/暂停、下一首
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronBackward,
                                            contentDescription = "上一首",
                                            tint = playerText,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    // 播放/暂停按钮
                                    Box(
                                        modifier = Modifier.size(72.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (playerState.isLoading) {
                                            InfiniteProgressIndicator(
                                                modifier = Modifier.size(40.dp)
                                            )
                                        } else {
                                            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                                                Icon(
                                                    imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                                    tint = playerText,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = "下一首",
                                            tint = playerText,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }

                                // 第二行：关闭、播放模式、播放列表、更多
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.Close,
                                            contentDescription = "关闭",
                                            tint = playerText.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (!isFMMode && !isIntelligenceMode) {
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                            Icon(
                                                imageVector = when (playMode) {
                                                    0 -> MiuixIcons.Sort
                                                    1 -> MiuixIcons.Merge
                                                    2 -> MiuixIcons.Replace
                                                    else -> MiuixIcons.Sort
                                                },
                                                contentDescription = when (playMode) {
                                                    0 -> "顺序播放"
                                                    1 -> "乱序播放"
                                                    2 -> "单曲循环"
                                                    else -> "顺序播放"
                                                },
                                                tint = playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                            Icon(
                                                imageVector = MiuixIcons.Playlist,
                                                contentDescription = "播放列表",
                                                tint = playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    if (isFMMode || isIntelligenceMode) {
                                        FmLikeButton(
                                            isLiked = isCurrentTrackLiked,
                                            onClick = {
                                                if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        PlayerShareButton(playerText) { onShare() }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        PlayerCommentButton(playerText) {
                                            playerState.currentTrack?.let { onNavigateToComment(it.id, it.title) }
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    if (!isFMMode && !isIntelligenceMode) {
                                        IconButton(
                                            onClick = {
                                                if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                            },
                                            modifier = Modifier.size(48.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                                contentDescription = if (isCurrentTrackLiked) "取消收藏" else "收藏",
                                                tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else playerText.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                    }
                                    IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.More,
                                            contentDescription = "更多",
                                            tint = playerText.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // 右侧：歌词
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(start = 16.dp, end = 24.dp)
                                .windowInsetsPadding(
                                    WindowInsets.navigationBars.only(WindowInsetsSides.Bottom)
                                )
                        ) {
                            if (isPureMusic) {
                                // 纯音乐显示提示
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "纯音乐，请欣赏",
                                        fontSize = 16.sp,
                                        color = playerText.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                // 显示歌词
                                LyricsView(
                                    lyrics = lyricState.lyrics, waitingForLyrics = waitingForLyrics,
                                    currentPosition = lyricPosition,
                                    onSeekTo = onLyricSeek,
                                    isPlaying = playerState.isPlaying,
                                    modifier = Modifier.fillMaxSize(),
                                    source = null,
                                    sourceColor = playerText.copy(alpha = 0.5f),
                                    showSourceHelp = true,
                                    onSourceClick = { showLyricUpgradeSheet = true },
                                    positionProvider = liveLyricsPositionProvider
                                )
                            }
                        }
                    }
                }
                } // 结束平板卡片封面样式
            } else {
                // 手机布局
                if (coverStyleIndex == 1) {
                    // 全屏渐变封面样式（手机）
                    // 用于记录标题栏和控件区域的实际位置
                    var titleBottomY by remember { mutableFloatStateOf(0f) }
                    var controlsTopY by remember { mutableFloatStateOf(Float.MAX_VALUE) }
                    
                    val density = LocalDensity.current
                    val containerHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
                    val topPaddingPx = with(density) { topPadding.toPx() }
                    // 封面是1:1比例，宽度等于屏幕宽度
                    val coverHeightPx = screenWidthPx
                    // 封面居中在标题栏下方到控件上方的区域
                    val coverCenterY = (titleBottomY + controlsTopY) / 2
                    val coverTopPx = coverCenterY - coverHeightPx / 2
                    val coverBottomPx = coverCenterY + coverHeightPx / 2
                    
                    Box(modifier = Modifier.fillMaxSize()) {
                        
                        // 模糊背景（全屏，覆盖状态栏区域）
                        Crossfade(
                            targetState = coverBitmap,
                            animationSpec = tween(durationMillis = 500),
                            label = "fullscreen_bg_crossfade"
                        ) { bmp ->
                            CoverImage(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = 1.15f, scaleY = 1.15f)
                                    .blur(radius = 100.dp),
                                contentScale = ContentScale.Crop
                            )
                        }
                        // 深色蒙版
                        Box(modifier = Modifier.fillMaxSize().background(playerDim))
                        
                            // 静态封面
                            val coverCenterY = (titleBottomY + controlsTopY) / 2
                            val coverTopOffset = coverCenterY - coverHeightPx / 2
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(1f)
                                    .offset { IntOffset(0, coverTopOffset.toInt()) }
                            ) {
                                CoverImage(
                                    bitmap = coverBitmap,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // 歌词显示时的静态封面模糊层
                            if (lyricsAlpha > 0f) {
                                CoverImage(
                                    bitmap = coverBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .offset { IntOffset(0, coverTopOffset.toInt()) }
                                        .graphicsLayer { alpha = lyricsAlpha }
                                        .blur(radius = 15.dp),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            
                            // 蒙版层
                            // 如果封面上边界比标题栏低，就用封面的上边界；否则用标题栏
                            // 如果封面下边界比播放栏高，就用封面的下边界；否则用播放栏
                            val maskTop = if (coverTopPx > titleBottomY) coverTopPx else titleBottomY
                            val maskBottom = if (coverBottomPx < controlsTopY) coverBottomPx else controlsTopY
                            val maskHeight = (maskBottom - maskTop).coerceAtLeast(1f)
                            val mask15Percent = maskTop + maskHeight * 0.15f
                            val mask85Percent = maskTop + maskHeight * 0.85f
                            
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        compositingStrategy = CompositingStrategy.Offscreen
                                    }
                                    .drawWithContent {
                                        drawContent()
                                        drawRect(
                                            brush = Brush.verticalGradient(
                                                0f to Color.Black,
                                                (maskTop / containerHeightPx).coerceIn(0f, 1f) to Color.Black,
                                                (mask15Percent / containerHeightPx).coerceIn(0f, 1f) to Color.Transparent,
                                                (mask85Percent / containerHeightPx).coerceIn(0f, 1f) to Color.Transparent,
                                                (maskBottom / containerHeightPx).coerceIn(0f, 1f) to Color.Black,
                                                1f to Color.Black
                                            ),
                                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                        )
                                    }
                            ) {
                                CoverImage(
                                    bitmap = coverBitmap,
                                    contentDescription = null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer(scaleX = 1.15f, scaleY = 1.15f)
                                        .blur(radius = 100.dp),
                                    contentScale = ContentScale.Crop
                                )
                                // 压暗层（跟随蒙版区域，随 DstIn 渐变生效）
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(playerDim)
                                )
                            }
                        
                        // 内容层（在封面和蒙版上方）
                        Column(
                            modifier = Modifier.padding(top = topPadding).fillMaxSize()
                        ) {
                            // 歌曲标题和信息
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 16.dp, bottom = 8.dp)
                                    .onGloballyPositioned { coordinates ->
                                        // 使用positionInParent获取不受graphicsLayer影响的位置
                                        // 加上topPadding得到实际窗口位置
                                        titleBottomY = topPaddingPx + coordinates.positionInParent().y + coordinates.size.height
                                    },
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = trackTitle, fontSize = 24.sp, fontWeight = FontWeight.Medium, color = playerText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    PlayerArtistAlbumText(track, playerText, 14.sp, onArtistTextClick, onAlbumTextClick, modifier = Modifier.fillMaxWidth())
                                }
                                if (showCastButton) {
                                    IconButton(onClick = onOpenSystemUI) {
                                        Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "音频输出", tint = playerText)
                                    }
                                }
                            }

                            // 中间区域：歌词（封面已在背景层）
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) { showLyricsInFullScreen = !showLyricsInFullScreen },
                                contentAlignment = Alignment.Center
                            ) {
                                // 歌词显示
                                if (lyricsAlpha > 0f) {
                                    // 歌词蒙版背景（压暗+模糊+渐变）
                                    // 使用封面的上下位置，渐变位置为封面的15%和85%
                                    val lyricsMaskTop = if (coverTopPx > titleBottomY) coverTopPx else titleBottomY
                                    val lyricsMaskBottom = if (coverBottomPx < controlsTopY) coverBottomPx else controlsTopY
                                    val lyricsMaskHeight = (lyricsMaskBottom - lyricsMaskTop).coerceAtLeast(1f)
                                    val lyricsGradient15 = (lyricsMaskTop + lyricsMaskHeight * 0.15f) / containerHeightPx
                                    val lyricsGradient85 = (lyricsMaskTop + lyricsMaskHeight * 0.85f) / containerHeightPx
                                    
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = lyricsAlpha }
                                            .background(
                                                Brush.verticalGradient(
                                                    0f to Color.Transparent,
                                                    lyricsGradient15.coerceIn(0f, 1f) to playerDim.copy(alpha = 0.3f),
                                                    lyricsGradient85.coerceIn(0f, 1f) to playerDim.copy(alpha = 0.3f),
                                                    1f to Color.Transparent
                                                )
                                            )
                                    )
                                    // 歌词内容
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { alpha = lyricsAlpha }
                                    ) {
                                        if (isPureMusic) {
                                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp), contentAlignment = Alignment.Center) {
                                                Text(text = "纯音乐，请欣赏", fontSize = 16.sp, color = playerText.copy(alpha = 0.5f))
                                            }
                                        } else {
                                            LyricsView(lyrics = lyricState.lyrics, waitingForLyrics = waitingForLyrics, currentPosition = lyricPosition, onSeekTo = onLyricSeek, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = lyricHorizontalPadding), source = null, sourceColor = playerText.copy(alpha = 0.5f), sourceStartPadding = 24.dp - lyricHorizontalPadding, showSourceHelp = true, onSourceClick = { showLyricUpgradeSheet = true }, positionProvider = liveLyricsPositionProvider)
                                        }
                                    }
                                }
                            }
                            
                            // 底部控制区域
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = if (screenWidth < 400.dp) 8.dp else 24.dp)
                                    .onGloballyPositioned { coordinates ->
                                        controlsTopY = topPaddingPx + coordinates.positionInParent().y
                                    }
                            ) {
                                var seekPosition by remember { mutableStateOf<Long?>(null) }
                                val displayPosition = seekPosition ?: playerState.currentPosition
                                val progress = if (playerState.duration > 0) displayPosition.toFloat() / playerState.duration.toFloat() else 0f
                                PlayerProgressSlider(value = progress, color = playerText, onValueChange = { newValue -> seekPosition = (newValue * playerState.duration).toLong() }, onValueChangeFinished = { seekPosition?.let { onSeekTo(it) }; seekPosition = null }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(horizontal = if (screenWidth < 400.dp) 16.dp else 0.dp).padding(bottom = 2.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = if (screenWidth < 400.dp) 16.dp else 0.dp).padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = formatDuration(displayPosition), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                    Text(text = qualityLabel, fontSize = 12.sp, color = playerText)
                                    Text(text = formatDuration(playerState.duration), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                }
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一首", tint = playerText, modifier = Modifier.size(32.dp)) }
                                        Spacer(modifier = Modifier.width(24.dp))
                                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                            if (playerState.isLoading) InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
                                            else IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) { Icon(imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play, contentDescription = if (playerState.isPlaying) "暂停" else "播放", tint = playerText, modifier = Modifier.size(40.dp)) }
                                        }
                                        Spacer(modifier = Modifier.width(24.dp))
                                        IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一首", tint = playerText, modifier = Modifier.size(32.dp)) }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = if ((isFMMode || isIntelligenceMode) && screenWidth < 400.dp) Arrangement.SpaceEvenly else Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        if (isFMMode || isIntelligenceMode) {
                                            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = { showLyricsInFullScreen = !showLyricsInFullScreen }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (showLyricsInFullScreen) MiuixIcons.NotesFill else MiuixIcons.Notes, contentDescription = "歌词", tint = if (showLyricsInFullScreen) playerText else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                            FmLikeButton(
                                                isLiked = isCurrentTrackLiked,
                                                onClick = {
                                                    if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                                }
                                            )
                                            if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                            PlayerShareButton(playerText) { onShare() }
                                            if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                        } else {
                                            IconButton(onClick = { showLyricsInFullScreen = !showLyricsInFullScreen }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (showLyricsInFullScreen) MiuixIcons.NotesFill else MiuixIcons.Notes, contentDescription = "歌词", tint = if (showLyricsInFullScreen) playerText else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) { Icon(imageVector = when (playMode) { 0 -> MiuixIcons.Sort; 1 -> MiuixIcons.Merge; 2 -> MiuixIcons.Replace; else -> MiuixIcons.Sort }, contentDescription = when (playMode) { 0 -> "顺序播放"; 1 -> "乱序播放"; 2 -> "单曲循环"; else -> "顺序播放" }, tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.Playlist, contentDescription = "播放列表", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = { if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack() }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites, contentDescription = if (isCurrentTrackLiked) "取消收藏" else "收藏", tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            Spacer(modifier = Modifier.width(16.dp))
                                        }
                                        IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.More, contentDescription = "更多", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                    }
                                }
                            }
                        }
                    }
                } else {
                // 卡片封面样式（原有手机布局）
                Column(
                    modifier = Modifier
                        .padding(top = topPadding)
                        .fillMaxSize()
                ) {
                    // 歌曲标题和信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 16.dp, bottom = 24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                text = trackTitle,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = playerText,
                                maxLines = if (isTablet) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            PlayerArtistAlbumText(track, playerText, 14.sp, onArtistTextClick, onAlbumTextClick, modifier = Modifier.fillMaxWidth())
                        }
                        
                        // 投屏按钮
                        if (showCastButton) {
                            IconButton(onClick = onOpenSystemUI) {
                                Icon(
                                    imageVector = MiuixIcons.ScreenMirroring,
                                    contentDescription = "音频输出",
                                    tint = playerText
                                )
                            }
                        }
                    }

                    // 水平翻页器（封面/歌词）
                    HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = coverStyleIndex != 3 || !vinylSwipeSongs,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) { page ->
                        when (page) {
                            0 -> {
                                // 封面页面（小屏模式下隐藏封面）
                                if (isSmallScreen) {
                                    // 小屏模式：显示歌曲信息代替封面
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally
                                        ) {
                                            Text(
                                                text = trackTitle,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = playerText,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            PlayerArtistAlbumText(track, playerText, 16.sp, onArtistTextClick, onAlbumTextClick,
                                                modifier = Modifier.fillMaxWidth(), centered = true, maxLines = Int.MAX_VALUE)
                                        }
                                    }
                                } else {
                                    // 正常模式：显示封面
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        val constrainedCoverSize = minOf(imageSize, maxWidth, maxHeight).coerceAtLeast(0.dp)
                                        if (coverStyleIndex == 3) {
                                            VinylRecordCover(coverBitmap.takeIf { coverBitmapKey == trackCoverKey }, track?.title, constrainedCoverSize, coverRotation, isPlaying,
                                                trackCoverKey, coverBitmapKey == trackCoverKey, vinylSwipeSongs, onPreviousSafe, onNextSafe,
                                                onShowLyrics = { coroutineScope.launch { pagerState.animateScrollToPage(1) } })
                                        } else Card(
                                            modifier = Modifier
                                                .size(constrainedCoverSize)
                                                .aspectRatio(1f),
                                            cornerRadius = 24.dp,
                                            colors = CardDefaults.defaultColors(
                                                color = Color.Transparent,
                                                contentColor = playerText
                                            ),
                                            pressFeedbackType = PressFeedbackType.Tilt,
                                            onClick = {}
                                        ) {
                                            Box(modifier = Modifier.fillMaxSize()) {
                                                // 静态封面
                                                CoverImage(
                                                    bitmap = coverBitmap,
                                                    contentDescription = track?.title,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .clip(coverShape)
                                                        .graphicsLayer {
                                                            rotationZ = coverRotation
                                                        },
                                                    contentScale = ContentScale.Crop
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            1 -> {
                                Box(Modifier.fillMaxSize().then(if (coverStyleIndex == 3 && vinylSwipeSongs) Modifier.clickable {
                                    coroutineScope.launch { pagerState.animateScrollToPage(0) }
                                } else Modifier)) {
                                // 歌词页面
                                if (isPureMusic) {
                                    // 纯音乐显示提示
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 24.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "纯音乐，请欣赏",
                                            fontSize = 16.sp,
                                            color = playerText.copy(alpha = 0.5f)
                                        )
                                    }
                                } else {
                                    LyricsView(
                                        lyrics = lyricState.lyrics, waitingForLyrics = waitingForLyrics,
                                        currentPosition = lyricPosition,
                                        onSeekTo = onLyricSeek,
                                        isPlaying = playerState.isPlaying,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = lyricHorizontalPadding),
                                        source = null,
                                        sourceColor = playerText.copy(alpha = 0.5f),
                                        sourceStartPadding = 24.dp - lyricHorizontalPadding,
                                        showSourceHelp = true,
                                        onSourceClick = { showLyricUpgradeSheet = true },
                                        positionProvider = liveLyricsPositionProvider
                                    )
                                }
                                }
                            }
                        }
                    }

                    // 底部控制区域
                    Column(modifier = Modifier.padding(horizontal = if (screenWidth < 400.dp) 8.dp else 24.dp)) {
                        // 播放进度条
                        var seekPosition by remember { mutableStateOf<Long?>(null) }
                        val displayPosition = seekPosition ?: playerState.currentPosition
                        val progress = if (playerState.duration > 0) {
                            displayPosition.toFloat() / playerState.duration.toFloat()
                        } else {
                            0f
                        }

                        PlayerProgressSlider(
                            value = progress, color = playerText,
                            onValueChange = { newValue ->
                                seekPosition = (newValue * playerState.duration).toLong()
                            },
                            onValueChangeFinished = {
                                seekPosition?.let { onSeekTo(it) }
                                seekPosition = null
                            },
                            enabled = !playerState.isLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (screenWidth < 400.dp) 16.dp else 0.dp)
                                .padding(bottom = 2.dp)
                        )

                        // 时间显示和音质
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = if (screenWidth < 400.dp) 16.dp else 0.dp)
                                .padding(bottom = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                        Text(
                            text = formatDuration(displayPosition),
                            fontSize = 12.sp,
                            color = playerText.copy(alpha = 0.6f)
                        )
                        Text(
                            text = qualityLabel,
                            fontSize = 12.sp,
                            color = playerText
                        )
                        Text(
                            text = formatDuration(playerState.duration),
                            fontSize = 12.sp,
                            color = playerText.copy(alpha = 0.6f)
                        )
                    }

                    // 控制按钮
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 第一行：上一首、播放/暂停、下一首
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = onPreviousSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronBackward,
                                    contentDescription = "上一首",
                                    tint = playerText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            // 播放/暂停按钮
                            Box(
                                modifier = Modifier.size(72.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                if (playerState.isLoading) {
                                    InfiniteProgressIndicator(
                                        modifier = Modifier.size(40.dp)
                                    )
                                } else {
                                    IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(56.dp)) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                            contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                            tint = playerText,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            IconButton(onClick = onNextSafe, modifier = Modifier.size(56.dp).alpha(controlIconAlpha)) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = "下一首",
                                    tint = playerText,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                        }

                        // 第二行：关闭/播放模式/歌词/播放列表
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalArrangement = if ((isFMMode || isIntelligenceMode) && screenWidth < 400.dp) Arrangement.SpaceEvenly else Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (isFMMode || isIntelligenceMode) {
                                IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) {
                                    Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭",
                                        tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                                }
                                if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = {
                                    coroutineScope.launch { pagerState.animateScrollToPage(if (pagerState.currentPage == 0) 1 else 0) }
                                }, modifier = Modifier.size(48.dp)) {
                                    Icon(imageVector = if (pagerState.currentPage == 1) MiuixIcons.NotesFill else MiuixIcons.Notes,
                                        contentDescription = "歌词", tint = if (pagerState.currentPage == 1) playerText else playerText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp))
                                }
                                if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                FmLikeButton(
                                    isLiked = isCurrentTrackLiked,
                                    onClick = {
                                        if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                    }
                                )
                                if (screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                                PlayerShareButton(playerText) { onShare() }
                            } else {
                                // 歌词按钮
                                IconButton(
                                    onClick = {
                                        coroutineScope.launch {
                                            if (pagerState.currentPage == 0) {
                                                pagerState.animateScrollToPage(1)
                                            } else {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = if (pagerState.currentPage == 1) MiuixIcons.NotesFill else MiuixIcons.Notes,
                                        contentDescription = "歌词",
                                        tint = if (pagerState.currentPage == 1) {
                                            playerText
                                        } else {
                                            playerText.copy(alpha = 0.6f)
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = onSwitchPlayModeSafe, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                    Icon(
                                        imageVector = when (playMode) {
                                            0 -> MiuixIcons.Sort
                                            1 -> MiuixIcons.Merge
                                            2 -> MiuixIcons.Replace
                                            else -> MiuixIcons.Sort
                                        },
                                        contentDescription = when (playMode) {
                                            0 -> "顺序播放"
                                            1 -> "乱序播放"
                                            2 -> "单曲循环"
                                            else -> "顺序播放"
                                        },
                                        tint = playerText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) {
                                    Icon(
                                        imageVector = MiuixIcons.Playlist,
                                        contentDescription = "播放列表",
                                        tint = playerText.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = { if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack() }, modifier = Modifier.size(48.dp)) {
                                    Icon(imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                        contentDescription = if (isCurrentTrackLiked) "取消收藏" else "收藏",
                                        tint = if (isCurrentTrackLiked) MiuixTheme.colorScheme.primary else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp))
                                }
                            }
                            if ((!isFMMode && !isIntelligenceMode) || screenWidth >= 400.dp) Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                    tint = playerText.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                    } // 结束底部控制区域
                }
            }
            } // 结束手机卡片封面样式
            
            // Playback sheet handle stays below the status bar in every layout.
            Box(
                modifier = Modifier.align(Alignment.TopCenter)
                    .padding(top = topPadding + 2.dp)
                    .width(48.dp).height(4.dp)
                    .background(playerText.copy(alpha = 0.45f), RoundedCornerShape(50))
            )

            // 播放列表抽屉（作为覆盖层）
            var shouldScrollToCurrent by remember { mutableStateOf(false) }
            
            LaunchedEffect(shouldScrollToCurrent) {
                if (shouldScrollToCurrent) {
                    val currentIndex = currentPlaylist.indexOfFirst { 
                        it.id == playerState.currentTrack?.id 
                    }
                    if (currentIndex >= 0) {
                        playlistListState.animateScrollToItem(currentIndex)
                    }
                    shouldScrollToCurrent = false
                }
            }
            
            OverlayBottomSheet(
                show = showPlaylistSheet,
                title = "播放列表",
                insideMargin = DpSize(12.dp, 0.dp),
                startAction = null,
                endAction = {
                    IconButton(
                        onClick = { showPlaylistSheet = false }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Close,
                            contentDescription = "关闭",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
                onDismissRequest = { showPlaylistSheet = false }
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (currentPlaylist.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无歌曲",
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = playlistListState,
                            modifier = Modifier.fillMaxWidth(),
                            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                        ) {
                            items(currentPlaylist) { track ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    colors = CardDefaults.defaultColors(
                                        color = MiuixTheme.colorScheme.secondaryContainer
                                    ),
                                    pressFeedbackType = PressFeedbackType.Tilt,
                                    showIndication = true,
                                    onClick = {
                                        onPlayTrack(track)
                                        showPlaylistSheet = false
                                    }
                                ) {
                                    TrackItem(
                                        track = track,
                                        isPlaying = track.id == playerState.currentTrack?.id,
                                        onClick = {
                                            onPlayTrack(track)
                                            showPlaylistSheet = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // 定位到当前歌曲
                    FloatingActionButton(
                        onClick = { shouldScrollToCurrent = true },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 24.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Location,
                            contentDescription = "定位到当前歌曲",
                            tint = MiuixTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            
            // 更多抽屉（作为覆盖层）
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
                    state = moreListState,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
                ) {
                    item {
                        TrackMoreActions(liked = isCurrentTrackLiked,
                            onShare = { showMoreSheet = false; onShare() },
                            onLike = {
                                showMoreSheet = false
                                if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                            },
                            onComment = { showMoreSheet = false; track?.let { onNavigateToComment(it.id, it.title) } })
                    }
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
                            WindowDropdownPreference(
                                title = "音质",
                                summary = com.hypermusic.miuix.data.model.AudioQuality.summary,
                                items = qualityOptions,
                                selectedIndex = viewModel.audioQualityIndex,
                                startAction = { Icon(imageVector = MiuixIcons.Music, contentDescription = null, modifier = Modifier.size(24.dp)) },
                                onSelectedIndexChange = { viewModel.updateAudioQualityIndex(it) }
                            )
                            com.hypermusic.miuix.ui.components.LoudnessPreference()
                            ArrowPreference(title = "音效", startAction = { SettingsItemIcon(R.drawable.ic_md3_equalizer) }, onClick = {
                                showMoreSheet = false
                                onNavigateToSoundEffects()
                            })
                            ArrowPreference(
                                title = "播放速度",
                                startAction = { SettingsItemIcon(R.drawable.ic_md3_speed) },
                                endActions = {
                                    Text(
                                        text = "${formatSpeedValue(viewModel.playbackSpeed)}x",
                                        style = MiuixTheme.textStyles.body2,
                                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                    )
                                },
                                bottomAction = {
                                    Slider(
                                        value = viewModel.playbackSpeed,
                                        onValueChange = { newValue ->
                                            viewModel.updatePlaybackSpeed(newValue)
                                        },
                                        valueRange = 0.1f..5f,
                                        steps = 48,
                                        showKeyPoints = true,
                                        keyPoints = listOf(1f),
                                        magnetThreshold = 0.005f,
                                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                onClick = {
                                    speedInput = formatSpeedValue(viewModel.playbackSpeed)
                                    showSpeedDialog = true
                                }
                            )
                        }
                    }

                    if (playerState.currentTrack?.source in setOf("bodian", "kugou", "qishui")) {
                        item {
                            Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                                ArrowPreference(title = "添加到个人歌单", onClick = { showAddTrackSheet = true })
                            }
                        }
                    }
                    item { SmallTitle("歌词") }
                    item {
                        Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
                            ArrowPreference(title = "匹配歌词", onClick = { showLyricUpgradeSheet = true })
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
                            // 显示所有歌手
                            val artistNames = track?.artistNames ?: emptyList()
                            val artistIds = track?.artistIds ?: emptyList()
                            android.util.Log.d("PlayerPage", "artistNames: $artistNames, artistIds: $artistIds")
                            if (artistNames.isNotEmpty()) {
                                artistNames.forEachIndexed { index, artistName ->
                                    val artistId = if (index < artistIds.size) artistIds[index] else ""
                                    ArrowPreference(
                                        title = "歌手：$artistName",
                                        onClick = {
                                            android.util.Log.d("PlayerPage", "Clicked artist: $artistName, id: $artistId")
                                            showMoreSheet = false
                                            if (artistId.isNotEmpty()) {
                                                onNavigateToArtist(artistId)
                                            }
                                        }
                                    )
                                }
                            } else {
                                ArrowPreference(
                                    title = "歌手：${track?.artist ?: "未知"}",
                                    onClick = {
                                        showMoreSheet = false
                                        if (track?.artistId?.isNotEmpty() == true) {
                                            onNavigateToArtist(track.artistId)
                                        }
                                    }
                                )
                            }
                            // 显示专辑
                            ArrowPreference(
                                title = "专辑：${track?.album ?: "未知"}",
                                onClick = {
                                    showMoreSheet = false
                                    if (track?.albumId?.isNotEmpty() == true) {
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
                        }
                    }
                }
            }
        }

        AddTrackToPlaylistSheet(show = showAddTrackSheet, track = playerState.currentTrack, onDismiss = { showAddTrackSheet = false })
        LyricMatchSheet(show = showLyricUpgradeSheet, original = playerState.currentTrack,
            currentLyrics = lyricState.lyrics,
            currentLyricSource = lyricState.source.takeIf { lyricState.lyrics?.lines?.isNotEmpty() == true && lyricState.error == null },
            lyricsLoading = lyricState.isLoading,
            onDismiss = { showLyricUpgradeSheet = false }, onChanged = { viewModel.loadLyrics() })

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

        // 调节播放速度对话框
        OverlayDialog(
            show = showSpeedDialog,
            title = "调节播放速度",
            summary = "输入0.1-5之间的数字",
            onDismissRequest = { showSpeedDialog = false },
            onDismissFinished = { shortcutFocusRequester.requestFocus() },
            content = {
                Column {
                    TextField(
                        value = speedInput,
                        onValueChange = { newValue ->
                            if (newValue.isEmpty() || newValue.matches(Regex("^\\d*\\.?\\d*$"))) {
                                speedInput = newValue
                            }
                        },
                        label = "0.1 - 5",
                        useLabelAsPlaceholder = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        TextButton(
                            text = "取消",
                            onClick = { showSpeedDialog = false },
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "确定",
                            onClick = {
                                speedInput.toFloatOrNull()?.let { viewModel.updatePlaybackSpeed(it) }
                                showSpeedDialog = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary(),
                        )
                    }
                }
            },
        )
    }
    }
}

private fun formatSpeedValue(speed: Float): String {
    val hundredths = (speed * 100f).roundToInt()
    return if (hundredths % 10 == 0) {
        String.format(java.util.Locale.US, "%.1f", speed)
    } else {
        String.format(java.util.Locale.US, "%.2f", speed)
    }
}

@Composable
private fun PlayerArtistAlbumText(track: Track?, color: Color, fontSize: TextUnit,
    onArtistClick: (Int) -> Unit, onAlbumClick: () -> Unit, modifier: Modifier = Modifier, centered: Boolean = false,
    maxLines: Int = 1) {
    val text = buildPlayerArtistAlbumText(track)
    ClickableText(text = text, modifier = modifier, maxLines = maxLines, overflow = TextOverflow.Ellipsis,
        style = TextStyle(fontSize = fontSize, color = color.copy(alpha = 0.7f),
            textAlign = if (centered) TextAlign.Center else TextAlign.Start),
        onClick = { offset ->
            when {
                text.getStringAnnotations("artist", offset, offset).isNotEmpty() ->
                    onArtistClick(text.getStringAnnotations("artist", offset, offset).first().item.toIntOrNull() ?: 0)
                text.getStringAnnotations("album", offset, offset).isNotEmpty() -> onAlbumClick()
            }
        })
}

internal fun isPlayerArtistGroup(track: Track): Boolean =
    track.artistNames.size >= 4 || track.artist.trim() == "群星"

internal fun buildPlayerArtistAlbumText(track: Track?): androidx.compose.ui.text.AnnotatedString {
    val artist = track?.artist ?: "未知歌手"
    val album = track?.album ?: "未知专辑"
    return buildAnnotatedString {
        var cursor = 0
        val names = track?.artistNames.orEmpty()
        val isGroup = track?.let(::isPlayerArtistGroup) ?: false
        if (!isGroup && names.isNotEmpty()) {
            names.forEachIndexed { index, name ->
                if (name.isNotBlank()) {
                    val start = artist.indexOf(name, cursor)
                    if (start >= 0) {
                        append(artist.substring(cursor, start))
                        pushStringAnnotation("artist", index.toString()); append(name); pop()
                        cursor = start + name.length
                    }
                }
            }
        }
        if (cursor < artist.length) {
            pushStringAnnotation("artist", "0"); append(artist.substring(cursor)); pop()
        }
        append(" - ")
        pushStringAnnotation("album", "album"); append(album); pop()
    }
}

@Composable
private fun PlayerProgressSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    enabled: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val progress = value.coerceIn(0f, 1f)
    var isDragging by remember { mutableStateOf(false) }
    val lineWidth by animateDpAsState(if (isDragging) 10.dp else 6.dp, label = "playerProgressWidth")
    BoxWithConstraints(modifier = modifier.height(24.dp).pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            try {
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (kotlin.math.abs(change.position.x - down.position.x) > viewConfiguration.touchSlop) {
                        isDragging = true
                    }
                } while (change.pressed)
            } finally {
                isDragging = false
            }
        }
    }, contentAlignment = Alignment.CenterStart) {
        // 保留 Miuix 滑块的拖动、点击、触觉反馈和无障碍语义，视觉由细轨道单独绘制。
        Slider(
            value = progress,
            onValueChange = onValueChange,
            onValueChangeFinished = onValueChangeFinished,
            enabled = enabled,
            modifier = Modifier.requiredWidth(maxWidth + 18.dp).height(28.dp).alpha(0f)
        )
        ComposeCanvas(Modifier.matchParentSize()) {
            val strokeWidth = lineWidth.toPx()
            val radius = 5.dp.toPx()
            val left = radius
            val right = (size.width - radius).coerceAtLeast(left)
            val thumbX = if (rtl) right - (right - left) * progress else left + (right - left) * progress
            val y = size.height / 2f
            drawLine(color.copy(alpha = 0.3f), Offset(left, y), Offset(right, y), strokeWidth, StrokeCap.Round)
            drawLine(color, Offset(if (rtl) right else left, y), Offset(thumbX, y), strokeWidth, StrokeCap.Round)
            drawCircle(color, radius = radius, center = Offset(thumbX, y))
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

internal fun getQualityText(
    bitrate: Int,
    actualQuality: String,
    isFMMode: Boolean = false,
    isIntelligenceMode: Boolean = false,
    showQuality: Boolean = true,
    fmModeLabel: String = "私人FM"
): String {
    val quality = actualQuality.ifBlank {
        if (bitrate > 0) com.hypermusic.miuix.data.model.AudioQuality.neteaseActual(bitrate, "") else "未知音质"
    }
    val prefix = when {
        isFMMode -> fmModeLabel
        isIntelligenceMode -> "心动模式"
        else -> ""
    }
    // 关闭「显示当前歌曲音质」时只保留私人FM/心动模式文字
    if (!showQuality) return prefix
    return if (prefix.isEmpty()) quality else "$prefix · $quality"
}

/**
 * 使用共享的封面 Bitmap 渲染封面，避免每个位置重复联网获取封面。
 * bitmap 为 null 时展示占位图。
 */
@Composable
private fun CoverImage(
    bitmap: ImageBitmap?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Image(
            painter = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter(),
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

/**
 * 私人FM / 心动模式下的喜欢（取消喜欢）按钮。
 * 未喜欢时直接喜欢，已喜欢时弹出取消喜欢确认框。
 */
@Composable
private fun FmLikeButton(
    isLiked: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val playerDark = rememberPlayerDark()
    val playerText = if (playerDark) Color.White else Color.Black
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = if (isLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (isLiked) "取消喜欢" else "喜欢",
            tint = if (isLiked) playerText else playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerFavoriteButton(
    isLiked: Boolean,
    playerText: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = if (isLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
            contentDescription = if (isLiked) "取消收藏" else "收藏",
            tint = if (isLiked) playerText else playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerShareButton(
    playerText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = MiuixIcons.Share,
            contentDescription = "分享",
            tint = playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun PlayerCommentButton(
    playerText: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick, modifier = modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Comment,
            contentDescription = "评论",
            tint = playerText.copy(alpha = 0.6f),
            modifier = Modifier.size(24.dp)
        )
    }
}
