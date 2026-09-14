package com.cloudmusic.miuix.ui.components

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * 动态封面播放器
 * ExoPlayer由外部管理，此组件只负责显示
 * 注意：此组件应仅在首帧就绪后才渲染，避免黑屏闪烁
 */
@Composable
fun AnimatedCoverPlayer(
    exoPlayer: ExoPlayer?,
    modifier: Modifier = Modifier,
    onFirstFrameReady: () -> Unit = {},
    onError: () -> Unit = {}
) {
    // 监听播放器状态
    DisposableEffect(exoPlayer) {
        if (exoPlayer == null) return@DisposableEffect onDispose {}
        
        // 如果已经在播放，直接回调
        if (exoPlayer.playbackState == androidx.media3.common.Player.STATE_READY) {
            onFirstFrameReady()
        }
        
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == androidx.media3.common.Player.STATE_READY) {
                    onFirstFrameReady()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                Log.e("AnimatedCoverPlayer", "播放错误: ${error.errorCodeName}", error)
                onError()
            }
        }
        
        exoPlayer.addListener(listener)
        
        onDispose {
            exoPlayer.removeListener(listener)
        }
    }

    if (exoPlayer != null) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                    useArtwork = false
                }
            },
            modifier = modifier.fillMaxSize(),
            update = { playerView ->
                playerView.player = exoPlayer
            }
        )
    }
}
