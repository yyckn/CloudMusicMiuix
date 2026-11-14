package com.hypermusic.miuix.ui.effects

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Vertices
import kotlin.math.exp
import kotlin.math.sqrt

/** ARC4 seed stream compatible with the numeric seed used by the reference preset. */
internal class FluidSeed(seed: Int) {
    private val permutation = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        val text = "$seed\u0000"
        val key = IntArray(text.length)
        var smear = 0
        text.forEachIndexed { index, char ->
            smear = smear xor (key[index] * 19)
            key[index] = (smear + char.code) and 255
        }
        var swapIndex = 0
        permutation.indices.forEach { index ->
            swapIndex = (swapIndex + permutation[index] + key[index % key.size]) and 255
            val old = permutation[index]
            permutation[index] = permutation[swapIndex]
            permutation[swapIndex] = old
        }
        repeat(256) { byte() }
    }

    private fun byte(): Int {
        i = (i + 1) and 255
        j = (j + permutation[i]) and 255
        val old = permutation[i]
        permutation[i] = permutation[j]
        permutation[j] = old
        return permutation[(permutation[i] + permutation[j]) and 255]
    }

    fun next(): Double {
        var value = 0.0
        repeat(6) { value = value * 256.0 + byte() }
        var divisor = 281474976710656.0
        var extra = 0
        while (value < 4503599627370496.0) {
            value = (value + extra) * 256.0
            divisor *= 256.0
            extra = byte()
        }
        while (value >= 9007199254740992.0) {
            value /= 2.0
            divisor /= 2.0
            extra = extra ushr 1
        }
        return (value + extra) / divisor
    }
}

/** The reference's two static control-point deformations, scaled to a card-sized surface. */
internal fun fluidPresetMesh(size: Size): Vertices {
    val count = 100
    val span = 1000f
    val x = FloatArray(count * count)
    val y = FloatArray(x.size)
    val z = FloatArray(x.size)
    for (row in 0 until count) for (column in 0 until count) {
        val index = row * count + column
        x[index] = (column / 99f - 0.5f) * span
        y[index] = (0.5f - row / 99f) * span
    }
    val controls = listOf(floatArrayOf(240f, -200f, 260f, 160f), floatArrayOf(-240f, 200f, -260f, -160f))
    controls.forEach { control ->
        for (row in 1 until count - 1) for (column in 1 until count - 1) {
            val index = row * count + column
            val dx = x[index] - control[0]
            val dy = y[index] - control[1]
            val distance = sqrt(dx * dx + dy * dy + z[index] * z[index])
            if (distance >= 900f) continue
            val edgeX = x[column]
            val dxEdge = x[index] - edgeX
            val topY = y[index] - span / 2f
            val bottomY = y[index] + span / 2f
            val topDistance = sqrt(dxEdge * dxEdge + topY * topY + z[index] * z[index])
            val bottomDistance = sqrt(dxEdge * dxEdge + bottomY * bottomY + z[index] * z[index])
            val normalized = distance / 900f
            val weight = exp(-normalized * normalized / 0.125f) / (0.25f * sqrt(6.28f) * 1.6f) -
                5f / minOf(topDistance, bottomDistance).coerceAtLeast(0.001f)
            if (weight > 0.001f) {
                x[index] += (control[2] - control[0]) * weight
                y[index] += (control[3] - control[1]) * weight
                z[index] += 400f * weight
            }
        }
    }
    val square = maxOf(size.width, size.height)
    val positions = x.indices.map { index ->
        Offset(size.width / 2f + x[index] * square / span, size.height / 2f - y[index] * square / span)
    }
    val textureCoordinates = x.indices.map { index ->
        Offset(index % count / 99f * square, (1f - index / count / 99f) * square)
    }
    val indices = ArrayList<Int>(99 * 99 * 6)
    for (row in 0 until count - 1) for (column in 0 until count - 1) {
        val a = row * count + column
        indices.addAll(listOf(a, a + count, a + 1, a + 1, a + count, a + count + 1))
    }
    return Vertices(VertexMode.Triangles, positions, textureCoordinates = textureCoordinates,
        colors = List(positions.size) { Color.White }, indices = indices)
}
