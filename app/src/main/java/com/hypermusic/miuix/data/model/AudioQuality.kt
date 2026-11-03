package com.hypermusic.miuix.data.model

import com.hypermusic.miuix.data.api.MusicPlatform

/** Maximum requested quality. The server may return a lower available format. */
object AudioQuality {
    val neteaseOptions = listOf("Hi-Res 无损", "高品质（320kbps）", "中等品质（192kbps）", "标准品质（128kbps）")
    val qqOptions = listOf("Hi-Res 无损", "无损", "高品质（320kbps）", "标准品质（128kbps）", "低品质（AAC）")
    private val neteaseBitrates = listOf(999000, 320000, 192000, 128000)

    fun options(platform: MusicPlatform): List<String> = if (platform == MusicPlatform.QQ) qqOptions else neteaseOptions
    fun neteaseBitrate(index: Int): Int = neteaseBitrates[index.coerceIn(neteaseBitrates.indices)]
    fun migrateNeteaseIndex(old: Int): Int = when (old) { 0, 1 -> 0; 2, 3 -> 1; else -> 3 }

    /** Lossless bitrate varies with the recording and must not be used to infer Hi-Res. */
    fun neteaseActual(bitrate: Int, format: String): String = when {
        format.equals("flac", true) || format.equals("ape", true) || format.equals("alac", true) -> "无损"
        bitrate <= 0 -> "未知音质"
        bitrate >= 300000 -> "高品质（320kbps）"
        bitrate >= 160000 -> "中等品质（192kbps）"
        else -> "标准品质（128kbps）"
    }
}
