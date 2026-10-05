package com.musichub.miuix.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun DraggablePlayerSheet(
    isVisible: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val offsetY = remember { Animatable(0f) }
    var dragProgress by remember { mutableFloatStateOf(if (isVisible) 0f else 1f) }
    val screenHeight = with(density) { 800.dp.toPx() } // 默认高度

    if (isVisible || offsetY.value > 0f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = offsetY.value
                    alpha = 1f - (offsetY.value / screenHeight).coerceIn(0f, 1f)
                }
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { },
                        onDragEnd = {
                            scope.launch {
                                if (offsetY.value > screenHeight * 0.3f) {
                                    // 滑动超过30%，关闭
                                    offsetY.animateTo(screenHeight, tween(300))
                                    onDismiss()
                                    offsetY.snapTo(0f)
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
        ) {
            content()
        }
    }
}
