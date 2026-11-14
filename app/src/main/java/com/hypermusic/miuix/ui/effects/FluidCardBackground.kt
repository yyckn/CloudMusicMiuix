package com.hypermusic.miuix.ui.effects

import android.graphics.RuntimeShader
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
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive

/** Flowing gradient surface with a closely spaced orange/yellow palette. */
@Composable
internal fun FluidCardBackground(modifier: Modifier = Modifier, active: Boolean = true) {
    val shader = remember {
        runCatching {
            RuntimeShader(FLUID_SHADER).apply {
                val random = FluidSeed(1000)
                val dyes = FloatArray(24)
                repeat(6) { index ->
                    dyes[index * 4] = random.next().toFloat()
                    dyes[index * 4 + 1] = random.next().toFloat()
                    dyes[index * 4 + 2] = 0.1f + random.next().toFloat() * 0.1f
                    dyes[index * 4 + 3] = 0.3f + random.next().toFloat() * 0.3f
                }
                setFloatUniform("dyes", dyes)
            }
        }.getOrNull()
    }
    val brush = remember {
        Brush.linearGradient(
            listOf(Color(0xFFF39A32), Color(0xFFF5B740), Color(0xFFF6D05A), Color(0xFFEF633D), Color(0xFFF8C975), Color(0xFFF39A32))
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
                        time += (delta / 1_000_000_000f).coerceAtMost(0.1f)
                        previousFrame = now
                    }
                }
            }
        }
    }
    Spacer(modifier.onGloballyPositioned { visible = !it.boundsInWindow().isEmpty }.drawWithCache {
        // Geometry is static: construct once per size, not once per animation frame.
        val mesh = fluidPresetMesh(size)
        val paint = Paint().apply { this.shader = shader }
        val square = maxOf(size.width, size.height).coerceAtLeast(1f)
        onDrawBehind {
            if (shader == null) drawRect(brush)
            else {
                shader.setFloatUniform("resolution", square, square)
                shader.setFloatUniform("time", time)
                drawContext.canvas.drawVertices(mesh, BlendMode.Modulate, paint)
            }
        }
    })
}

private const val FLUID_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float4 dyes[6];

float coverage(float2 coordinate, float4 dye) {
    return 1.0 - smoothstep(dye.z, dye.w, distance(coordinate, dye.xy));
}

half4 main(float2 fragCoord) {
    // fragCoord is the interpolated texture coordinate of the deformed mesh.
    float2 uv = fragCoord / max(resolution, float2(1.0));
    float t = time * 2.4;
    // The vivid preset deforms an existing smooth color surface, not a turbulent field.
    // A 0.15 displacement matches the preset's default Blur Scale.
    float2 warped = uv + 0.15 * float2(sin(t + uv.y * 15.0), cos(t + uv.x * 15.0));
    warped = clamp(warped, 0.0, 1.0);
    float3 orange = float3(243.0, 154.0, 50.0) / 255.0;
    float3 gold = float3(245.0, 183.0, 64.0) / 255.0;
    float3 yellow = float3(246.0, 208.0, 90.0) / 255.0;
    float3 orangeRed = float3(239.0, 99.0, 61.0) / 255.0;
    float3 apricot = float3(248.0, 201.0, 117.0) / 255.0;
    float3 color = mix(orange, gold, warped.x);
    color = mix(color, yellow, clamp(warped.x * warped.x - 0.04, 0.0, 1.0));
    color = mix(color, orange, coverage(warped, dyes[0]));
    color = mix(color, gold, coverage(warped, dyes[1]));
    color = mix(color, yellow, coverage(warped, dyes[2]));
    color = mix(color, orangeRed, coverage(warped, dyes[3]));
    color = mix(color, apricot, coverage(warped, dyes[4]));
    color = mix(color, orange, coverage(warped, dyes[5]));
    return half4(half3(color), 1.0);
}
"""
