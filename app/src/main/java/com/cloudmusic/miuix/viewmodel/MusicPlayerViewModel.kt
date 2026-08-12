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
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.service.MediaPlaybackService
import com.google.gson.Gson
import kotlinx.coroutines.launch

data class PlayerState(
    val currentTrack: Track? = null,
    val isPlaying: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null
)

data class RecommendState(
    val playlists: List<RecommendPlaylist> = emptyList(),
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
        bindMediaService()
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
                onSuccess = { url ->
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            service.prepareTrackWithUrl(track, url)
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

    fun loadPlaylistDetail(playlistId: String) {
        viewModelScope.launch {
            playlistDetailState = playlistDetailState.copy(isLoading = true, error = null)
            NeteaseApi.getPlaylistDetail(playlistId).fold(
                onSuccess = { detail ->
                    playlistDetailState = playlistDetailState.copy(
                        detail = detail,
                        isLoading = false
                    )
                    currentPlaylist = detail.tracks
                    saveLastPlaylist()
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
                onSuccess = { url ->
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            service.playTrackWithUrl(track, url)
                            playerState = playerState.copy(isPlaying = true, isLoading = false)
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

    override fun onCleared() {
        super.onCleared()
        if (isBound) {
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
