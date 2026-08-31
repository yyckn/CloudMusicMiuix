package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.ui.components.LyricsView
import com.cloudmusic.miuix.ui.theme.ThemeSettings
import com.cloudmusic.miuix.viewmodel.LyricState
import com.cloudmusic.miuix.viewmodel.PlayerState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
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
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.Merge
import top.yukonga.miuix.kmp.icon.extended.More
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
    playerState: PlayerState,
    lyricState: LyricState,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    isFMMode: Boolean = false,
    isIntelligenceMode: Boolean = false,
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
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showUnlikeDialog by remember { mutableStateOf(false) }
    val playlistListState = rememberLazyListState()
    val isPureMusic = lyricState.lyrics?.isPureMusic == true
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    
    // 音质选择
    val qualityOptions = listOf(
        "Hi-Res 无损",
        "无损",
        "高品质",
        "标准品质",
        "低品质"
    )
    val qualityBitrates = listOf(999000, 999000, 740000, 320000, 128000)
    var selectedQualityIndex by remember { 
        mutableStateOf(
            context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
                .getInt("audio_quality_index", 0)
        ) 
    }

    // 获取专辑歌手信息
    LaunchedEffect(track?.albumId) {
        if (track?.albumId?.isNotEmpty() == true) {
            val albumIdStr = track.albumId.removePrefix("nealbum_")
            val albumId = albumIdStr.toLongOrNull()
            if (albumId != null) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    com.cloudmusic.miuix.data.api.NeteaseApi.getAlbumDetail(albumId.toString()).fold(
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
    LaunchedEffect(showPlaylistSheet, showMoreSheet) {
        onDrawerStateChanged(showPlaylistSheet || showMoreSheet)
    }
    // 始终有2页（封面+歌词），纯音乐时歌词页显示"纯音乐，请欣赏"
    val pageCount = 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()
    
    // 全屏封面模式下，歌词显示状态
    var showLyricsInFullScreen by remember { mutableStateOf(false) }
    val coverStyleIndex = ThemeSettings.coverStyleIndex // 0=卡片, 1=全屏渐变
    
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
        // 预留底部控件高度：Slider区域 + 按钮区域 + 底部间距
        val bottomReserved = if (isTablet) 186.dp else 186.dp
        // 标题区域高度（歌曲名称 + 歌手专辑 + 间距）
        val titleAreaHeight = 80.dp
        // 封面可用高度 = 屏幕高度 - 顶部padding - 底部预留 - 标题区域高度 - 额外间距
        val availableHeight = screenHeight - topPadding - bottomReserved - titleAreaHeight - 48.dp
        // 封面大小取高度和宽度的最小值，确保1:1比例不被裁剪
        val imageSize = minOf(availableHeight, screenWidth - 24.dp, screenHeight - topPadding - 240.dp).coerceAtLeast(0.dp)
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
                    targetState = track?.imgUrl,
                    animationSpec = tween(durationMillis = 500),
                    label = "bg_cover_crossfade"
                ) { imgUrl ->
                    AsyncImage(
                        model = imgUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = 1.15f,
                                scaleY = 1.15f
                            )
                            .blur(radius = 100.dp),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                }
                // 压暗层 #000000 25%不透明度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40000000))
                )
            }
            
            if (isTablet) {
                // 平板布局
                if (coverStyleIndex == 1 || coverStyleIndex == 2) {
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
                            Crossfade(
                                targetState = track?.imgUrl,
                                animationSpec = tween(durationMillis = 500),
                                label = "tablet_cover_crossfade"
                            ) { imgUrl ->
                                AsyncImage(
                                    model = imgUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop,
                                    error = painterResource(android.R.drawable.ic_menu_gallery)
                                )
                            }
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
                                        Text(text = track?.title ?: "未知歌曲", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    }
                                    IconButton(onClick = onOpenSystemUI) {
                                        Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "投屏", tint = Color.White)
                                    }
                                }
                                
                                // 歌词区域（中间）
                                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                    if (isPureMusic) {
                                        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                                            Text(text = "纯音乐，请欣赏", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    } else {
                                        LyricsView(lyrics = lyricState.lyrics, currentPosition = playerState.currentPosition, onSeekTo = onSeekTo, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp))
                                    }
                                }
                                
                                // 控制按钮（底部）
                                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 16.dp)) {
                                    val progress = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration.toFloat() else 0f
                                    Slider(value = progress, onValueChange = { newValue -> onSeekTo((newValue * playerState.duration).toLong()) }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(text = formatDuration(playerState.currentPosition), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                        Text(text = buildString { if (isFMMode) append("FM模式 · "); else if (isIntelligenceMode) append("心动模式 · "); append(getQualityText(playerState.bitrate)) }, fontSize = 12.sp, color = Color.White)
                                        Text(text = formatDuration(playerState.duration), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(32.dp)) }
                                            Spacer(modifier = Modifier.width(24.dp))
                                            Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                                if (playerState.isLoading) InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
                                                else IconButton(onClick = onTogglePlayPause) { Icon(imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play, contentDescription = if (playerState.isPlaying) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(40.dp)) }
                                            }
                                            Spacer(modifier = Modifier.width(24.dp))
                                            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(32.dp)) }
                                        }
                                        Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                            IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            if (!isFMMode && !isIntelligenceMode) {
                                                Spacer(modifier = Modifier.width(16.dp))
                                                IconButton(onClick = onSwitchPlayMode, modifier = Modifier.size(48.dp)) { Icon(imageVector = when (playMode) { 0 -> MiuixIcons.Sort; 1 -> MiuixIcons.Merge; 2 -> MiuixIcons.Replace; else -> MiuixIcons.Sort }, contentDescription = when (playMode) { 0 -> "顺序播放"; 1 -> "乱序播放"; 2 -> "单曲循环"; else -> "顺序播放" }, tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                            }
                                            Spacer(modifier = Modifier.width(16.dp))
                                            if (!isFMMode && !isIntelligenceMode) {
                                                IconButton(onClick = { showPlaylistSheet = true }, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.ListView, contentDescription = "播放列表", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                                Spacer(modifier = Modifier.width(16.dp))
                                            }
                                            IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.More, contentDescription = "更多", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
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
                                    color = Color.White,
                                    maxLines = if (isTablet) 1 else 2,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                    fontSize = 14.sp,
                                    color = Color.White.copy(alpha = 0.7f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            // 投屏按钮
                            IconButton(onClick = onOpenSystemUI) {
                                Icon(
                                    imageVector = MiuixIcons.ScreenMirroring,
                                    contentDescription = "投屏",
                                    tint = Color.White
                                )
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
                                Crossfade(
                                    targetState = track?.imgUrl,
                                    animationSpec = tween(durationMillis = 500),
                                    label = "cover_crossfade"
                                ) { imgUrl ->
                                    AsyncImage(
                                        model = imgUrl,
                                        contentDescription = track?.title,
                                        modifier = Modifier
                                            .size(imageSize)
                                            .aspectRatio(1f)
                                            .clip(coverShape)
                                            .graphicsLayer {
                                                rotationZ = coverRotation
                                            },
                                        contentScale = ContentScale.Crop,
                                        error = painterResource(android.R.drawable.ic_menu_gallery)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                            // 播放进度条
                            val progress = if (playerState.duration > 0) {
                                playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                            } else {
                                0f
                            }

                            Slider(
                                value = progress,
                                onValueChange = { newValue ->
                                    val newPosition = (newValue * playerState.duration).toLong()
                                    onSeekTo(newPosition)
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
                                    text = formatDuration(playerState.currentPosition),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
                                )
                                Text(
                                    text = getQualityText(playerState.bitrate),
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = formatDuration(playerState.duration),
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.6f)
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
                                    IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronBackward,
                                            contentDescription = "上一首",
                                            tint = Color.White,
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
                                            IconButton(onClick = onTogglePlayPause) {
                                                Icon(
                                                    imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(40.dp)
                                                )
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(24.dp))
                                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.ChevronForward,
                                            contentDescription = "下一首",
                                            tint = Color.White,
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
                                            tint = Color.White.copy(alpha = 0.6f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    if (!isFMMode && !isIntelligenceMode) {
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = onSwitchPlayMode, modifier = Modifier.size(48.dp)) {
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
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { showPlaylistSheet = true }, modifier = Modifier.size(48.dp)) {
                                            Icon(
                                                imageVector = MiuixIcons.ListView,
                                                contentDescription = "播放列表",
                                                tint = Color.White.copy(alpha = 0.6f),
                                                modifier = Modifier.size(24.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = MiuixIcons.More,
                                            contentDescription = "更多",
                                            tint = Color.White.copy(alpha = 0.6f),
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
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }
                            } else {
                                // 显示歌词
                                LyricsView(
                                    lyrics = lyricState.lyrics,
                                    currentPosition = playerState.currentPosition,
                                    onSeekTo = onSeekTo,
                                    isPlaying = playerState.isPlaying,
                                    modifier = Modifier.fillMaxSize()
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
                    Box(modifier = Modifier.fillMaxSize()) {
                        // 模糊背景（全屏，覆盖状态栏区域）
                        Crossfade(
                            targetState = track?.imgUrl,
                            animationSpec = tween(durationMillis = 500),
                            label = "fullscreen_bg_crossfade"
                        ) { imgUrl ->
                            AsyncImage(
                                model = imgUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer(scaleX = 1.15f, scaleY = 1.15f)
                                    .blur(radius = 100.dp),
                                contentScale = ContentScale.Crop,
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )
                        }
                        // 深色蒙版
                        Box(modifier = Modifier.fillMaxSize().background(Color(0x40000000)))
                        
                        // 内容层
                        Column(
                            modifier = Modifier.padding(top = topPadding).fillMaxSize()
                        ) {
                            // 歌曲标题和信息
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 16.dp, bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = track?.title ?: "未知歌曲", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth())
                                }
                                IconButton(onClick = onOpenSystemUI) {
                                    Icon(imageVector = MiuixIcons.ScreenMirroring, contentDescription = "投屏", tint = Color.White)
                                }
                            }
                            
                            // 中间区域：封面 + 歌词
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth()
                                    .clickable(
                                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                                        indication = null
                                    ) { showLyricsInFullScreen = !showLyricsInFullScreen },
                                contentAlignment = Alignment.Center
                            ) {
                                // 清晰封面
                                Box(
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f)
                                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                        .drawWithContent {
                                            drawContent()
                                            drawRect(
                                                brush = Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black, Color.Black, Color.Transparent)),
                                                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                                            )
                                        }
                                ) {
                                    Crossfade(targetState = track?.imgUrl, animationSpec = tween(durationMillis = 500), label = "fullscreen_cover_crossfade") { imgUrl ->
                                        AsyncImage(
                                            model = imgUrl, contentDescription = null,
                                            modifier = Modifier.fillMaxSize().then(if (showLyricsInFullScreen) Modifier.blur(10.dp) else Modifier),
                                            contentScale = ContentScale.Crop,
                                            error = painterResource(android.R.drawable.ic_menu_gallery)
                                        )
                                    }
                                    if (lyricsAlpha > 0f) {
                                        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF333333).copy(alpha = maskAlpha)))
                                    }
                                }
                                
                                // 歌词显示
                                if (lyricsAlpha > 0f) {
                                    Box(modifier = Modifier.fillMaxSize().graphicsLayer { alpha = lyricsAlpha }) {
                                        if (isPureMusic) {
                                            Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp), contentAlignment = Alignment.Center) {
                                                Text(text = "纯音乐，请欣赏", fontSize = 16.sp, color = Color.White.copy(alpha = 0.5f))
                                            }
                                        } else {
                                            LyricsView(lyrics = lyricState.lyrics, currentPosition = playerState.currentPosition, onSeekTo = onSeekTo, isPlaying = playerState.isPlaying, modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp))
                                        }
                                    }
                                }
                            }
                            
                            // 底部控制区域
                            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                                val progress = if (playerState.duration > 0) playerState.currentPosition.toFloat() / playerState.duration.toFloat() else 0f
                                Slider(value = progress, onValueChange = { newValue -> onSeekTo((newValue * playerState.duration).toLong()) }, enabled = !playerState.isLoading, modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
                                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = formatDuration(playerState.currentPosition), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                    Text(text = buildString { if (isFMMode) append("FM模式 · "); else if (isIntelligenceMode) append("心动模式 · "); append(getQualityText(playerState.bitrate)) }, fontSize = 12.sp, color = Color.White)
                                    Text(text = formatDuration(playerState.duration), fontSize = 12.sp, color = Color.White.copy(alpha = 0.6f))
                                }
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) { Icon(imageVector = MiuixIcons.ChevronBackward, contentDescription = "上一首", tint = Color.White, modifier = Modifier.size(32.dp)) }
                                        Spacer(modifier = Modifier.width(24.dp))
                                        Box(modifier = Modifier.size(72.dp), contentAlignment = Alignment.Center) {
                                            if (playerState.isLoading) InfiniteProgressIndicator(modifier = Modifier.size(40.dp))
                                            else IconButton(onClick = onTogglePlayPause) { Icon(imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play, contentDescription = if (playerState.isPlaying) "暂停" else "播放", tint = Color.White, modifier = Modifier.size(40.dp)) }
                                        }
                                        Spacer(modifier = Modifier.width(24.dp))
                                        IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) { Icon(imageVector = MiuixIcons.ChevronForward, contentDescription = "下一首", tint = Color.White, modifier = Modifier.size(32.dp)) }
                                    }
                                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = onBackClick, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.Close, contentDescription = "关闭", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { showLyricsInFullScreen = !showLyricsInFullScreen }, modifier = Modifier.size(48.dp)) { Icon(imageVector = if (showLyricsInFullScreen) MiuixIcons.NotesFill else MiuixIcons.Notes, contentDescription = "歌词", tint = if (showLyricsInFullScreen) Color.White else Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
                                        Spacer(modifier = Modifier.width(16.dp))
                                        IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) { Icon(imageVector = MiuixIcons.More, contentDescription = "更多", tint = Color.White.copy(alpha = 0.6f), modifier = Modifier.size(24.dp)) }
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
                        .padding(horizontal = 24.dp)
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
                                color = Color.White,
                                maxLines = if (isTablet) 1 else 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                fontSize = 14.sp,
                                color = Color.White.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        // 投屏按钮
                        IconButton(onClick = onOpenSystemUI) {
                            Icon(
                                imageVector = MiuixIcons.ScreenMirroring,
                                contentDescription = "投屏",
                                tint = Color.White
                            )
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
                                                color = Color.White,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Text(
                                                text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                                                fontSize = 16.sp,
                                                color = Color.White.copy(alpha = 0.7f),
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
                                        Crossfade(
                                            targetState = track?.imgUrl,
                                            animationSpec = tween(durationMillis = 500),
                                            label = "cover_crossfade"
                                        ) { imgUrl ->
                                            AsyncImage(
                                                model = imgUrl,
                                                contentDescription = track?.title,
                                                modifier = Modifier
                                                    .size(imageSize)
                                                    .aspectRatio(1f)
                                                    .clip(coverShape)
                                                    .graphicsLayer {
                                                        rotationZ = coverRotation
                                                    },
                                                contentScale = ContentScale.Crop,
                                                error = painterResource(android.R.drawable.ic_menu_gallery)
                                            )
                                        }
                                    }
                                }
                            }
                            1 -> {
                                // 歌词页面
                                if (isPureMusic) {
                                    // 纯音乐显示提示
                                    Box(
                                        modifier = Modifier.fillMaxSize(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "纯音乐，请欣赏",
                                            fontSize = 16.sp,
                                            color = Color.White.copy(alpha = 0.5f)
                                        )
                                    }
                                } else {
                                    LyricsView(
                                        lyrics = lyricState.lyrics,
                                        currentPosition = playerState.currentPosition,
                                        onSeekTo = onSeekTo,
                                        isPlaying = playerState.isPlaying,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }
                    }

                    // 播放进度条
                    val progress = if (playerState.duration > 0) {
                        playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                    } else {
                        0f
                    }

                    Slider(
                        value = progress,
                        onValueChange = { newValue ->
                            val newPosition = (newValue * playerState.duration).toLong()
                            onSeekTo(newPosition)
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
                            text = formatDuration(playerState.currentPosition),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
                        )
                        Text(
                            text = getQualityText(playerState.bitrate),
                            fontSize = 12.sp,
                            color = Color.White
                        )
                        Text(
                            text = formatDuration(playerState.duration),
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f)
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
                            IconButton(onClick = onPrevious, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronBackward,
                                    contentDescription = "上一首",
                                    tint = Color.White,
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
                                    IconButton(onClick = onTogglePlayPause) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                            contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(24.dp))
                            IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                                Icon(
                                    imageVector = MiuixIcons.ChevronForward,
                                    contentDescription = "下一首",
                                    tint = Color.White,
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
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (!isFMMode && !isIntelligenceMode) {
                                Spacer(modifier = Modifier.width(16.dp))
                                // 播放模式按钮
                                IconButton(
                                    onClick = onSwitchPlayMode,
                                    modifier = Modifier.size(48.dp)
                                ) {
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
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
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
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            if (!isFMMode && !isIntelligenceMode) {
                                Spacer(modifier = Modifier.width(16.dp))
                                IconButton(onClick = { showPlaylistSheet = true }, modifier = Modifier.size(48.dp)) {
                                    Icon(
                                        imageVector = MiuixIcons.ListView,
                                        contentDescription = "播放列表",
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = { showMoreSheet = true }, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
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
                startAction = {
                    IconButton(
                        onClick = { shouldScrollToCurrent = true }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Location,
                            contentDescription = "定位到当前歌曲",
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                },
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
                            WindowDropdownPreference(
                                title = "音质",
                                summary = "*选择的音质仅代表可获取的最高音质，实际音频音质可能因歌曲质量而不同",
                                items = qualityOptions,
                                selectedIndex = selectedQualityIndex,
                                onSelectedIndexChange = { newIndex ->
                                    selectedQualityIndex = newIndex
                                    // 保存到SharedPreferences
                                    context.getSharedPreferences("player_prefs", android.content.Context.MODE_PRIVATE)
                                        .edit()
                                        .putInt("audio_quality_index", newIndex)
                                        .apply()
                                }
                            )
                        }
                    }
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
}

private fun formatDuration(durationMs: Long): String {
    if (durationMs <= 0) return "0:00"
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

private fun getQualityText(bitrate: Int): String {
    return when {
        bitrate <= 0 -> "未知品质"
        bitrate <= 320000 -> "低品质"
        bitrate <= 1411000 -> "SQ 标准品质"
        bitrate <= 2304000 -> "HQ 高品质"
        else -> "Hi-Res 无损音频"
    }
}
