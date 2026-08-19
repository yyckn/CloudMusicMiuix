package com.cloudmusic.miuix.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.data.model.Playlist
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.service.MediaPlaybackService
import com.google.gson.Gson
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val bitrate: Int = 0
)

data class RecommendState(
    val playlists: List<RecommendPlaylist> = emptyList(),
    val userPlaylists: List<RecommendPlaylist> = emptyList(),
    val dailySongs: List<Track> = emptyList(),
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
    val error: String? = null
)

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    var playerState by mutableStateOf(PlayerState())
        private set

    var recommendState by mutableStateOf(RecommendState())
        private set

    var playlistDetailState by mutableStateOf(PlaylistDetailState())
        private set

    var searchState by mutableStateOf(SearchState())
        private set

    var currentPlaylist by mutableStateOf<List<Track>>(emptyList())

    private var mediaPlaybackService: MediaPlaybackService? = null
    private var isBound = false
    private val gson = Gson()
    private val prefs = application.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    private var pendingTrack: Track? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlaybackService.LocalBinder
            mediaPlaybackService = binder.getService()
            isBound = true
            // 如果有待播放的歌曲，准备播放
            pendingTrack?.let { track ->
                prepareTrack(track)
                pendingTrack = null
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlaybackService = null
            isBound = false
        }
    }

    init {
        loadLastPlayedTrack()
        loadLastPlaylist()
        loadRecommendPlaylists()
        loadDailyRecommendSongs()
        loadUserPlaylists()
        bindMediaService()
        startProgressUpdate()
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                if (playerState.isPlaying) {
                    mediaPlaybackService?.let { service ->
                        playerState = playerState.copy(
                            currentPosition = service.getCurrentPosition(),
                            duration = service.getDuration()
                        )
                    }
                }
            }
        }
    }

    private fun bindMediaService() {
        val intent = Intent(getApplication(), MediaPlaybackService::class.java)
        getApplication<Application>().startService(intent)
        getApplication<Application>().bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun loadLastPlayedTrack() {
        val trackJson = prefs.getString("last_track", null)
        if (trackJson != null) {
            try {
                val track = gson.fromJson(trackJson, Track::class.java)
                playerState = playerState.copy(currentTrack = track, isPlaying = false)
                // 自动准备播放
                prepareTrack(track)
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    private fun saveLastPlayedTrack(track: Track) {
        val trackJson = gson.toJson(track)
        prefs.edit().putString("last_track", trackJson).apply()
    }

    private fun prepareTrack(track: Track) {
        if (!isBound) {
            pendingTrack = track
            return
        }
        viewModelScope.launch {
            NeteaseApi.getSongUrl(track.id).fold(
                onSuccess = { (url, bitrate, _) ->
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            service.prepareTrackWithUrl(track, url)
                            playerState = playerState.copy(bitrate = bitrate)
                        }
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                },
                onFailure = { e ->
                    // Ignore errors
                }
            )
        }
    }

    private fun loadLastPlaylist() {
        val playlistJson = prefs.getString("last_playlist", null)
        if (playlistJson != null) {
            try {
                val tracks = gson.fromJson(playlistJson, Array<Track>::class.java).toList()
                currentPlaylist = tracks
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
    }

    private fun saveLastPlaylist() {
        if (currentPlaylist.isNotEmpty()) {
            val playlistJson = gson.toJson(currentPlaylist)
            prefs.edit().putString("last_playlist", playlistJson).apply()
        }
    }

    fun loadRecommendPlaylists() {
        viewModelScope.launch {
            recommendState = recommendState.copy(isLoading = true, error = null)
            NeteaseApi.getRecommendPlaylists().fold(
                onSuccess = { playlists ->
                    recommendState = recommendState.copy(
                        playlists = playlists,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    recommendState = recommendState.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun loadDailyRecommendSongs() {
        viewModelScope.launch {
            NeteaseApi.getDailyRecommendSongs().fold(
                onSuccess = { songs ->
                    recommendState = recommendState.copy(dailySongs = songs)
                },
                onFailure = { e ->
                    android.util.Log.e("MusicPlayerViewModel", "loadDailyRecommendSongs error: ${e.message}")
                }
            )
        }
    }

    fun loadUserPlaylists() {
        viewModelScope.launch {
            android.util.Log.d("MusicPlayerViewModel", "loadUserPlaylists called")
            val userId = NeteaseApi.getCurrentUserId()
            android.util.Log.d("MusicPlayerViewModel", "loadUserPlaylists userId: $userId")
            if (userId != null) {
                android.util.Log.d("MusicPlayerViewModel", "loadUserPlaylists - calling API with userId: $userId")
                NeteaseApi.getUserPlaylists(userId).fold(
                    onSuccess = { playlists ->
                        android.util.Log.d("MusicPlayerViewModel", "loadUserPlaylists success: ${playlists.size} playlists")
                        recommendState = recommendState.copy(userPlaylists = playlists)
                    },
                    onFailure = { e ->
                        android.util.Log.e("MusicPlayerViewModel", "loadUserPlaylists error: ${e.message}")
                    }
                )
            } else {
                android.util.Log.d("MusicPlayerViewModel", "loadUserPlaylists: userId is null - user not logged in")
            }
        }
    }

    fun loadPlaylistDetail(playlistId: String) {
        viewModelScope.launch {
            playlistDetailState = playlistDetailState.copy(isLoading = true, error = null)
            
            // 每日推荐歌单特殊处理
            if (playlistId == "daily_recommend") {
                val dailySongs = recommendState.dailySongs
                playlistDetailState = playlistDetailState.copy(
                    detail = PlaylistDetail(
                        info = Playlist(
                            id = "daily_recommend",
                            title = "每日推荐",
                            coverImgUrl = dailySongs.firstOrNull()?.imgUrl ?: "",
                            sourceUrl = "",
                            trackCount = dailySongs.size,
                            description = "根据你的音乐口味生成"
                        ),
                        tracks = dailySongs
                    ),
                    isLoading = false
                )
            }
            // 云盘歌单特殊处理
            else if (playlistId == "cloud_disk") {
                cloudDiskOffset = 0
                cloudDiskHasMore = true
                NeteaseApi.getCloudDiskSongs(offset = 0, limit = cloudDiskLimit).fold(
                    onSuccess = { result ->
                        cloudDiskOffset = result.tracks.size
                        cloudDiskHasMore = result.hasMore
                        playlistDetailState = playlistDetailState.copy(
                            detail = PlaylistDetail(
                                info = Playlist(
                                    id = "cloud_disk",
                                    title = "云盘",
                                    coverImgUrl = "",
                                    sourceUrl = "",
                                    trackCount = result.tracks.size,
                                    description = ""
                                ),
                                tracks = result.tracks
                            ),
                            isLoading = false
                        )
                    },
                    onFailure = { e ->
                        playlistDetailState = playlistDetailState.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error"
                        )
                    }
                )
            } else {
                NeteaseApi.getPlaylistDetail(playlistId).fold(
                    onSuccess = { detail ->
                        playlistDetailState = playlistDetailState.copy(
                            detail = detail,
                            isLoading = false
                        )
                    },
                    onFailure = { e ->
                        playlistDetailState = playlistDetailState.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error"
                        )
                    }
                )
            }
        }
    }

    // 云盘分段加载相关变量
    private var cloudDiskOffset = 0
    private var cloudDiskHasMore = true
    private val cloudDiskLimit = 100
    var isCloudDiskLoadingMore by mutableStateOf(false)
        private set

    fun loadMoreCloudDiskSongs() {
        if (!cloudDiskHasMore || isCloudDiskLoadingMore) return
        
        viewModelScope.launch {
            isCloudDiskLoadingMore = true
            NeteaseApi.getCloudDiskSongs(offset = cloudDiskOffset, limit = cloudDiskLimit).fold(
                onSuccess = { result ->
                    cloudDiskOffset += result.tracks.size
                    cloudDiskHasMore = result.hasMore
                    
                    val currentTracks = playlistDetailState.detail?.tracks ?: emptyList()
                    val allTracks = currentTracks + result.tracks
                    
                    playlistDetailState = playlistDetailState.copy(
                        detail = playlistDetailState.detail?.copy(
                            info = playlistDetailState.detail?.info?.copy(
                                trackCount = allTracks.size
                            ) ?: Playlist("", "", "", "", allTracks.size),
                            tracks = allTracks
                        )
                    )
                    currentPlaylist = allTracks
                    isCloudDiskLoadingMore = false
                },
                onFailure = { e ->
                    isCloudDiskLoadingMore = false
                }
            )
        }
    }

    fun clearPlaylistDetail() {
        playlistDetailState = PlaylistDetailState()
    }

    fun searchSongs(keyword: String) {
        viewModelScope.launch {
            searchState = searchState.copy(isLoading = true, error = null)
            NeteaseApi.searchSongs(keyword).fold(
                onSuccess = { tracks ->
                    searchState = searchState.copy(
                        results = tracks,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    searchState = searchState.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun clearSearch() {
        searchState = SearchState()
    }

    fun playTrack(track: Track) {
        viewModelScope.launch {
            playerState = playerState.copy(isLoading = true, error = null, currentTrack = track)
            saveLastPlayedTrack(track)
            saveLastPlaylist()

            NeteaseApi.getSongUrl(track.id).fold(
                onSuccess = { (url, bitrate, _) ->
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            service.playTrackWithUrl(track, url)
                            playerState = playerState.copy(
                                isPlaying = true,
                                isLoading = false,
                                bitrate = bitrate
                            )
                        }
                    } catch (e: Exception) {
                        playerState = playerState.copy(
                            isLoading = false,
                            error = e.message
                        )
                    }
                },
                onFailure = { e ->
                    playerState = playerState.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    fun togglePlayPause() {
        mediaPlaybackService?.let { service ->
            service.togglePlayPause()
            playerState = playerState.copy(isPlaying = !playerState.isPlaying)
        }
    }

    fun seekTo(position: Long) {
        mediaPlaybackService?.seekTo(position)
        playerState = playerState.copy(currentPosition = position)
    }

    fun playNext() {
        val current = playerState.currentTrack ?: return
        val index = currentPlaylist.indexOfFirst { it.id == current.id }
        if (index >= 0 && index < currentPlaylist.size - 1) {
            playTrack(currentPlaylist[index + 1])
        }
    }

    fun playPrevious() {
        val current = playerState.currentTrack ?: return
        val index = currentPlaylist.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playTrack(currentPlaylist[index - 1])
        }
    }

    fun downloadCurrentTrack() {
        val track = playerState.currentTrack ?: return
        viewModelScope.launch {
            NeteaseApi.getSongUrl(track.id).fold(
                onSuccess = { (url, _, type) ->
                    if (url.isNotEmpty()) {
                        // 触发下载
                        downloadSong(url, track.title, track.artist, type)
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("MusicPlayerViewModel", "Failed to get download URL: ${e.message}")
                }
            )
        }
    }

    private fun downloadSong(url: String, title: String, artist: String, type: String = "mp3") {
        try {
            val context = getApplication<Application>()
            val downloadManager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            val fileName = "$title - $artist.$type"
            val request = android.app.DownloadManager.Request(android.net.Uri.parse(url))
                .setTitle("$title - $artist")
                .setDescription("正在下载歌曲")
                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_MUSIC, fileName)
            downloadManager.enqueue(request)
            android.util.Log.d("MusicPlayerViewModel", "Download started: $fileName")
        } catch (e: Exception) {
            android.util.Log.e("MusicPlayerViewModel", "Download failed: ${e.message}")
        }
    }

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
