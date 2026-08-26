package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.cloudmusic.miuix.viewmodel.LyricState
import com.cloudmusic.miuix.viewmodel.PlayerState
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ChevronBackward
import top.yukonga.miuix.kmp.icon.extended.ChevronForward
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.ScreenMirroring
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun PlayerPage(
    playerState: PlayerState,
    lyricState: LyricState,
    currentPlaylist: List<Track> = emptyList(),
    isCurrentTrackLiked: Boolean = false,
    onBackClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDownload: () -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    onLoadLyrics: () -> Unit = {},
    onOpenSystemUI: () -> Unit = {},
    onLikeTrack: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val track = playerState.currentTrack
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val playlistListState = rememberLazyListState()
    val isPureMusic = lyricState.lyrics?.isPureMusic == true
    // 纯音乐只有1页（封面），非纯音乐有2页（封面+歌词）
    val pageCount = if (isPureMusic) 1 else 2
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

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

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            // 简单的TopBar，不使用大标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Transparent)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 返回按钮
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = Color.White
                        )
                    }

                    // 更多按钮区域
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 投屏按钮
                        IconButton(onClick = onOpenSystemUI) {
                            Icon(
                                imageVector = MiuixIcons.ScreenMirroring,
                                contentDescription = "投屏",
                                tint = Color.White
                            )
                        }
                        
                        // 更多按钮
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true }
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.More,
                                    contentDescription = "更多",
                                    tint = Color.White
                                )
                            }
                        OverlayListPopup(
                            show = showMoreMenu,
                            alignment = PopupPositionProvider.Align.TopEnd,
                            onDismissRequest = { showMoreMenu = false }
                        ) {
                            ListPopupColumn {
                                DropdownImpl(
                                    text = "下载此歌曲",
                                    optionSize = 1,
                                    isSelected = false,
                                    index = 0,
                                    enabled = true,
                                    onSelectedIndexChange = {
                                        showMoreMenu = false
                                        onDownload()
                                    }
                                )
                            }
                        }
                    }
                    }
                }
            }
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
        val availableHeight = screenHeight - topPadding - bottomReserved - titleAreaHeight - 32.dp
        // 封面大小取高度和宽度的最小值，确保1:1比例不被裁剪
        val imageSize = minOf(availableHeight, screenWidth - 24.dp, screenHeight - topPadding - 200.dp).coerceAtLeast(0.dp)

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
                            .blur(radius = 100.dp),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
                }
                // 压暗层 #161616 25%不透明度
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40161616))
                )
            }
            
            if (isTablet) {
                // 平板布局：左右分屏
                Column(
                    modifier = Modifier
                        .padding(top = topPadding)
                        .fillMaxSize()
                ) {
                    // 歌曲标题和信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp)
                            .padding(top = 8.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = track?.title ?: "未知歌曲",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // 左右分屏区域
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        // 左侧：封面 + 控制按钮
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // 封面
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
                                            .clip(RoundedCornerShape(16.dp)),
                                        contentScale = ContentScale.Fit,
                                        error = painterResource(android.R.drawable.ic_menu_gallery)
                                    )
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
                                    IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
                                        Icon(
                                            imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                            contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                            tint = Color.White,
                                            modifier = Modifier.size(40.dp)
                                        )
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

                                // 第二行：喜欢、播放列表
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(onClick = onLikeTrack, modifier = Modifier.size(48.dp)) {
                                        Icon(
                                            imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                            contentDescription = if (isCurrentTrackLiked) "取消喜欢" else "喜欢",
                                            tint = if (isCurrentTrackLiked) Color.Red else Color.White.copy(alpha = 0.6f),
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
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                }
            } else {
                // 手机布局：原有布局
                Column(
                    modifier = Modifier
                        .padding(top = topPadding)
                        .fillMaxSize()
                        .padding(horizontal = 24.dp)
                ) {
                    // 歌曲标题和信息
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp, bottom = 16.dp)
                    ) {
                        Text(
                            text = track?.title ?: "未知歌曲",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                            fontSize = 16.sp,
                            color = Color.White.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth()
                        )
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
                                // 封面页面
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
                                                .clip(RoundedCornerShape(16.dp)),
                                            contentScale = ContentScale.Fit,
                                            error = painterResource(android.R.drawable.ic_menu_gallery)
                                        )
                                    }
                                }
                            }
                            1 -> {
                                // 歌词页面
                                LyricsView(
                                    lyrics = lyricState.lyrics,
                                    currentPosition = playerState.currentPosition,
                                    onSeekTo = onSeekTo,
                                    modifier = Modifier.fillMaxSize()
                                )
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
                            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(72.dp)) {
                                Icon(
                                    imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                    contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                                    tint = Color.White,
                                    modifier = Modifier.size(40.dp)
                                )
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

                        // 第二行：歌词/喜欢/播放列表
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 歌词按钮（纯音乐时禁用）
                            IconButton(
                                onClick = { 
                                    if (!isPureMusic) {
                                        coroutineScope.launch {
                                            if (pagerState.currentPage == 0) {
                                                pagerState.animateScrollToPage(1)
                                            } else {
                                                pagerState.animateScrollToPage(0)
                                            }
                                        }
                                    }
                                },
                                enabled = !isPureMusic,
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Notes,
                                    contentDescription = "歌词",
                                    tint = if (isPureMusic) {
                                        Color.White.copy(alpha = 0.2f)
                                    } else if (pagerState.currentPage == 1) {
                                        Color.White
                                    } else {
                                        Color.White.copy(alpha = 0.6f)
                                    },
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            IconButton(onClick = onLikeTrack, modifier = Modifier.size(48.dp)) {
                                Icon(
                                    imageVector = if (isCurrentTrackLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                    contentDescription = if (isCurrentTrackLiked) "取消喜欢" else "喜欢",
                                    tint = if (isCurrentTrackLiked) Color.Red else Color.White.copy(alpha = 0.6f),
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
                    }
                }
            }

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
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(currentPlaylist) { track ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
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

private fun getQualityText(bitrate: Int): String {
    return when {
        bitrate <= 0 -> "未知品质"
        bitrate <= 320000 -> "低品质"
        bitrate <= 1411000 -> "SQ 标准品质"
        bitrate <= 2304000 -> "HQ 高品质"
        else -> "Hi-Res 无损音频"
    }
}
