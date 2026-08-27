package com.cloudmusic.miuix.ui.pages

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.data.model.AlbumInfo
import com.cloudmusic.miuix.data.model.Track
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.PressFeedbackType
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Play
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.clickable

@Composable
fun AlbumPage(
    albumId: Long,
    onBackClick: () -> Unit,
    onTrackClick: (Track, List<Track>) -> Unit,
    onPlayAllClick: (List<Track>) -> Unit,
    currentTrack: Track? = null,
    isBackHandlerEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()
    var albumInfo by remember { mutableStateOf<AlbumInfo?>(null) }
    var albumSongs by remember { mutableStateOf<List<Track>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    BackHandler(enabled = isBackHandlerEnabled) {
        onBackClick()
    }

    LaunchedEffect(albumId) {
        isLoading = true
        NeteaseApi.getAlbumDetail(albumId.toString()).fold(
            onSuccess = { (info, songs) ->
                albumInfo = info
                albumSongs = songs
            },
            onFailure = { /* Handle error */ }
        )
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = albumInfo?.name ?: "专辑详情",
                largeTitle = "专辑详情",
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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 16.dp),
            overscrollEffect = null
        ) {
            // 专辑头部信息
            item {
                AlbumHeader(
                    albumInfo = albumInfo,
                    trackCount = albumSongs.size,
                    onPlayAllClick = { onPlayAllClick(albumSongs) }
                )
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
            } else if (albumSongs.isEmpty()) {
                item {
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
                }
            } else {
                itemsIndexed(albumSongs) { index, track ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        ),
                        pressFeedbackType = PressFeedbackType.Tilt,
                        showIndication = true,
                        onClick = { onTrackClick(track, albumSongs) }
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
                            },
                            endActions = {
                                if (track.isVip) {
                                    Text(
                                        text = "VIP",
                                        fontSize = MiuixTheme.textStyles.footnote2.fontSize,
                                        color = MiuixTheme.colorScheme.onPrimary,
                                        modifier = Modifier
                                            .background(
                                                color = MiuixTheme.colorScheme.primary,
                                                shape = RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                }
                                Text(
                                    text = formatDuration(track.duration),
                                    fontSize = 12.sp,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumHeader(
    albumInfo: AlbumInfo?,
    trackCount: Int,
    onPlayAllClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            AsyncImage(
                model = albumInfo?.picUrl,
                contentDescription = albumInfo?.name,
                modifier = Modifier
                    .size(140.dp)
                    .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
                error = painterResource(android.R.drawable.ic_menu_gallery)
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = albumInfo?.name ?: "",
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (!albumInfo?.artist.isNullOrEmpty()) {
                    Text(
                        text = "歌手：${albumInfo?.artist}",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (albumInfo != null && albumInfo.publishTime > 0) {
                    Text(
                        text = "发行时间：${formatPublishTime(albumInfo.publishTime)}",
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                }

                Text(
                    text = "$trackCount 首歌曲",
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }

        Button(
            onClick = onPlayAllClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = ButtonDefaults.buttonColorsPrimary()
        ) {
            Icon(
                imageVector = MiuixIcons.Play,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(text = "播放全部")
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

private fun formatPublishTime(timestamp: Long): String {
    if (timestamp <= 0) return ""
    val date = java.util.Date(timestamp)
    val format = java.text.SimpleDateFormat("yyyy年MM月dd日", java.util.Locale.CHINA)
    return format.format(date)
}
