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
    val isVip: Boolean = false
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
    val description: String = ""
)
