package com.musichub.miuix.ui.components

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.musichub.miuix.viewmodel.PlayerState
import com.musichub.miuix.ui.effects.liquid.lens
import com.musichub.miuix.ui.effects.liquid.vibrancy
import com.musichub.miuix.ui.theme.isInDarkTheme
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.ProgressIndicatorDefaults
import top.yukonga.miuix.kmp.basic.Surface
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.textureBlur
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
    hidePlaylistButton: Boolean = false,
    blurBackdrop: LayerBackdrop? = null,
    liquidBackdrop: Backdrop? = null,
    floatingProgress: Float = if (isFloatingStyle) 1f else 0f,
    floatingBottomMargin: Dp = 12.dp,
    modifier: Modifier = Modifier
) {
    if (playerState.currentTrack != null) {
        val navBarBottomPadding = if (!hasBottomBar) {
            WindowInsets.navigationBars.only(WindowInsetsSides.Bottom).asPaddingValues().calculateBottomPadding()
        } else {
            0.dp
        }

        val floatingFraction = floatingProgress.coerceIn(0f, 1f)
        var barHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        // 未测量前按悬浮样式的最终高度估算，避免首帧圆角为 0
        val measuredHeight = if (barHeightPx > 0) with(density) { barHeightPx.toDp() } else 66.dp
        val surfaceShape = RoundedCornerShape((measuredHeight / 2) * floatingFraction)
        val coverSize = lerp(48.dp, 44.dp, floatingFraction)
        val coverCorner = lerp(8.dp, 10.dp, floatingFraction)
        val liquidContainerColor = MiuixTheme.colorScheme.surfaceContainer.copy(0.4f)
        val liquidModifier = if (liquidBackdrop != null) {
            Modifier.drawBackdrop(
                backdrop = liquidBackdrop,
                shape = { surfaceShape },
                effects = {
                    padding = maxOf(padding, 40.dp.toPx())
                    vibrancy()
                    blur(4.dp.toPx(), 4.dp.toPx())
                    lens(
                        refractionHeight = 24.dp.toPx(),
                        refractionAmount = 24.dp.toPx(),
                    )
                },
                onDrawSurface = { drawRect(liquidContainerColor) },
            )
        } else {
            Modifier
        }
        val blurModifier = if (blurBackdrop != null) {
            Modifier.textureBlur(
                backdrop = blurBackdrop,
                shape = surfaceShape,
                blurRadius = 25f,
                colors = BlurColors(
                    blendColors = listOf(
                        BlendColorEntry(color = MiuixTheme.colorScheme.surfaceVariant.copy(0.87f)),
                    ),
                ),
            )
        } else {
            Modifier
        }
        val liquidShadowModifier = if (liquidBackdrop != null) {
            Modifier.dropShadow(
                shape = surfaceShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isInDarkTheme()) 0.2f else 0.1f,
                ),
            )
        } else {
            Modifier
        }
        val backgroundModifier = if (liquidBackdrop != null) {
            liquidShadowModifier.then(liquidModifier)
        } else {
            blurModifier
        }

        Column(modifier = modifier.fillMaxWidth()) {
            with(sharedTransitionScope) {
                Surface(
                    onClick = { onPlayerBarClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            start = 12.dp * floatingFraction,
                            end = 12.dp * floatingFraction,
                            bottom = floatingBottomMargin * floatingFraction
                        )
                        .onSizeChanged { barHeightPx = it.height }
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
                        }
                        .then(backgroundModifier),
                    shape = surfaceShape,
                    color = if (liquidBackdrop != null || blurBackdrop != null) {
                        Color.Transparent
                    } else {
                        MiuixTheme.colorScheme.surfaceVariant
                    }
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
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 封面图片
                            AsyncImage(
                                model = playerState.currentTrack?.imgUrl,
                                contentDescription = playerState.currentTrack?.title,
                                modifier = Modifier
                                    .size(coverSize)
                                    .clip(RoundedCornerShape(coverCorner)),
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
                            if (!hidePlaylistButton) {
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
