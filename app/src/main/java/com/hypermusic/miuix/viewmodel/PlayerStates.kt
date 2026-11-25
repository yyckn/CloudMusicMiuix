package com.hypermusic.miuix.viewmodel

import androidx.annotation.Keep
import com.google.gson.annotations.SerializedName
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.model.PlaylistDetail
import com.hypermusic.miuix.data.model.RecommendPlaylist
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPosition: Long = 0L,
    val lyricOffsetMs: Long = 0L,
    val duration: Long = 0L,
    val bitrate: Int = 0,
    val audioQuality: String = ""
)

@Keep
internal data class PlatformPlaybackSnapshot(
    @SerializedName(value = "track", alternate = ["a"]) val track: Track? = null,
    @SerializedName(value = "playlist", alternate = ["b"]) val playlist: List<Track> = emptyList(),
    @SerializedName(value = "original", alternate = ["c"]) val original: List<Track> = emptyList(),
    @SerializedName(value = "position", alternate = ["d"]) val position: Long = 0,
    @SerializedName(value = "mode", alternate = ["e"]) val mode: Int = 0,
    @SerializedName(value = "fm", alternate = ["f"]) val fm: Boolean = false,
    @SerializedName(value = "intelligence", alternate = ["g"]) val intelligence: Boolean = false,
    @SerializedName(value = "fmTracks", alternate = ["i"]) val fmTracks: List<Track> = emptyList(),
    @SerializedName(value = "fmIndex", alternate = ["j"]) val fmIndex: Int = 0,
    @SerializedName(value = "intelligenceTracks", alternate = ["k"]) val intelligenceTracks: List<Track> = emptyList(),
    @SerializedName(value = "intelligenceIndex", alternate = ["l"]) val intelligenceIndex: Int = 0,
    @SerializedName(value = "seed", alternate = ["m"]) val seed: String = "",
    @SerializedName(value = "playlistId", alternate = ["n"]) val playlistId: String = "",
    @SerializedName("albumPlayback") val albumPlayback: Boolean = false,
    @SerializedName("additionalRadioId") val additionalRadioId: String? = null,
    @SerializedName("fmModeLabel") val fmModeLabel: String? = null
) {
    internal fun belongsTo(platform: MusicPlatform): Boolean {
        val source = platform.trackSource
        return (additionalRadioId == null || additionalRadioId.startsWith("extra:${platform.storageKey}:")) &&
            (track == null || track.source == source || !track.localDownloadId.isNullOrBlank()) &&
            listOf(playlist, original, fmTracks, intelligenceTracks).all { tracks ->
                tracks.orEmpty().all { it?.source == source || !it?.localDownloadId.isNullOrBlank() }
            }
    }
}

data class RecommendState(
    val playlists: List<RecommendPlaylist> = emptyList(),
    val userPlaylists: List<RecommendPlaylist> = emptyList(),
    val dailySongs: List<Track> = emptyList(),
    /** 雷达歌单 */
    val radarPlaylists: List<RecommendPlaylist> = emptyList(),
    /** 歌手推荐 */
    val recommendArtists: List<com.hypermusic.miuix.data.model.ArtistInfo> = emptyList(),
    /** 新碟上架 */
    val newAlbums: List<com.hypermusic.miuix.data.model.AlbumInfo> = emptyList(),
    /** 收藏的专辑 */
    val userAlbums: List<com.hypermusic.miuix.data.model.AlbumInfo> = emptyList(),
    /** 云盘第一首歌封面（歌单页云盘卡片用） */
    val cloudDiskCoverUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

data class PlaylistDetailState(
    val detail: PlaylistDetail? = null,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class SearchState(
    val results: List<Track> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val hasMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)

data class LyricState(
    val lyrics: SongLyrics? = null,
    val source: String? = null,
    val rawDebugText: String = "",
    val matchedTrack: Track? = null,
    val upgraded: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val loadingStartedAt: Long = 0
)
