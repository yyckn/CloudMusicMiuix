package com.cloudmusic.miuix.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
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
import top.yukonga.miuix.kmp.basic.IconButton
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
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    hasBottomBar: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (playerState.currentTrack != null) {
        with(sharedTransitionScope) {
            Surface(
                modifier = modifier
                    .fillMaxWidth()
                    .sharedElement(
                        sharedContentState = rememberSharedContentState(key = "player_bar"),
                        animatedVisibilityScope = animatedVisibilityScope
                    ),
                color = MiuixTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp,
                            end = 12.dp,
                            top = 8.dp,
                            bottom = if (hasBottomBar) 8.dp else 32.dp
                        ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
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

                    androidx.compose.foundation.layout.Column(
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

                    if (playerState.isLoading) {
                        InfiniteProgressIndicator(
                            modifier = Modifier.size(36.dp)
                        )
                    } else {
                        IconButton(
                            onClick = onTogglePlayPause,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    MiuixTheme.colorScheme.primary,
                                    CircleShape
                                )
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
