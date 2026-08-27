package com.cloudmusic.miuix.data.model

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artistId: String,
    val album: String,
    val albumId: String,
    val source: String = "netease",
    val sourceUrl: String,
    val imgUrl: String,
    val url: String? = null,
    val duration: Long = 0,
    val isVip: Boolean = false,
    val artistNames: List<String> = emptyList(), // 原始歌手名称列表
    val artistIds: List<String> = emptyList() // 原始歌手ID列表
)

data class Playlist(
    val id: String,
    val title: String,
    val coverImgUrl: String,
    val sourceUrl: String,
    val trackCount: Int = 0,
    val description: String = ""
)

data class PlaylistDetail(
    val info: Playlist,
    val tracks: List<Track>
)

data class RecommendPlaylist(
    val id: String,
    val title: String,
    val coverImgUrl: String,
    val playCount: Long = 0,
    val trackCount: Int = 0,
    val description: String = "",
    val isCloudDisk: Boolean = false
)

data class UserProfile(
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val gender: Int = 0, // 0: 未知, 1: 男, 2: 女
    val birthday: Long = 0,
    val province: Int = 0,
    val city: Int = 0,
    val signature: String = "",
    val description: String = "",
    val detailDescription: String = "",
    val backgroundUrl: String = "",
    val vipType: Int = 0,
    val authStatus: Int = 0,
    val expertTags: List<String> = emptyList(),
    val createTime: Long = 0 // 注册时间戳（毫秒）
)

data class LyricLine(
    val text: String,
    val translation: String = "",
    val startAt: Long, // 毫秒
    val endAt: Long = 0, // 毫秒
    val words: List<LyricWord>? = null, // 逐字歌词
    val isArtistLine: Boolean = false // 是否为歌手行
)

data class LyricWord(
    val text: String,
    val startAt: Long, // 毫秒
    val endAt: Long // 毫秒
)

data class SongLyrics(
    val lines: List<LyricLine> = emptyList(),
    val isPureMusic: Boolean = false
)

data class ArtistInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val albumSize: Int = 0,
    val mvSize: Int = 0,
    val musicSize: Int = 0,
    val briefDesc: String = ""
)

data class AlbumInfo(
    val id: Long = 0,
    val name: String = "",
    val picUrl: String = "",
    val size: Int = 0,
    val publishTime: Long = 0,
    val artist: String = "",
    val artistId: Long = 0
)
