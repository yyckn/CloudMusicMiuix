package com.cloudmusic.miuix.viewmodel

import android.media.MediaPlayer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cloudmusic.miuix.data.api.NeteaseApi
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
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

class MusicPlayerViewModel : ViewModel() {

    var playerState by mutableStateOf(PlayerState())
        private set

    var recommendState by mutableStateOf(RecommendState())
        private set

    var playlistDetailState by mutableStateOf(PlaylistDetailState())
        private set

    var currentPlaylist by mutableStateOf<List<Track>>(emptyList())

    private var mediaPlayer: MediaPlayer? = null

    init {
        loadRecommendPlaylists()
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

    fun playTrack(track: Track) {
        viewModelScope.launch {
            playerState = playerState.copy(isLoading = true, error = null, currentTrack = track)

            NeteaseApi.getSongUrl(track.id).fold(
                onSuccess = { url ->
                    try {
                        mediaPlayer?.release()
                        mediaPlayer = MediaPlayer().apply {
                            setDataSource(url)
                            setOnPreparedListener {
                                start()
                                playerState = playerState.copy(isPlaying = true, isLoading = false)
                            }
                            setOnCompletionListener {
                                playNext()
                            }
                            setOnErrorListener { _, _, _ ->
                                playerState = playerState.copy(
                                    isLoading = false,
                                    error = "Playback error"
                                )
                                true
                            }
                            prepareAsync()
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
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playerState = playerState.copy(isPlaying = false)
            } else {
                player.start()
                playerState = playerState.copy(isPlaying = true)
            }
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
        mediaPlayer?.release()
        mediaPlayer = null
    }
}
