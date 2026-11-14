package com.hypermusic.miuix.ui.pages

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

@Composable
internal fun VinylRecordCover(bitmap: ImageBitmap?, title: String?, maxDiscSize: Dp, rotation: Float, isPlaying: Boolean,
    trackKey: String?, imageReady: Boolean, swipeSongs: Boolean, onPrevious: () -> Unit, onNext: () -> Unit, onShowLyrics: (() -> Unit)?) {
    val armAngle = animateFloatAsState(if (isPlaying) 12f else -16f, tween(500), label = "vinyl_arm")
    val scope = rememberCoroutineScope()
    val offset = remember { Animatable(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    val currentTrack by rememberUpdatedState(trackKey)
    val currentImageReady by rememberUpdatedState(imageReady)
    val previous by rememberUpdatedState(onPrevious)
    val next by rememberUpdatedState(onNext)
    val screenWidth = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val reserved = 64.dp
        val discSize = minOf(maxDiscSize, maxWidth, (maxHeight - reserved).coerceAtLeast(0.dp))
        Box(Modifier.size(discSize, discSize + reserved)
            .then(if (swipeSongs && onShowLyrics != null) Modifier.clickable(enabled = !busy, onClick = onShowLyrics) else Modifier)
            .then(if (swipeSongs) Modifier.pointerInput(screenWidth) {
                detectHorizontalDragGestures(
                    onDragStart = { if (!busy) { dragging = true; dragOffset = offset.value } },
                    onHorizontalDrag = { change, amount ->
                        if (dragging && !busy) { change.consume(); dragOffset = (dragOffset + amount).coerceIn(-screenWidth, screenWidth) }
                    },
                    onDragCancel = {
                        if (dragging) {
                            val released = dragOffset
                            busy = true
                            scope.launch {
                                offset.snapTo(released)
                                dragging = false
                                offset.animateTo(0f, tween(250))
                                busy = false
                            }
                        }
                    },
                    onDragEnd = {
                        if (dragging) {
                            val released = dragOffset
                            busy = true
                            scope.launch {
                                offset.snapTo(released)
                                dragging = false
                                if (abs(released) < 60.dp.toPx()) {
                                    offset.animateTo(0f, tween(250))
                                } else {
                                    val oldTrack = currentTrack
                                    val direction = if (released < 0) -1f else 1f
                                    offset.animateTo(direction * screenWidth, tween(220))
                                    if (direction < 0) next() else previous()
                                    val changed = withTimeoutOrNull(2500) { snapshotFlow { currentTrack }.first { it != oldTrack } } != null
                                    if (changed) withTimeoutOrNull(2000) { snapshotFlow { currentImageReady }.first { it } }
                                    offset.snapTo(if (changed) -direction * screenWidth else direction * screenWidth)
                                    offset.animateTo(0f, tween(300))
                                }
                                busy = false
                            }
                        }
                    }
                )
            } else Modifier)) {
            Box(Modifier.fillMaxSize().graphicsLayer { translationX = if (dragging) dragOffset else offset.value }) {
            // 凹槽与封面一起旋转；光泽和唱针保持在唱盘坐标系内。
            Canvas(Modifier.offset(y = reserved).size(discSize).graphicsLayer { rotationZ = rotation }) {
                val radius = size.minDimension / 2f
                drawCircle(Color(0xff111214), radius)
                repeat(34) { index ->
                    val grooveRadius = radius * (0.70f + index * 0.0085f)
                    drawCircle(if (index % 2 == 0) Color(0xff252629) else Color(0xff191a1d),
                        radius = grooveRadius, style = Stroke(width = radius * 0.009f))
                }
                drawCircle(Color.Black.copy(alpha = .8f), radius, style = Stroke(width = radius * .012f))
            }
            Canvas(Modifier.offset(y = reserved).size(discSize)) {
                val radius = size.minDimension / 2f
                drawCircle(Brush.radialGradient(0f to Color.Transparent, .7f to Color.Transparent,
                    1f to Color.Black.copy(alpha = .48f), center = center, radius = radius), radius)
                rotate(30f) {
                    drawCircle(Brush.linearGradient(listOf(Color.White.copy(alpha = .10f), Color.Transparent,
                        Color.White.copy(alpha = .05f)), start = Offset(0f, center.y), end = Offset(size.width, center.y)), radius)
                }
            }
            Box(Modifier.offset(y = reserved).size(discSize), contentAlignment = Alignment.Center) {
                val imageModifier = Modifier.size(discSize * .68f).clip(CircleShape).graphicsLayer { rotationZ = rotation }
                if (bitmap != null) Image(bitmap, title, imageModifier, contentScale = ContentScale.Crop)
                else Image(com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter(), title, imageModifier, contentScale = ContentScale.Crop)
            }
            }
            Canvas(Modifier.fillMaxSize()) {
                val d = discSize.toPx()
                val pivot = Offset(size.width / 2f, 18.dp.toPx())
                val elbow = Offset(pivot.x + d * .17f, pivot.y + reserved.toPx() + d * .02f)
                val tip = Offset(pivot.x + d * .32f, pivot.y + reserved.toPx() + d * .075f)
                rotate(armAngle.value, pivot) {
                    val lineWidth = (d * .022f).coerceIn(5.dp.toPx(), 10.dp.toPx())
                    drawLine(Color(0xffeeeeee), pivot, elbow, lineWidth, cap = StrokeCap.Round)
                    drawLine(Color(0xffeeeeee), elbow, tip, lineWidth, cap = StrokeCap.Round)
                    drawCircle(Color(0xffeeeeee), lineWidth * 1.5f, tip)
                }
                drawCircle(Color.Black.copy(alpha = .22f), 18.dp.toPx(), pivot)
                drawCircle(Color(0xffeeeeee), 11.dp.toPx(), pivot)
                drawCircle(Color(0xffb5b5b5), 4.dp.toPx(), pivot)
            }
        }
    }
}
