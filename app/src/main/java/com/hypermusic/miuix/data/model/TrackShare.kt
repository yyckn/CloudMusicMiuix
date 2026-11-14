package com.hypermusic.miuix.data.model

/** Keep the platform and link attached to the song, even after switching sources. */
fun Track.shareText(): String {
    val platform = when (source) {
        "netease" -> "网易云音乐"
        "qqmusic" -> "QQ音乐"
        "bodian" -> "波点音乐"
        "kugou" -> "酷狗音乐"
        "qishui" -> "汽水音乐"
        else -> "HyperMusic"
    }
    val link = sourceUrl.ifBlank {
        if (source == "netease") "https://music.163.com/#/song?id=${id.removePrefix("netrack_")}" else ""
    }
    return "【$platform】分享来自 @${artist} 的单曲《${title}》" + if (link.isBlank()) "" else ": $link"
}
