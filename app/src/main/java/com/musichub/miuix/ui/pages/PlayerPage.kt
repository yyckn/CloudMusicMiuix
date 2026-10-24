package com.musichub.miuix.ui.pages

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import com.musichub.miuix.ui.theme.KeyboardShortcutSettings
import com.musichub.miuix.ui.theme.ShortcutAction
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.data.lyrics.LyricUpgradeBlacklist
import com.musichub.miuix.data.lyrics.LyricUpgradeRequiredList
import com.musichub.miuix.ui.components.LyricsView
import com.musichub.miuix.ui.theme.ThemeSettings
import com.musichub.miuix.ui.theme.rememberPlayerDark
import com.musichub.miuix.viewmodel.LyricState
import com.musichub.miuix.viewmodel.MusicPlayerViewModel
import com.musichub.miuix.viewmodel.PlayerState
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
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.CheckboxPreference
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
import top.yukonga.miuix.kmp.icon.extended.ListView
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
    onDownloadAndShare: () -> Unit = {},
    onDownloadCoverAndShare: () -> Unit = {},
    onShare: () -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    onLoadLyrics: () -> Unit = {},
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
    val controlIconAlpha = if (controlsLocked) 0.35f else 1f
    val onPreviousSafe: () -> Unit = { if (!controlsLocked) onPrevious() }
    val onNextSafe: () -> Unit = { if (!controlsLocked) onNext() }
    val onSwitchPlayModeSafe: () -> Unit = { if (!controlsLocked) onSwitchPlayMode() }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showLyricUpgradeSheet by remember { mutableStateOf(false) }
    var restoreOriginalLyrics by remember { mutableStateOf(false) }
    var forceUpgradeLyrics by remember { mutableStateOf(false) }
    // 仅在开启「自动升级为逐字歌词」时展示歌词来源标识
    val lyricSourceLabel = if (ThemeSettings.wordByWordLyrics && ThemeSettings.autoUpgradeLyrics) {
        lyricState.source
    } else {
        null
    }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    var showSpeedDialog by remember { mutableStateOf(false) }
    var speedInput by remember { mutableStateOf("") }
    val playlistListState = rememberLazyListState()
    val isPureMusic = lyricState.lyrics?.isPureMusic == true
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

    // 封面只联网拉取一次，之后所有位置复用同一张 Bitmap 进行渲染
    var coverBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(track?.imgUrl) {
        val url = track?.imgUrl
        if (url.isNullOrEmpty()) {
            coverBitmap = null
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(url)
            .size(1080)
            .memoryCacheKey("player_cover_$url")
            .diskCacheKey(url)
            .build()
        when (val result = context.imageLoader.execute(request)) {
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
            else -> { /* 保留上一张封面，渲染时使用占位图 */ }
        }
    }
    
    // 音质选择
    val qualityOptions = listOf(
        "Hi-Res 无损",
        "无损",
        "高品质",
        "标准品质",
        "低品质"
    )

    // 获取专辑歌手信息
    LaunchedEffect(track?.albumId) {
        if (track?.albumId?.isNotEmpty() == true) {
            val albumIdStr = track.albumId.removePrefix("nealbum_")
            val albumId = albumIdStr.toLongOrNull()
            if (albumId != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.musichub.miuix.data.api.NeteaseApi.getAlbumDetail(albumId.toString()).fold(
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
            }
        } else {
            albumArtist = null
            albumArtistId = null
        }
    }

    // 通知外部抽屉状态变化
    LaunchedEffect(showPlaylistSheet, showMoreSheet, showLyricUpgradeSheet) {
        onDrawerStateChanged(showPlaylistSheet || showMoreSheet || showLyricUpgradeSheet)
    }

    // 打开歌词来源弹窗时，同步当前歌曲的黑名单/需要升级状态
    LaunchedEffect(showLyricUpgradeSheet) {
        if (showLyricUpgradeSheet) {
            val track = playerState.currentTrack
            restoreOriginalLyrics = track != null && LyricUpgradeBlacklist.contains(track.id)
            forceUpgradeLyrics = track != null && LyricUpgradeRequiredList.contains(track.id)
        }
    }
    // 始终有2页（封面+歌词），纯音乐时歌词页显示"纯音乐，请欣赏"
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    
    // 全屏封面模式下，歌词显示状态
    var showLyricsInFullScreen by remember { mutableStateOf(false) }
    val coverStyleIndex = ThemeSettings.smallScreenCoverStyle // 小屏: 0=卡片, 1=全屏渐变
    val largeScreenCoverStyleSetting = ThemeSettings.largeScreenCoverStyle // 大屏: 0=卡片, 1=全屏渐变
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
    val useRoundCover = ThemeSettings.useRoundCover
    val coverRotationIndex = ThemeSettings.coverRotationIndex
    val isPlaying = playerState.isPlaying
    
    // 计算封面旋转角度
    val coverRotation = when {
        !useRoundCover -> 0f
        coverRotationIndex == 0 -> 0f // 静止
        coverRotationIndex == 1 && isPlaying -> { // 播放时旋转
            val infiniteTransition = rememberInfiniteTransition(label = "cover_rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 8000, easing = LinearEasing)
                ),
                label = "rotation"
            )
            rotation
        }
        coverRotationIndex == 2 -> { // 始终旋转
            val infiniteTransition = rememberInfiniteTransition(label = "cover_rotation")
            val rotation by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 8000, easing = LinearEasing)
                ),
                label = "rotation"
            )
            rotation
        }
        else -> 0f
    }

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
    LaunchedEffect(showSpeedDialog, showUnlikeDialog, showMoreSheet, showPlaylistSheet, showLyricUpgradeSheet) {
        if (!showSpeedDialog && !showUnlikeDialog && !showMoreSheet && !showPlaylistSheet && !showLyricUpgradeSheet) {
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
                when (event.nativeKeyEvent.keyCode) {
                    KeyboardShortcutSettings.keyFor(ShortcutAction.PlayPause) -> {
                        onTogglePlayPause()
                        true
                    }
                    KeyboardShortcutSettings.keyFor(ShortcutAction.Previous) -> {
                        onPrevious()
                        true
                    }
                    KeyboardShortcutSettings.keyFor(ShortcutAction.Next) -> {
                        onNext()
                        true
                    }
                    KeyboardShortcutSettings.keyFor(ShortcutAction.SeekBackward) -> {
                        onSeekTo((playerState.currentPosition - 10_000L).coerceAtLeast(0L))
                        true
                    }
                    KeyboardShortcutSettings.keyFor(ShortcutAction.SeekForward) -> {
                        val target = playerState.currentPosition + 10_000L
                        onSeekTo(
                            if (playerState.duration > 0L) target.coerceAtMost(playerState.duration)
                            else target
                        )
                        true
                    }
                    KeyboardShortcutSettings.keyFor(ShortcutAction.Like) -> {
                        onLikeTrack()
                        true
                    }
                    else -> false
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
        val screenAspectRatio =
            configuration.screenWidthDp.toFloat() / configuration.screenHeightDp.toFloat()
        val largeScreenCoverStyle =
            if (largeScreenCoverStyleSetting == 1 && screenAspectRatio < 4f / 3f) 0
            else largeScreenCoverStyleSetting
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
                                        Text(text = track?.title ?: "未知歌曲", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = playerText, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}", fontSize = 14.sp, color = playerText.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    }
                                    if (showCastButton) {
                                        IconButton(onClick = onOpenSystemUI) {
                                            Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "投屏", tint = playerText)
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
                                        LyricsView(lyrics = lyricState.lyrics, currentPosition = playerState.currentPosition, onSeekTo = onSeekTo, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = lyricHorizontalPadding), source = lyricSourceLabel, sourceColor = playerText.copy(alpha = 0.5f), sourceStartPadding = 24.dp - lyricHorizontalPadding, showSourceHelp = true, onSourceClick = { showLyricUpgradeSheet = true }, positionProvider = liveLyricsPositionProvider)
                                    }
                                }
                                
                                // 控制按钮（底部）
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                                    var seekPosition by remember { mutableStateOf<Long?>(null) }
                                    val displayPosition = seekPosition ?: playerState.currentPosition
                                    val progress = if (playerState.duration > 0) displayPosition.toFloat() / playerState.duration.toFloat() else 0f
                                    Slider(value = progress, onValueChange = { newValue -> seekPosition = (newValue * playerState.duration).toLong() }, onValueChangeFinished = { seekPosition?.let { onSeekTo(it) }; seekPosition = null }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = formatDuration(displayPosition), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                        Text(text = getQualityText(playerState.bitrate, isFMMode, isIntelligenceMode, ThemeSettings.showQuality), fontSize = 12.sp, color = playerText)
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
                                                IconButton(onClick = { if (!controlsLocked) showPlaylistSheet = true }, modifier = Modifier.size(48.dp).alpha(controlIconAlpha)) { Icon(imageVector = MiuixIcons.ListView, contentDescription = "播放列表", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
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
                                    text = track?.title ?: "未知歌曲",
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = playerText,
                                    maxLines = if (isTablet) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                    fontSize = 14.sp,
                                    color = playerText.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // 投屏按钮
                            if (showCastButton) {
                                IconButton(onClick = onOpenSystemUI) {
                                    Icon(
                                        imageVector = MiuixIcons.ScreenMirroring,
                                        contentDescription = "投屏",
                                        tint = playerText
                                    )
                                }
                            }
                        }

                        // 封面（小屏模式下隐藏）
                        if (!isSmallScreen) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Card(
                                    modifier = Modifier
                                        .size(imageSize)
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

                            Slider(
                                value = progress,
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
                                    .padding(bottom = 4.dp)
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
                                    text = getQualityText(playerState.bitrate, isFMMode, isIntelligenceMode, ThemeSettings.showQuality),
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
                                                imageVector = MiuixIcons.ListView,
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
                                    lyrics = lyricState.lyrics,
                                    currentPosition = playerState.currentPosition,
                                    onSeekTo = onSeekTo,
                                    isPlaying = playerState.isPlaying,
                                    modifier = Modifier.fillMaxSize(),
                                    source = lyricSourceLabel,
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
                                    Text(text = track?.title ?: "未知歌曲", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = playerText, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}", fontSize = 14.sp, color = playerText.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                }
                                if (showCastButton) {
                                    IconButton(onClick = onOpenSystemUI) {
                                        Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "投屏", tint = playerText)
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
                                            LyricsView(lyrics = lyricState.lyrics, currentPosition = playerState.currentPosition, onSeekTo = onSeekTo, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = lyricHorizontalPadding), source = lyricSourceLabel, sourceColor = playerText.copy(alpha = 0.5f), sourceStartPadding = 24.dp - lyricHorizontalPadding, showSourceHelp = true, onSourceClick = { showLyricUpgradeSheet = true }, positionProvider = liveLyricsPositionProvider)
                                        }
                                    }
                                }
                            }
                            
                            // 底部控制区域
                            Column(
                                modifier = Modifier
                                    .padding(horizontal = 24.dp)
                                    .onGloballyPositioned { coordinates ->
                                        controlsTopY = topPaddingPx + coordinates.positionInParent().y
                                    }
                            ) {
                                var seekPosition by remember { mutableStateOf<Long?>(null) }
                                val displayPosition = seekPosition ?: playerState.currentPosition
                                val progress = if (playerState.duration > 0) displayPosition.toFloat() / playerState.duration.toFloat() else 0f
                                Slider(value = progress, onValueChange = { newValue -> seekPosition = (newValue * playerState.duration).toLong() }, onValueChangeFinished = { seekPosition?.let { onSeekTo(it) }; seekPosition = null }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = formatDuration(displayPosition), fontSize = 12.sp, color = playerText.copy(alpha = 0.6f))
                                    Text(text = getQualityText(playerState.bitrate, isFMMode, isIntelligenceMode, ThemeSettings.showQuality), fontSize = 12.sp, color = playerText)
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
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭", tint = playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                        if (isFMMode || isIntelligenceMode) {
                                            Spacer(modifier = Modifier.width(16.dp))
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
                                        } else {
                                            Spacer(modifier = Modifier.width(16.dp))
                                            IconButton(onClick = { showLyricsInFullScreen = !showLyricsInFullScreen }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (showLyricsInFullScreen) MiuixIcons.NotesFill else MiuixIcons.Notes, contentDescription = "歌词", tint = if (showLyricsInFullScreen) playerText else playerText.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            PlayerFavoriteButton(
                                                isLiked = isCurrentTrackLiked,
                                                playerText = playerText,
                                                onClick = {
                                                    if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(16.dp))
                                            PlayerShareButton(playerText) { onShare() }
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
                                text = track?.title ?: "未知歌曲",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Medium,
                                color = playerText,
                                maxLines = if (isTablet) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                fontSize = 14.sp,
                                color = playerText.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // 投屏按钮
                        if (showCastButton) {
                            IconButton(onClick = onOpenSystemUI) {
                                Icon(
                                    imageVector = MiuixIcons.ScreenMirroring,
                                    contentDescription = "投屏",
                                    tint = playerText
                                )
                            }
                        }
                    }

                    // 水平翻页器（封面/歌词）
                    HorizontalPager(
                        state = pagerState,
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
                                                text = track?.title ?: "未知歌曲",
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = playerText,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                                fontSize = 16.sp,
                                                color = playerText.copy(alpha = 0.7f),
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                } else {
                                    // 正常模式：显示封面
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Card(
                                            modifier = Modifier
                                                .size(imageSize)
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
                                        lyrics = lyricState.lyrics,
                                        currentPosition = playerState.currentPosition,
                                        onSeekTo = onSeekTo,
                                        isPlaying = playerState.isPlaying,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = lyricHorizontalPadding),
                                        source = lyricSourceLabel,
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

                    // 底部控制区域
                    Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                        // 播放进度条
                        var seekPosition by remember { mutableStateOf<Long?>(null) }
                        val displayPosition = seekPosition ?: playerState.currentPosition
                        val progress = if (playerState.duration > 0) {
                            displayPosition.toFloat() / playerState.duration.toFloat()
                        } else {
                            0f
                        }

                        Slider(
                            value = progress,
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
                                .padding(bottom = 4.dp)
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
                            text = getQualityText(playerState.bitrate, isFMMode, isIntelligenceMode, ThemeSettings.showQuality),
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
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 关闭按钮
                            IconButton(
                                onClick = onBackClick,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Close,
                                    contentDescription = "关闭",
                                    tint = playerText.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (isFMMode || isIntelligenceMode) {
                                Spacer(modifier = Modifier.width(16.dp))
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
                            } else {
                                Spacer(modifier = Modifier.width(16.dp))
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
                                PlayerFavoriteButton(
                                    isLiked = isCurrentTrackLiked,
                                    playerText = playerText,
                                    onClick = {
                                        if (isCurrentTrackLiked) showUnlikeDialog = true else onLikeTrack()
                                    }
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                PlayerShareButton(playerText) { onShare() }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
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
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp)
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
                            if (isVipAccount) {
                                WindowDropdownPreference(
                                    title = "音质",
                                    summary = "*选择的音质仅代表可获取的最高音质，实际音频音质可能因歌曲质量而不同",
                                    items = qualityOptions,
                                    selectedIndex = viewModel.audioQualityIndex,
                                    startAction = {
                                        Icon(
                                            imageVector = MiuixIcons.Music,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    onSelectedIndexChange = { newIndex ->
                                        viewModel.updateAudioQualityIndex(newIndex)
                                    }
                                )
                            } else {
                                BasicComponent(
                                    title = "音质",
                                    summary = "登录VIP账号后可享受更高音质",
                                    startAction = {
                                        Icon(
                                            imageVector = MiuixIcons.Music,
                                            contentDescription = null,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    },
                                    endActions = {
                                        Text(
                                            text = "低品质",
                                            style = MiuixTheme.textStyles.body2,
                                            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                )
                            }
                            BasicComponent(
                                title = "播放速度",
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
                                        magnetThreshold = 0.1f,
                                        hapticEffect = SliderDefaults.SliderHapticEffect.Step,
                                        modifier = Modifier.padding(vertical = 4.dp)
                                    )
                                },
                                onClick = {
                                    speedInput = formatSpeedValue(viewModel.playbackSpeed)
                                    showSpeedDialog = true
                                }
                            )
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
                                    onShare()
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
                                    track?.let { t ->
                                        onNavigateToComment(t.id, t.title)
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

        // 歌词来源 / 自动升级歌词格式
        val upgradeTrack = playerState.currentTrack
        val upgraded = lyricState.upgraded
        val neteaseHasWordLyrics = !upgraded &&
            (lyricState.lyrics?.lines?.any { !it.words.isNullOrEmpty() } == true)
        val upgradeSummary = when {
            upgraded -> "此歌曲已为您替换为其他平台的逐字歌词，如果您不需要此功能，可以在设置中关闭“自动升级为逐字歌词”。"
            neteaseHasWordLyrics -> "检测到此歌曲在网易云音乐平台已支持逐字歌词，我们已跳过升级为其他平台的逐字歌曲。"
            else -> "检测到此歌曲在网易云音乐平台暂未提供逐字歌词，可尝试从其他平台获取逐字歌词。"
        }
        OverlayDialog(
            show = showLyricUpgradeSheet,
            title = "自动升级歌词格式",
            summary = upgradeSummary,
            onDismissRequest = { showLyricUpgradeSheet = false },
            content = {
                Column {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        if (upgraded) {
                            CheckboxPreference(
                                title = "此歌曲识别有误，恢复原歌词",
                                checked = restoreOriginalLyrics,
                                onCheckedChange = { restoreOriginalLyrics = it }
                            )
                        } else {
                            CheckboxPreference(
                                title = if (neteaseHasWordLyrics) {
                                    "此歌曲实际没有逐字歌词，升级至其他平台逐字歌词"
                                } else {
                                    "升级至其他平台逐字歌词"
                                },
                                checked = forceUpgradeLyrics,
                                onCheckedChange = { forceUpgradeLyrics = it }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(
                            text = "取消",
                            onClick = { showLyricUpgradeSheet = false },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(20.dp))
                        TextButton(
                            text = "保存",
                            onClick = {
                                if (upgradeTrack != null) {
                                    if (upgraded) {
                                        if (restoreOriginalLyrics) {
                                            LyricUpgradeBlacklist.add(
                                                LyricUpgradeBlacklist.Entry(
                                                    id = upgradeTrack.id,
                                                    title = upgradeTrack.title,
                                                    artist = upgradeTrack.artist,
                                                    album = upgradeTrack.album
                                                )
                                            )
                                            LyricUpgradeRequiredList.remove(upgradeTrack.id)
                                        } else {
                                            LyricUpgradeBlacklist.remove(upgradeTrack.id)
                                        }
                                    } else {
                                        if (forceUpgradeLyrics) {
                                            LyricUpgradeRequiredList.add(
                                                LyricUpgradeRequiredList.Entry(
                                                    id = upgradeTrack.id,
                                                    title = upgradeTrack.title,
                                                    artist = upgradeTrack.artist,
                                                    album = upgradeTrack.album
                                                )
                                            )
                                            LyricUpgradeBlacklist.remove(upgradeTrack.id)
                                        } else {
                                            LyricUpgradeRequiredList.remove(upgradeTrack.id)
                                        }
                                    }
                                    viewModel.loadLyrics()
                                }
                                showLyricUpgradeSheet = false
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.textButtonColorsPrimary()
                        )
                    }
                }
            }
        )

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

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun getQualityText(
    bitrate: Int,
    isFMMode: Boolean = false,
    isIntelligenceMode: Boolean = false,
    showQuality: Boolean = true
): String {
    val quality = when {
        bitrate <= 0 -> "未知品质"
        bitrate <= 320000 -> "低品质"
        bitrate <= 1411000 -> "SQ 标准品质"
        bitrate <= 2304000 -> "HQ 高品质"
        else -> "Hi-Res 无损音频"
    }
    val prefix = when {
        isFMMode -> "私人FM"
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
            painter = painterResource(android.R.drawable.ic_menu_gallery),
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
