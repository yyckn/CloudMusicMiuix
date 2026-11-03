package com.hypermusic.miuix.data.model

/** 搜索分类和详情页所需的标识。 */
enum class SearchCategory(val title: String, val neteaseType: Int, val qqType: Int) {
    SONG("单曲", 1, 0), PLAYLIST("歌单", 1000, 3), ALBUM("专辑", 10, 2), ARTIST("歌手", 100, 1)
}

data class SearchEntity(
    val id: String,
    val title: String,
    val imageUrl: String = "",
    val creator: String = "",
    val songCount: Long? = null,
    val playCount: Long? = null,
    val albumCount: Long? = null,
    val date: String = ""
)
