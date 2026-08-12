package com.cloudmusic.miuix.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.cloudmusic.miuix.viewmodel.PlayerState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Play

@Composable
fun PlayerBar(
    playerState: PlayerState,
    onTogglePlayPause: () -> Unit,
    onPlayerBarClick: () -> Unit = {},
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    hasBottomBar: Boolean = true,
    isFloatingStyle: Boolean = false,
    modifier: Modifier = Modifier
) {
    if (playerState.currentTrack != null) {
        with(sharedTransitionScope) {
            Surface(
                onClick = { onPlayerBarClick() },
                modifier = modifier
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
                    ),
                shape = if (isFloatingStyle) RoundedCornerShape(16.dp) else RoundedCornerShape(0.dp),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
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
                                .background(
                                    MiuixTheme.colorScheme.primary,
                                    CircleShape
                                )
                                .clickable { onTogglePlayPause() },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playerState.isPlaying) MiuixIcons.Medium.Pause else MiuixIcons.Medium.Play,
                                contentDescription = if (playerState.isPlaying) "Pause" else "Play",
                                tint = MiuixTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
