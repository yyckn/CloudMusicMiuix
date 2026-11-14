package com.hypermusic.miuix.ui.effects

import android.graphics.RuntimeShader
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Continuous curved gradient ribbons, all within a rose/pink palette. */
@Composable
internal fun CurveCardBackground(modifier: Modifier = Modifier, active: Boolean = true) {
    val shader = remember { runCatching { RuntimeShader(CURVE_SHADER) }.getOrNull() }
    val brush = remember(shader) {
        shader?.let { ShaderBrush(it) } ?: Brush.linearGradient(
            listOf(Color(0xFFEE6693), Color(0xFFFA9EB9), Color(0xFFD94984))
        )
    }
    var time by remember { mutableFloatStateOf(0f) }
    var visible by remember { mutableStateOf(false) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, active, visible, shader) {
        if (!active || !visible || shader == null) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previousFrame = 0L
            while (isActive) {
                withFrameNanos { now ->
                    if (previousFrame == 0L) previousFrame = now
                    val delta = now - previousFrame
                    if (delta >= 33_333_333L) {
                        time = (time + (delta / 1_000_000_000f).coerceAtMost(0.1f) * 2f) % 32f
                        previousFrame = now
                    }
                }
            }
        }
    }
    Canvas(modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }) {
        shader?.setFloatUniform("resolution", size.width, size.height)
        shader?.setFloatUniform("time", time)
        drawRect(brush)
    }
}

private const val CURVE_SHADER = """
uniform float2 resolution;
uniform float time;

half4 main(float2 coord) {
    float2 p = (coord - 0.5 * resolution) / max(min(resolution.x, resolution.y), 1.0);
    float phase = time * 0.1963495408;
    // Low-frequency curves bend together; there is no turbulent noise or scattered blobs.
    float curve = p.x * 0.72 + p.y * 0.88;
    curve += 0.25 * sin(p.y * 3.2 + phase);
    curve += 0.15 * sin(p.x * 3.8 - phase);
    curve += 0.06 * sin((p.x + p.y) * 4.6 + phase * 2.0);
    float ribbon = 0.5 + 0.5 * sin(curve * 7.0 - phase);
    float broad = 0.5 + 0.5 * sin(curve * 3.5 + phase);
    float3 rose = float3(238.0, 102.0, 147.0) / 255.0;
    float3 blush = float3(250.0, 158.0, 185.0) / 255.0;
    float3 deepPink = float3(217.0, 73.0, 132.0) / 255.0;
    float3 palePink = float3(248.0, 182.0, 201.0) / 255.0;
    float3 color = mix(deepPink, rose, smoothstep(0.0, 0.65, broad));
    color = mix(color, blush, smoothstep(0.10, 0.90, ribbon) * 0.85);
    color = mix(color, palePink, smoothstep(0.80, 1.0, ribbon) * 0.45);
    return half4(half3(color), 1.0);
}
"""
