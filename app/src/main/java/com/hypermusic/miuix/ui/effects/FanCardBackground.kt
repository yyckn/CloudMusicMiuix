package com.hypermusic.miuix.ui.effects

import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Fixed fan geometry with two-color gradients travelling outward on each sector. */
@Composable
internal fun FanCardBackground(modifier: Modifier = Modifier, active: Boolean = true) {
    var visible by remember { mutableStateOf(false) }
    var time by remember { mutableFloatStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, active, visible) {
        if (!active || !visible) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrame = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (previousFrame == 0L) previousFrame = now
                    val delta = now - previousFrame
                    if (delta >= 33_333_333L) {
                        time = (time + (delta / 1_000_000_000f).coerceAtMost(0.1f)) % 16f
                        previousFrame = now
                    }
                }
            }
        }
    }
    Spacer(modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }
        .drawWithCache {
            val origin = Offset(size.width, size.height / 2f)
            // Clockwise around the opposite edge: broad purple center, narrow blue/green rays.
            val edge = listOf(
                Offset(size.width, 0f), Offset(size.width * 0.84f, 0f),
                Offset(size.width * 0.62f, 0f), Offset(size.width * 0.30f, 0f), Offset.Zero,
                Offset(0f, size.height), Offset(size.width * 0.30f, size.height),
                Offset(size.width * 0.62f, size.height), Offset(size.width * 0.84f, size.height), size.let { Offset(it.width, it.height) }
            )
            val palette = listOf(
                Color(0xFF318E85) to Color(0xFF60B8A9),
                Color(0xFF3378A7) to Color(0xFF49AAA3),
                Color(0xFF4237A8) to Color(0xFF527AD1),
                Color(0xFF5E339F) to Color(0xFF4B67BF),
                Color(0xFF3B217F) to Color(0xFF7642AC),
                Color(0xFF49309C) to Color(0xFF6354C0),
                Color(0xFF34378F) to Color(0xFF497CBD),
                Color(0xFF34539A) to Color(0xFF409C9C),
                Color(0xFF3C658E) to Color(0xFF59AE9E)
            )
            val sectors = edge.zipWithNext().map { (a, b) ->
                val path = Path().apply {
                    moveTo(origin.x, origin.y)
                    lineTo(a.x, a.y)
                    lineTo(b.x, b.y)
                    close()
                }
                val vector = (a + b) / 2f - origin
                path to vector / vector.getDistance().coerceAtLeast(1f)
            }
            val period = size.maxDimension.coerceAtLeast(1f) * 0.95f
            onDrawBehind {
                drawRect(Color(0xFF47258D))
                sectors.forEachIndexed { index, (path, direction) ->
                    val (first, second) = palette[index]
                    val phase = time / 16f + index * 0.055f
                    val start = origin + direction * (phase * period)
                    // A-B-A has identical endpoints: translating by one period is seamless.
                    drawPath(path, Brush.linearGradient(
                        colors = listOf(first, second, first),
                        start = start, end = start + direction * period,
                        tileMode = TileMode.Repeated
                    ))
                }
            }
        })
}
