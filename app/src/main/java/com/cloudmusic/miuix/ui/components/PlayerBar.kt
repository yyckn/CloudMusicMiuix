package com.cloudmusic.miuix.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.viewmodel.PlayerState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.ListView
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play

@Composable
fun PlayerBar(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlayerBarClick: () -> Unit = {},
    onSwipeUp: () -> Unit = {},
    onPlaylistClick: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    hasBottomBar: Boolean = true,
    isFloatingStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (playerState.currentTrack != null) {
        val navBarBottomPadding = if (!hasBottomBar) {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        } else {
            0.dp
        }

        Column(modifier = modifier.fillMaxWidth()) {
            with(sharedTransitionScope) {
                Surface(
                    onClick = { onPlayerBarClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFloatingStyle) {
                                Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp)
                            } else {
                                Modifier
                            }
                        )
                        .sharedElement(
                            sharedContentState = rememberSharedContentState(key = "player_bar"),
                            animatedVisibilityScope = animatedVisibilityScope
                        )
                        .pointerInput(Unit) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    if (dragAmount < -10) {
                                        change.consume()
                                        onSwipeUp()
                                    }
                                }
                            )
                        },
                    shape = if (isFloatingStyle) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp),
                    color = MiuixTheme.colorScheme.surfaceVariant
                ) {
                    Column {
                        // 顶部进度条
                        val progress = if (playerState.duration > 0) {
                            playerState.currentPosition.toFloat() / playerState.duration.toFloat()
                        } else {
                            0f
                        }

                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp),
                            colors = ProgressIndicatorDefaults.progressIndicatorColors(
                                foregroundColor = MiuixTheme.colorScheme.primary,
                                backgroundColor = MiuixTheme.colorScheme.surfaceVariant
                            ),
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 封面图片
                            AsyncImage(
                                model = playerState.currentTrack?.imgUrl,
                                contentDescription = playerState.currentTrack?.title,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop,
                                error = painterResource(android.R.drawable.ic_menu_gallery)
                            )

                            Spacer(modifier = Modifier.width(12.dp))

                            // 歌曲信息
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = playerState.currentTrack?.title ?: "",
                                    style = MiuixTheme.textStyles.main,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = playerState.currentTrack?.artist ?: "",
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // 播放/暂停按钮
                            if (playerState.isLoading) {
                                InfiniteProgressIndicator(
                                    modifier = Modifier.size(36.dp)
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onTogglePlayPause() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (playerState.isPlaying) MiuixIcons.Pause else MiuixIcons.Play,
                                        contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                        tint = MiuixTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }

                            // 列表按钮
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { onPlaylistClick() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.ListView,
                                    contentDescription = "播放列表",
                                    tint = MiuixTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 底部导航栏区域的背景蒙版
            if (!isFloatingStyle && navBarBottomPadding > 0.dp) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(navBarBottomPadding)
                        .background(MiuixTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}
