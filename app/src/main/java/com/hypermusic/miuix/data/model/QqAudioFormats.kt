package com.hypermusic.miuix.data.model

/** QQ 音乐按文件前缀区分可请求的格式，按最高到最低排列。 */
internal object QqAudioFormats {
    data class Format(val prefix: String, val extension: String, val bitrate: Int, val quality: String)
    val all = listOf(
        Format("RS01", "flac", 0, "Hi-Res 无损"),
        Format("F000", "flac", 0, "无损"),
        Format("M800", "mp3", 320000, "高品质（320kbps）"),
        Format("M500", "mp3", 128000, "标准品质（128kbps）"),
        Format("C400", "m4a", 96000, "低品质（AAC）")
    )
    fun candidates(index: Int): List<Format> = all.drop(index.coerceIn(all.indices))
    fun match(candidates: List<Format>, filename: String): Format? =
        candidates.firstOrNull { filename.substringBefore('?').substringAfterLast('/').startsWith(it.prefix) }
}
