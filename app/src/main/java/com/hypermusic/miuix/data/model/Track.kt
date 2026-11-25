package com.hypermusic.miuix.data.model

import com.google.gson.annotations.SerializedName

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
    val artistIds: List<String> = emptyList(), // 原始歌手ID列表
    val localDownloadId: String? = null,
    @SerializedName("platformData")
    internal val platformData: TrackPlatformData? = null,
    // Legacy JSON fields are retained only so existing downloads and playback snapshots remain readable.
    @SerializedName("isCloudDisk") private val legacyIsCloudDisk: Boolean? = null,
    @SerializedName("qqMediaMid") private val legacyQqMediaMid: String? = null,
    @SerializedName("qqSongId") private val legacyQqSongId: Long? = null,
    @SerializedName("bodianFreeSign") private val legacyBodianFreeSign: String? = null,
    @SerializedName("kugouHash") private val legacyKugouHash: String? = null,
    @SerializedName("kugouAlbumAudioId") private val legacyKugouAlbumAudioId: String? = null
) {
    val platform: TrackPlatformData
        get() = platformData ?: TrackPlatformData(
            isCloudDisk = legacyIsCloudDisk == true,
            qqMediaMid = legacyQqMediaMid.orEmpty(),
            qqSongId = legacyQqSongId ?: 0,
            bodianFreeSign = legacyBodianFreeSign.orEmpty(),
            kugouHash = legacyKugouHash.orEmpty(),
            kugouAlbumAudioId = legacyKugouAlbumAudioId.orEmpty()
        )
}

data class Playlist(
    val id: String,
    val title: String,
    val coverImgUrl: String,
    val sourceUrl: String,
    val trackCount: Int = 0,
    val description: String = "",
    val kugouCreatorId: String = "",
    val kugouOriginalListId: String = "",
    val kugouCreatorGid: String = "",
    val creatorId: String = ""
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
    val isCloudDisk: Boolean = false,
    val isPersonalCollection: Boolean = false,
    val isOwned: Boolean = false
)

/** QQ 音乐喜欢列表的展示名称；其他歌单名称保持不变。 */
fun displayQqLikedPlaylistTitle(title: String, accountName: String?): String {
    val name = when {
        title == "我喜欢" || title == "我喜欢的音乐" -> accountName.orEmpty().trim()
        title.endsWith("的我喜欢") -> title.removeSuffix("的我喜欢").trim()
        else -> return title
    }
    return if (name.isBlank()) "收藏的音乐" else "$name 收藏的音乐"
}

/** 详情接口可能只返回“我喜欢”，保留列表中已经确定的歌单主人。 */
fun displayQqPlaylistDetailTitle(rawTitle: String?, initialTitle: String, accountName: String?): String {
    if (rawTitle == null) return initialTitle
    if ((rawTitle == "我喜欢" || rawTitle == "我喜欢的音乐") && initialTitle.endsWith(" 收藏的音乐")) return initialTitle
    return displayQqLikedPlaylistTitle(rawTitle, accountName)
}

/** 「XXX喜欢的音乐」→「XXX 收藏的音乐」（仅展示用） */
fun displayPlaylistTitle(title: String): String {
    return if (title.endsWith("喜欢的音乐")) {
        title.removeSuffix("喜欢的音乐") + " 收藏的音乐"
    } else {
        title
    }
}

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
    val isArtistLine: Boolean = false, // 是否为歌手行
    val phonetic: String = "", // 音译/罗马音副行
    val isBackingVocal: Boolean = false, // 仅 AMLL 中被英文括号完整包裹的衬词
    val amllAgent: String = "" // 仅 AMLL 设置：v1 左侧、v2 右侧；其他来源保持为空
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

data class Comment(
    val commentId: Long = 0,
    val userId: Long = 0,
    val nickname: String = "",
    val avatarUrl: String = "",
    val content: String = "",
    val time: Long = 0, // 评论时间戳
    val likedCount: Int = 0,
    val replies: List<Comment> = emptyList()
)

data class CommentResult(
    val comments: List<Comment> = emptyList(),
    val total: Int = 0,
    val hasMore: Boolean = false
)
