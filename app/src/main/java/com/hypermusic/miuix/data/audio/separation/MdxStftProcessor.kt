package com.hypermusic.miuix.data.audio.separation

import kotlin.math.PI
import kotlin.math.cos
import org.jtransforms.fft.DoubleFFT_1D

/**
 * UVR MDX-Net 的 STFT / iSTFT 实现：
 * - 周期 Hann 窗、center=true、[L实, L虚, R实, R虚] 平面顺序；
 * - 输入频谱裁剪到 dimF，最低 3 个频点为 0；
 * - 逆变换按窗平方归一化后重叠相加。
 */
class MdxStftProcessor(
    val nFft: Int,
    val hopLength: Int,
    val dimF: Int,
    val dimT: Int = 256,
) {
    val chunkSize: Int = hopLength * (dimT - 1)
    val trim: Int = nFft / 2
    val genSize: Int = chunkSize - 2 * trim

    private val fft = DoubleFFT_1D(nFft.toLong())
    private val window = DoubleArray(nFft) { n ->
        0.5 * (1.0 - cos(2.0 * PI * n.toDouble() / nFft.toDouble()))
    }
    private val bufL = DoubleArray(nFft)
    private val bufR = DoubleArray(nFft)
    private val paddedLen = chunkSize + nFft
    private val normWeights = DoubleArray(paddedLen).also { norm ->
        for (t in 0 until dimT) {
            val base = t * hopLength
            for (n in 0 until nFft) {
                val w = window[n]
                norm[base + n] += w * w
            }
        }
    }
    private val accumL = DoubleArray(paddedLen)
    private val accumR = DoubleArray(paddedLen)

    fun forwardStereo(
        leftChunk: FloatArray,
        rightChunk: FloatArray,
        dstTensor: FloatArray,
        zeroLowBins: Boolean = true,
    ) {
        val planeStride = dimF * dimT
        val p0 = 0
        val p1 = planeStride
        val p2 = 2 * planeStride
        val p3 = 3 * planeStride

        for (t in 0 until dimT) {
            val start = t * hopLength - trim
            for (n in 0 until nFft) {
                val w = window[n]
                val rawPos = start + n
                val pos = when {
                    rawPos < 0 -> (-rawPos).coerceIn(0, chunkSize - 1)
                    rawPos >= chunkSize -> (2 * (chunkSize - 1) - rawPos).coerceIn(0, chunkSize - 1)
                    else -> rawPos
                }
                bufL[n] = leftChunk[pos].toDouble() * w
                bufR[n] = rightChunk[pos].toDouble() * w
            }
            fft.realForward(bufL)
            fft.realForward(bufR)

            for (k in 0 until dimF) {
                val idx = k * dimT + t
                if (zeroLowBins && k < 3) {
                    dstTensor[p0 + idx] = 0f
                    dstTensor[p1 + idx] = 0f
                    dstTensor[p2 + idx] = 0f
                    dstTensor[p3 + idx] = 0f
                } else {
                    dstTensor[p0 + idx] = bufL[k * 2].toFloat()
                    dstTensor[p1 + idx] = if (k == 0) 0f else bufL[k * 2 + 1].toFloat()
                    dstTensor[p2 + idx] = bufR[k * 2].toFloat()
                    dstTensor[p3 + idx] = if (k == 0) 0f else bufR[k * 2 + 1].toFloat()
                }
            }
        }
    }

    fun inverseStereo(srcTensor: FloatArray, leftOut: FloatArray, rightOut: FloatArray) {
        val planeStride = dimF * dimT
        val p0 = 0
        val p1 = planeStride
        val p2 = 2 * planeStride
        val p3 = 3 * planeStride

        accumL.fill(0.0)
        accumR.fill(0.0)

        for (t in 0 until dimT) {
            // JTransforms packs DC and Nyquist in slots 0 and 1. MDX omits Nyquist.
            bufL[0] = srcTensor[p0 + t].toDouble()
            bufR[0] = srcTensor[p2 + t].toDouble()
            bufL[1] = 0.0
            bufR[1] = 0.0
            bufL.fill(0.0, 2 * dimF)
            bufR.fill(0.0, 2 * dimF)
            for (k in 1 until dimF) {
                val idx = k * dimT + t
                bufL[k * 2] = srcTensor[p0 + idx].toDouble()
                bufL[k * 2 + 1] = srcTensor[p1 + idx].toDouble()
                bufR[k * 2] = srcTensor[p2 + idx].toDouble()
                bufR[k * 2 + 1] = srcTensor[p3 + idx].toDouble()
            }
            fft.realInverse(bufL, true)
            fft.realInverse(bufR, true)

            val base = t * hopLength
            for (n in 0 until nFft) {
                val w = window[n]
                accumL[base + n] += bufL[n] * w
                accumR[base + n] += bufR[n] * w
            }
        }

        for (i in 0 until chunkSize) {
            val idx = trim + i
            val norm = normWeights[idx].coerceAtLeast(1e-8)
            leftOut[i] = (accumL[idx] / norm).toFloat()
            rightOut[i] = (accumR[idx] / norm).toFloat()
        }
    }
}
