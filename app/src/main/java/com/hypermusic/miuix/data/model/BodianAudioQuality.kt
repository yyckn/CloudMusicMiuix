package com.hypermusic.miuix.data.model

internal fun bodianActualQuality(lossless: Boolean, bitrate: Int): String = when {
    lossless -> "无损"
    bitrate == 320_000 -> "高品质（320kbps）"
    bitrate == 128_000 -> "标准品质（128kbps）"
    bitrate > 0 -> "${bitrate / 1000}kbps"
    else -> "未知音质"
}
