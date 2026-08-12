package com.cloudmusic.miuix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * 可滑动的播放器底部弹出层
 * 支持上滑打开、下滑关闭，完全跟手
 */
@Composable
fun SwipeablePlayerSheet(
    isVisible: Boolean,
    onShow: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    val screenHeight = with(density) { 800.dp.toPx() }

    // 监听isVisible变化，执行动画
    LaunchedEffect(isVisible) {
        if (isVisible) {
            // 从屏幕下方滑入
            offsetY.snapTo(screenHeight)
            offsetY.animateTo(0f, tween(300))
        } else {
            // 滑出到屏幕下方
            offsetY.animateTo(screenHeight, tween(300))
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = offsetY.value
                alpha = 1f - (offsetY.value / screenHeight).coerceIn(0f, 1f)
            }
            .pointerInput(isVisible) {
                if (isVisible) {
                    detectVerticalDragGestures(
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value > screenHeight * 0.3f) {
                                    // 滑动超过30%，关闭
                                    offsetY.animateTo(screenHeight, tween(300))
                                    onDismiss()
                                } else {
                                    // 滑动不足30%，弹回
                                    offsetY.animateTo(0f, tween(300))
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                offsetY.animateTo(0f, tween(300))
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            scope.launch {
                                val newOffset = (offsetY.value + dragAmount).coerceIn(0f, screenHeight)
                                offsetY.snapTo(newOffset)
                            }
                        }
                    )
                }
            }
    ) {
        content()
    }
}
