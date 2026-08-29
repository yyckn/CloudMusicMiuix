package com.cloudmusic.miuix.ui.components

import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalConfiguration
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
    enableSwipeToDismiss: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }
    // 初始值为屏幕高度，避免组件显示在屏幕中间
    val offsetY = remember { Animatable(screenHeight) }

    // 处理系统返回手势 - 优先级高于其他BackHandler
    BackHandler(enabled = isVisible) {
        scope.launch {
            offsetY.animateTo(screenHeight, tween(300))
            onDismiss()
        }
    }

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

    // 仅在可见或关闭动画进行中时加入布局树
    if (isVisible || offsetY.value < screenHeight) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = 1f - (offsetY.value / screenHeight).coerceIn(0f, 1f)
                }
                .pointerInput(isVisible, enableSwipeToDismiss) {
                    if (isVisible && enableSwipeToDismiss) {
                        detectVerticalDragGestures(
                            onDragEnd = {
                                scope.launch {
                                    if (offsetY.value > screenHeight * 0.15f) {
                                        // 滑动超过15%，关闭
                                        offsetY.animateTo(screenHeight, tween(300))
                                        onDismiss()
                                    } else {
                                        // 滑动不足15%，弹回
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
}
