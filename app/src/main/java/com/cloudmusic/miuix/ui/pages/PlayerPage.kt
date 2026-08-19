package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.viewmodel.PlayerState
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
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Location
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

@Composable
fun PlayerPage(
    playerState: PlayerState,
    currentPlaylist: List<Track> = emptyList(),
    onBackClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDownload: () -> Unit = {},
    onPlayTrack: (Track) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    val track = playerState.currentTrack
    var showMoreMenu by remember { mutableStateOf(false) }
    var showPlaylistSheet by remember { mutableStateOf(false) }
    val playlistListState = rememberLazyListState()

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

    BackHandler {
        onBackClick()
    }

    Scaffold(
        topBar = {
            // 简单的TopBar，不使用大标题
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MiuixTheme.colorScheme.surface)
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
                            tint = MiuixTheme.colorScheme.onSurface
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
                                tint = MiuixTheme.colorScheme.onSurface
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
    ) { paddingValues ->
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val screenWidth = configuration.screenWidthDp.dp
        val topPadding = paddingValues.calculateTopPadding()
        // 预留底部控件高度：Slider区域 + 按钮区域 + 底部间距
        val bottomReserved = 186.dp
        // 标题区域高度（歌曲名称 + 歌手专辑 + 间距）
        val titleAreaHeight = 80.dp
        // 封面可用高度 = 屏幕高度 - 顶部padding - 底部预留 - 标题区域高度 - 额外间距
        val availableHeight = screenHeight - topPadding - bottomReserved - titleAreaHeight - 32.dp
        val imageSize = minOf(availableHeight, screenWidth - 24.dp).coerceAtLeast(0.dp)

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(top = topPadding)
                    .fillMaxSize()
                    .padding(horizontal = 24.dp)
            ) {
                // 歌曲标题和信息（紧贴TopAppBar）
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 16.dp)
                ) {
                    // 歌曲名称
                    Text(
                        text = track?.title ?: "未知歌曲",
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // 歌手 - 专辑
                    Text(
                        text = "${track?.artist ?: "未知歌手"} - ${track?.album ?: "未知专辑"}",
                        fontSize = 16.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 封面图片（居中显示）
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AsyncImage(
                        model = track?.imgUrl,
                        contentDescription = track?.title,
                        modifier = Modifier
                            .size(imageSize)
                            .clip(RoundedCornerShape(16.dp)),
                        contentScale = ContentScale.Crop,
                        error = painterResource(android.R.drawable.ic_menu_gallery)
                    )
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
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = getQualityText(playerState.bitrate),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.primary
                    )
                    Text(
                        text = formatDuration(playerState.duration),
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                // 控制器区域
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 48.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 歌词按钮
                    IconButton(
                        onClick = { /* 暂不实现 */ },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Notes,
                            contentDescription = "歌词",
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // 上一首按钮
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ChevronBackward,
                            contentDescription = "上一首",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 播放/暂停按钮
                    IconButton(
                        onClick = onTogglePlayPause,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                            contentDescription = if (playerState.isPlaying) "暂停" else "播放",
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    // 下一首按钮
                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ChevronForward,
                            contentDescription = "下一首",
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    // 播放列表按钮
                    IconButton(
                        onClick = { showPlaylistSheet = true },
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(
                            imageVector = MiuixIcons.ListView,
                            contentDescription = "播放列表",
                            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            modifier = Modifier.size(24.dp)
                        )
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
