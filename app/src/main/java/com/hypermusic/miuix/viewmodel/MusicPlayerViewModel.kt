package com.hypermusic.miuix.viewmodel
import com.hypermusic.miuix.data.cache.*
import com.hypermusic.miuix.ui.theme.ThemeSettings

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.api.SharedPlaybackAccounts
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.model.Playlist
import com.hypermusic.miuix.data.model.PlaylistDetail
import com.hypermusic.miuix.data.model.RecommendPlaylist
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.lyrics.LyricsRepository
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.AudioQuality
import com.hypermusic.miuix.service.MediaPlaybackService
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {
    val listeningHistorySession = com.hypermusic.miuix.ui.pages.ListeningHistorySession()

    private val radarPlaylistIds = setOf("3136952023", "8402996200", "5320167908", "5327906368", "5362359247", "5300458264", "5341776086")

    var playerState by mutableStateOf(PlayerState())
        private set

    private var neteaseHomeScope: String? = null
    private var neteaseHomeGeneration = 0
    private val neteaseHomePartRevisions = mutableMapOf<String, Int>()
    var recommendState by mutableStateOf(RecommendState())
        private set

    var qqHomePlaylists by mutableStateOf<List<RecommendPlaylist>>(emptyList())
        private set
    var qqHomeFeatures by mutableStateOf<Map<QqMusicApi.HomeFeature, Result<PlaylistDetail>>>(emptyMap())
        private set
    var qqHomeCharts by mutableStateOf(QqMusicApi.featuredTopCharts)
        private set
    var qqHomeLoading by mutableStateOf(false)
        private set
    var qqHomeError by mutableStateOf<String?>(null)
        private set
    private var qqHomeAccountKey: String? = null
    private var qqHomeJob: Job? = null
    private var qqHomeGeneration = 0
    private val qqFeatureRevisions = mutableMapOf<QqMusicApi.HomeFeature, Int>()
    private var neteaseDailyRevision = 0

    var qqFavoritePlaylists by mutableStateOf<List<RecommendPlaylist>>(emptyList())
        private set
    var qqFavoriteLoading by mutableStateOf(false)
        private set
    var qqFavoriteError by mutableStateOf<String?>(null)
        private set
    private var qqFavoriteAccountKey: String? = null
    private var qqFavoriteJob: Job? = null
    private var qqFavoriteGeneration = 0

    fun loadQqFavorites(force: Boolean = false) {
        val accountKey = QqMusicApi.dataAccountId?.takeIf { QqMusicApi.isLoggedIn() } ?: "<guest>"
        if (!force && qqFavoriteAccountKey == accountKey && qqFavoriteError == null) return
        val changed = qqFavoriteAccountKey != accountKey
        qqFavoriteAccountKey = accountKey
        qqFavoriteJob?.cancel()
        val generation = ++qqFavoriteGeneration
        if (changed) {
            qqFavoritePlaylists = emptyList()
            qqFavoriteError = null
        }
        if (accountKey == "<guest>") {
            qqFavoriteLoading = false
            return
        }
        qqFavoriteLoading = true
        qqFavoriteJob = viewModelScope.launch {
            try {
                val result = QqMusicApi.getUserPlaylists()
                if (generation != qqFavoriteGeneration) return@launch
                result.fold(
                    onSuccess = { qqFavoritePlaylists = it; qqFavoriteError = null },
                    onFailure = { qqFavoriteError = it.message ?: "加载收藏失败" }
                )
            } finally {
                if (generation == qqFavoriteGeneration) qqFavoriteLoading = false
            }
        }
    }

    var qqFavoriteAlbums by mutableStateOf<List<com.hypermusic.miuix.data.model.AlbumInfo>>(emptyList())
        private set
    private var qqAlbumAccount: String? = null
    private var qqAlbumJob: Job? = null
    private var qqAlbumGeneration = 0

    fun loadQqFavoriteAlbums(force: Boolean = false) {
        val account = QqMusicApi.dataAccountId?.takeIf { QqMusicApi.isLoggedIn() }
        if (!force && account != null && qqAlbumAccount == account) return
        qqAlbumJob?.cancel()
        val generation = ++qqAlbumGeneration
        if (qqAlbumAccount != account) qqFavoriteAlbums = emptyList()
        qqAlbumAccount = account
        if (account == null) return
        qqAlbumJob = viewModelScope.launch {
            QqMusicApi.getFavoriteAlbums().fold(onSuccess = { albums ->
                if (generation == qqAlbumGeneration && QqMusicApi.dataAccountId == account) qqFavoriteAlbums = albums
            }, onFailure = {
                if (generation == qqAlbumGeneration) qqAlbumAccount = null
                Log.e("MusicPlayerViewModel", "QQ收藏专辑加载失败: ${it.message}")
            })
        }
    }

    fun invalidateQqHome() {
        qqHomeGeneration++
        qqHomeJob?.cancel()
        qqHomeAccountKey = null
        qqHomeLoading = false
    }

    fun loadQqHome(force: Boolean = false) {
        val accountKey = QqMusicApi.dataAccountId ?: "<guest>"
        if (!force && qqHomeAccountKey == accountKey) return
        val accountChanged = qqHomeAccountKey != accountKey
        qqHomeAccountKey = accountKey
        qqHomeJob?.cancel()
        val generation = ++qqHomeGeneration
        if (accountChanged) {
            qqHomePlaylists = emptyList()
            qqHomeFeatures = emptyMap()
            qqHomeCharts = QqMusicApi.featuredTopCharts
            qqHomeError = null
        }
        qqHomeLoading = true
        val pageKey = PageCache.key(getApplication(), MusicPlatform.QQ, "home")
        qqHomeJob = viewModelScope.launch {
            (if (accountChanged) PageCache.read<CachedQqHome>(getApplication(), pageKey) else null)?.let { cached ->
                if (generation == qqHomeGeneration) {
                    qqHomePlaylists = cached.playlists
                    qqHomeCharts = cached.charts.ifEmpty { QqMusicApi.featuredTopCharts }
                    qqHomeFeatures = cached.features.mapNotNull { (name, detail) -> QqMusicApi.HomeFeature.entries.firstOrNull { it.name == name }?.let { it to Result.success(detail) } }.toMap()
                }
            }
            try {
                coroutineScope {
                    launch {
                        val result = QqMusicApi.getTopCharts()
                        if (generation == qqHomeGeneration) result.onSuccess { qqHomeCharts = it }
                    }
                    launch {
                        val result = QqMusicApi.getRecommendPlaylists()
                        if (generation != qqHomeGeneration) return@launch
                        result.fold({ qqHomePlaylists = it; qqHomeError = null }, { qqHomeError = it.message })
                    }
                    QqMusicApi.HomeFeature.entries.forEach { feature ->
                        val featureRevision = qqFeatureRevisions[feature] ?: 0
                        launch {
                            val result = QqMusicApi.getHomeFeature(feature)
                            if (generation == qqHomeGeneration && featureRevision == (qqFeatureRevisions[feature] ?: 0)) {
                                if (result.isSuccess || qqHomeFeatures[feature]?.isSuccess != true)
                                    qqHomeFeatures = qqHomeFeatures + (feature to result)
                            }
                        }
                    }
                }
            } finally {
                if (generation == qqHomeGeneration) {
                    qqHomeLoading = false
                    PageCache.write(getApplication(), pageKey, CachedQqHome(qqHomePlaylists, qqHomeCharts,
                        qqHomeFeatures.mapNotNull { (feature, result) -> result.getOrNull()?.let { feature.name to it } }.toMap()))
                }
            }
        }
    }

    var playlistDetailState by mutableStateOf(PlaylistDetailState())
        private set

    var searchState by mutableStateOf(SearchState())
        private set

    var showUpdateLog by mutableStateOf(false)
        private set
    var selectedUpdateLog by mutableStateOf<com.hypermusic.miuix.data.model.UpdateInfo?>(null)
        private set

    var lyricState by mutableStateOf(LyricState())
        private set

    var isCurrentTrackLiked by mutableStateOf(false)
        private set
    private var likeStateGeneration = 0
    private var likeMutationInProgress = false

    // Toast消息通道
    val toastMessage = Channel<String>(Channel.BUFFERED)
    private val _toastMessage = toastMessage

    var currentPlaylist by mutableStateOf<List<Track>>(emptyList())
    
    // 用户信息
    var userId by mutableStateOf<String?>(null)
        private set
    var userNickname by mutableStateOf<String?>(null)
        private set
    var userAvatarUrl by mutableStateOf<String?>(null)
        private set
    var userVipType by mutableIntStateOf(0) // 0: 非VIP, 1100: VIP
        private set
    
    // 多账号切换
    var playbackAccountIndex by mutableStateOf(0)
        private set
    var dataAccountIndex by mutableStateOf(0)
        private set
    var playbackVipType by mutableIntStateOf(0) // 播放账号的VIP状态
        private set

    // 播放模式：0=顺序播放，1=乱序播放，2=单曲循环
    var playMode by mutableStateOf(0)
        private set
    private var originalPlaylist: List<Track> = emptyList()

    // 播放速度：0.1x - 5.0x，最多两位小数
    var playbackSpeed by mutableFloatStateOf(1.0f)
        private set

    private var platformQualityIndices by mutableStateOf<Map<MusicPlatform, Int>>(emptyMap())
    private fun resolvedAudioQualityIndex(platform: MusicPlatform) = platformQualityIndices[platform] ?: 0
    // Each platform stores its own API/menu quality index.
    val neteaseAudioQualityIndex get() = resolvedAudioQualityIndex(MusicPlatform.NETEASE)
    val qqAudioQualityIndex get() = resolvedAudioQualityIndex(MusicPlatform.QQ)
    val bodianAudioQualityIndex get() = resolvedAudioQualityIndex(MusicPlatform.BODIAN)
    val kugouAudioQualityIndex get() = resolvedAudioQualityIndex(MusicPlatform.KUGOU)
    val qishuiAudioQualityIndex get() = resolvedAudioQualityIndex(MusicPlatform.QISHUI)
    fun platformAudioQualityIndex(platform: MusicPlatform): Int = resolvedAudioQualityIndex(platform)
    val audioQualityIndex get() = platformAudioQualityIndex(PlatformManager.current)
    // 随身听FM模式
    var isFMMode by mutableStateOf(false)
        private set
    private var fmPlaylist: List<Track> = emptyList()
    internal val additionalRecommendations = com.hypermusic.miuix.data.api.AdditionalRecommendations(application)
    private var additionalRadioId: String? = null
    var fmModeLabel by mutableStateOf("私人FM")
        private set
    private var additionalRadioJob: Job? = null
    private var fmIndex by mutableIntStateOf(0)

    // 心动模式
    var isIntelligenceMode by mutableStateOf(false)
        private set
    private var intelligencePlaylist: List<Track> = emptyList()
    private var intelligenceIndex by mutableIntStateOf(0)
    private var intelligencePlaylistId: String = ""
    private var intelligenceSeedSongId: String = ""

    private var mediaPlaybackService: MediaPlaybackService? = null
    private var isBound = false
    private val gson = Gson()
    private val prefs = application.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)

    init {
        com.hypermusic.miuix.data.download.Downloads.init(application)
        // 恢复FM模式或心动模式状态
        val savedMode = if (PlatformManager.current == MusicPlatform.NETEASE && !prefs.contains("playback_snapshot_netease")) prefs.getString("play_mode_type", "normal") else "normal"
        when (savedMode) {
            "fm" -> {
                isFMMode = true
                // FM歌曲列表会在绑定服务后异步加载
            }
            "intelligence" -> {
                isIntelligenceMode = true
                val songId = prefs.getString("intelligence_song_id", "") ?: ""
                val playlistId = prefs.getString("intelligence_playlist_id", "") ?: ""
                if (songId.isNotEmpty() && playlistId.isNotEmpty()) {
                    // 心动模式歌曲列表会在绑定服务后异步加载
                }
            }
        }
    }

    private var playbackGeneration = 0
    private var playTrackRequestId = 0
    private var pendingPlatformRestore: MusicPlatform? = null
    private val initialPlaybackReady = kotlinx.coroutines.CompletableDeferred<Unit>()
    private var dailyShortcutJob: Job? = null
    private var clearServiceOnConnect = false
    private var pendingTrack: Track? = null
    private var albumPlayback = false
    private var pendingPosition = 0L
    private var pendingResume = false
    private var lyricsLoadJob: kotlinx.coroutines.Job? = null
    private var lyricsLoadGeneration = 0
    private var lyricsTrackKey: Pair<String, String>? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlaybackService.LocalBinder
            mediaPlaybackService = binder.getService()
            isBound = true
            if (clearServiceOnConnect) {
                mediaPlaybackService?.clearPlayback()
                clearServiceOnConnect = false
            }
            mediaPlaybackService?.setPlaybackSpeed(playbackSpeed)
            mediaPlaybackService?.setAlbumPlayback(albumPlayback)
            
            // 设置回调，处理系统控制器的上一首/下一首
            mediaPlaybackService?.onTrackChangedListener = object : MediaPlaybackService.OnTrackChangedListener {
                override fun onNextTrack() {
                    onTrackCompleted()
                }
                override fun onPreviousTrack() {
                    playPrevious()
                }
            }
            
            // 设置回调，同步播放状态（处理音频焦点丢失等情况）
            mediaPlaybackService?.onPlaybackStateChanged = { isPlaying ->
                playerState = playerState.copy(isPlaying = isPlaying, isLoading = if (isPlaying) false else playerState.isLoading)
            }
            mediaPlaybackService?.onAudioFormatChanged = { track, format, cacheFormat ->
                val audioTrack = mediaPlaybackService?.currentAudioTrack ?: track
                if (audioTrack.source == "bodian" && playerState.currentTrack?.id == track.id && playerState.currentTrack?.source == track.source) {
                    val bitrate = format.averageBitrate.takeIf { it > 0 } ?: format.peakBitrate.takeIf { it > 0 } ?: 0
                    val quality = com.hypermusic.miuix.data.model.bodianActualQuality(format.sampleMimeType == "audio/flac", bitrate)
                    if (quality != "未知音质") {
                        playerState = playerState.copy(bitrate = bitrate, audioQuality = quality)
                        viewModelScope.launch(Dispatchers.IO) {
                            com.hypermusic.miuix.data.cache.AudioCache.updateActual(getApplication(), audioTrack, cacheFormat, bitrate, quality)
                        }
                    }
                }
            }
            mediaPlaybackService?.onCachedPlaybackError = { track, position, resume, format ->
                viewModelScope.launch {
                    if (playerState.currentTrack != track) return@launch
                    val audioTrack = mediaPlaybackService?.currentAudioTrack ?: track
                    val account = try { playbackAccountFor(track) } catch (e: Exception) {
                        _toastMessage.trySend(e.message ?: "无法读取账号信息")
                        return@launch
                    }
                    val route = activeCrossPlayback
                    com.hypermusic.miuix.data.cache.AudioCache.forget(getApplication(), audioTrack,
                        route?.account?.id ?: account, route?.qualityIndex ?: requestedQualityFor(track))
                    withContext(Dispatchers.IO) {
                        runCatching {
                            val cache = com.hypermusic.miuix.data.cache.AudioCache.get(getApplication())
                            cache.removeResource(com.hypermusic.miuix.data.cache.AudioCache.key(audioTrack.source, audioTrack.id, format))
                        }
                    }
                    if (playerState.currentTrack == track) prepareTrack(track, position, resume)
                }
            }
            mediaPlaybackService?.onNotificationClose = { keepPlaybackAfterNotificationClose() }
            mediaPlaybackService?.onNotificationFavorite = { track ->
                if (playerState.currentTrack == track) {
                    likeCurrentTrack { liked ->
                        mediaPlaybackService?.setFavoriteState(track, liked)
                        _toastMessage.trySend(if (liked) "已收藏" else "已取消收藏")
                    }
                }
            }
            playerState.currentTrack?.let { mediaPlaybackService?.setFavoriteState(it, isCurrentTrackLiked) }
            
            // 如果有待播放的歌曲，准备播放
            pendingTrack?.let { track ->
                prepareTrack(track, pendingPosition, pendingResume)
                pendingTrack = null
                pendingPosition = 0L
                pendingResume = false
            }
            
            // 如果已有歌词，重新同步到Lyricon（处理APP重启后恢复播放的情况）
            lyricState.lyrics?.let { lyrics ->
                mediaPlaybackService?.updateLyrics(lyrics)
                Log.d("MusicPlayerViewModel", "Synced existing lyrics to Lyricon on service connect: ${lyrics.lines.size} lines")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            mediaPlaybackService?.onTrackChangedListener = null
            mediaPlaybackService?.onPlaybackStateChanged = null
            mediaPlaybackService?.onAudioFormatChanged = null
            mediaPlaybackService?.onCachedPlaybackError = null
            mediaPlaybackService?.onNotificationClose = null
            mediaPlaybackService?.onNotificationFavorite = null
            mediaPlaybackService = null
            isBound = false
        }
    }

    init {
        // 加载保存的账号索引
        playbackAccountIndex = prefs.getInt("playback_account_index", 0)
        dataAccountIndex = prefs.getInt("data_account_index", 0)
        // 加载保存的播放模式
        playMode = prefs.getInt("play_mode", 0)
        playbackSpeed = (prefs.getFloat("playback_speed", 1.0f).coerceIn(0.5f, 3f) * 10f).roundToInt() / 10f
        com.hypermusic.miuix.data.model.PlaybackQualityPreferences.migrate(prefs)
        platformQualityIndices = MusicPlatform.entries.associateWith {
            com.hypermusic.miuix.data.model.PlaybackQualityPreferences.resolve(prefs, it)
        }
        bindMediaService()
        startProgressUpdate()

        // 先应用保存的账号，再发起所有依赖登录态的数据/播放请求，
        // 否则启动瞬间仍会使用最后登录账号（NeteaseApi.init 载入的 cookies）。
        // Post startup work until construction has initialized the state declared below this init block.
        viewModelScope.launch(Dispatchers.Main) {
          try {
            val initialGeneration = playbackGeneration
            if (PlatformManager.current == MusicPlatform.NETEASE) applySavedAccounts()
            if (initialGeneration != playbackGeneration) return@launch
            val restored = restorePlatformPlayback(PlatformManager.current)
            if (!restored) {
                loadLastPlayedTrack()
                loadLastPlaylist()
            }
            if (PlatformManager.current == MusicPlatform.NETEASE) loadNeteasePages()

            if (!restored && PlatformManager.current == MusicPlatform.NETEASE) restorePlaybackMode()
          } finally {
            initialPlaybackReady.complete(Unit)
          }
        }
    }

    private fun snapshotKey(platform: MusicPlatform) = "playback_snapshot_${platform.storageKey}"

    private fun savePlatformPlayback(platform: MusicPlatform, positionOverride: Long? = null) {
        val track = playerState.currentTrack
        // Keep the last usable queue if a newly opened radio is still fetching its first song.
        if (track == null && playerState.isLoading) return
        val serviceTrack = mediaPlaybackService?.getCurrentTrack()
        val servicePosition = if (track != null && serviceTrack?.id == track.id && serviceTrack.source == track.source)
            mediaPlaybackService?.getCurrentPosition() else null
        val snapshot = PlatformPlaybackSnapshot(track, currentPlaylist.toList(), originalPlaylist.toList(),
            positionOverride ?: servicePosition ?: playerState.currentPosition, playMode,
            isFMMode, isIntelligenceMode, fmPlaylist.toList(), fmIndex,
            intelligencePlaylist.toList(), intelligenceIndex, intelligenceSeedSongId, intelligencePlaylistId, albumPlayback, additionalRadioId, fmModeLabel)
        prefs.edit().putString(snapshotKey(platform), gson.toJson(snapshot)).apply()
    }

    private fun restorePlatformPlayback(platform: MusicPlatform): Boolean {
        val raw = prefs.getString(snapshotKey(platform), null) ?: return false
        val snapshot = runCatching { gson.fromJson(raw, PlatformPlaybackSnapshot::class.java) }.getOrNull() ?: return false
        if (!snapshot.belongsTo(platform)) return false
        val track = snapshot.track
        albumPlayback = snapshot.albumPlayback
        mediaPlaybackService?.setAlbumPlayback(albumPlayback)
        currentPlaylist = snapshot.playlist.orEmpty()
        originalPlaylist = snapshot.original.orEmpty()
        playMode = snapshot.mode.coerceIn(0, 2)
        isFMMode = snapshot.fm
        additionalRadioId = snapshot.additionalRadioId?.takeIf { it.startsWith("extra:${platform.storageKey}:") }
        fmModeLabel = snapshot.fmModeLabel?.takeIf { it.isNotBlank() }
            ?: com.hypermusic.miuix.data.model.radioModeLabel(additionalRadioId, additionalRadioId?.let { additionalRecommendations.card(it)?.title })
        isIntelligenceMode = snapshot.intelligence
        fmPlaylist = snapshot.fmTracks.orEmpty(); fmIndex = snapshot.fmIndex.coerceAtLeast(0)
        intelligencePlaylist = snapshot.intelligenceTracks.orEmpty(); intelligenceIndex = snapshot.intelligenceIndex.coerceAtLeast(0)
        intelligenceSeedSongId = snapshot.seed.orEmpty(); intelligencePlaylistId = snapshot.playlistId.orEmpty()
        playerState = PlayerState(currentTrack = track, currentPosition = snapshot.position, duration = track?.duration ?: 0)
        if (track != null) {
            loadLyrics(); checkIfCurrentTrackLiked()
            prepareTrack(track, snapshot.position, resume = false)
        }
        return true
    }

    fun switchPlaybackPlatform(from: MusicPlatform, to: MusicPlatform) {
        if (pendingPlatformRestore != from) savePlatformPlayback(from)
        clearPlaybackForPlatformSwitch(clearSaved = false)
        val generation = playbackGeneration
        pendingPlatformRestore = to
        viewModelScope.launch {
            if (to == MusicPlatform.NETEASE) applySavedAccounts()
            if (generation == playbackGeneration && PlatformManager.current == to) {
                restorePlatformPlayback(to)
                pendingPlatformRestore = null
            }
        }
    }

    private fun keepPlaybackAfterNotificationClose() {
        playbackGeneration++
        playTrackRequestId++
        additionalRadioJob?.cancel()
        dailyShortcutJob?.cancel()
        pendingTrack = null
        pendingResume = false
        val track = playerState.currentTrack
        val service = mediaPlaybackService
        val position = if (track != null && service?.getCurrentTrack() == track) service.getCurrentPosition()
            else playerState.currentPosition
        playerState = playerState.copy(isPlaying = false, isLoading = false, currentPosition = position)
        if (track != null) {
            saveLastPlayedTrack(track)
            saveLastPlaylist()
            savePlatformPlayback(PlatformManager.current, positionOverride = position)
        }
    }

    fun clearPlaybackForPlatformSwitch(clearSaved: Boolean = true) {
        additionalRadioJob?.cancel()
        additionalRadioId = null
        fmModeLabel = "私人FM"
        playbackGeneration++
        albumPlayback = false
        mediaPlaybackService?.setAlbumPlayback(false)
        pendingTrack = null
        pendingPosition = 0L
        pendingResume = false
        lyricsLoadJob?.cancel()
        lyricsLoadGeneration++
        likeStateGeneration++
        isCurrentTrackLiked = false
        lyricState = LyricState()
        currentPlaylist = emptyList()
        originalPlaylist = emptyList()
        fmPlaylist = emptyList()
        intelligencePlaylist = emptyList()
        isFMMode = false
        isIntelligenceMode = false
        playerState = PlayerState()
        if (clearSaved) {
            val platform = PlatformManager.current
            val legacySource = runCatching { gson.fromJson(prefs.getString("last_track", null), Track::class.java)?.source }.getOrNull()
            prefs.edit().apply {
                remove(snapshotKey(platform))
                if (legacySource == platform.trackSource) { remove("last_track"); remove("last_playlist") }
                if (platform == MusicPlatform.NETEASE) {
                    remove("play_mode_type"); remove("intelligence_song_id"); remove("intelligence_playlist_id")
                }
            }.apply()
        }
        if (mediaPlaybackService == null) clearServiceOnConnect = true
        else mediaPlaybackService?.clearPlayback()
    }

    fun loadNeteasePages() {
        if (PlatformManager.current != MusicPlatform.NETEASE) return
        loadUserInfo()
        loadRecommendPlaylists()
        loadDailyRecommendSongs()
        loadUserPlaylists()
        loadRadarPlaylists()
        loadTopArtists()
        loadNewAlbums()
    }

    fun activateNetease() {
        viewModelScope.launch {
            applySavedAccounts()
            if (PlatformManager.current == MusicPlatform.NETEASE) loadNeteasePages()
        }
    }

    /** 应用保存的播放账号 / 数据账号，并刷新播放账号的 VIP 状态。 */
    private suspend fun applySavedAccounts() {
        val savedAccounts = NeteaseApi.getAccounts()
        if (savedAccounts.isEmpty()) return

        // 账号列表可能变化（删除账号等），越界时回退到第一个账号
        if (playbackAccountIndex !in savedAccounts.indices) {
            playbackAccountIndex = 0
            prefs.edit().putInt("playback_account_index", 0).apply()
        }
        if (dataAccountIndex !in savedAccounts.indices) {
            dataAccountIndex = 0
            prefs.edit().putInt("data_account_index", 0).apply()
        }

        if (playbackAccountIndex < savedAccounts.size) {
            NeteaseApi.getUserProfile(savedAccounts[playbackAccountIndex].cookies).fold(
                onSuccess = { profile -> playbackVipType = profile.vipType },
                onFailure = { error ->
                    playbackVipType = 0
                    if (error is NeteaseApi.CredentialExpiredException) NeteaseApi.notifyCredentialExpired()
                }
            )
        }

        val dataUserId = NeteaseApi.getAccounts().getOrNull(dataAccountIndex)?.userId
        if (dataUserId != null) {
            NeteaseApi.switchToAccount(dataUserId)
        }
    }

    /**
     * 登录完成 / 账号列表变化后刷新账号状态：
     * 重新应用播放账号（读取 VIP）、加载用户信息与收藏。
     */
    fun refreshAccountState() {
        val activeCookie = NeteaseApi.getCookie()
        val activeIndex = NeteaseApi.getAccounts().indexOfFirst { it.cookies == activeCookie }
        if (activeIndex >= 0 && activeIndex != dataAccountIndex) {
            dataAccountIndex = activeIndex
            prefs.edit().putInt("data_account_index", activeIndex).apply()
        }
        viewModelScope.launch {
            applySavedAccounts()
            loadUserInfo()
            loadRecommendPlaylists()
            loadUserPlaylists()
        }
    }

    /** 恢复私人FM / 心动模式，需在账号应用完成后调用。 */
    private fun restorePlaybackMode() {
        val generation = playbackGeneration
        val savedMode = prefs.getString("play_mode_type", "normal")
        when (savedMode) {
            "fm" -> {
                isFMMode = true
                viewModelScope.launch {
                    NeteaseApi.getPersonalFM().fold(
                        onSuccess = { tracks ->
                            if (generation != playbackGeneration || PlatformManager.current != MusicPlatform.NETEASE) return@fold
                            fmPlaylist = tracks
                            if (tracks.isNotEmpty()) {
                                currentPlaylist = tracks
                                // 如果没有上次播放的歌曲，播放第一首
                                if (playerState.currentTrack == null) {
                                    playTrack(tracks[0])
                                }
                            }
                        },
                        onFailure = { e ->
                            Log.e("MusicPlayerViewModel", "Failed to restore FM: ${e.message}")
                        }
                    )
                }
            }
            "intelligence" -> {
                val songId = prefs.getString("intelligence_song_id", "") ?: ""
                val playlistId = prefs.getString("intelligence_playlist_id", "") ?: ""
                if (songId.isNotEmpty() && playlistId.isNotEmpty()) {
                    isIntelligenceMode = true
                    intelligenceSeedSongId = songId
                    intelligencePlaylistId = playlistId
                    viewModelScope.launch {
                        NeteaseApi.getIntelligenceList(songId, playlistId).fold(
                            onSuccess = { tracks ->
                                if (generation != playbackGeneration || PlatformManager.current != MusicPlatform.NETEASE) return@fold
                                intelligencePlaylist = tracks
                                if (tracks.isEmpty()) {
                                    // 种子/歌单失效，清掉状态避免卡在心动模式
                                    isIntelligenceMode = false
                                    prefs.edit().remove("play_mode_type").apply()
                                    return@fold
                                }
                                currentPlaylist = tracks
                                // 如果没有上次播放的歌曲，播放第一首
                                if (playerState.currentTrack == null) {
                                    playTrack(tracks[0])
                                }
                            },
                            onFailure = { e ->
                                if (generation != playbackGeneration || PlatformManager.current != MusicPlatform.NETEASE) return@fold
                                Log.e("MusicPlayerViewModel", "Failed to restore intelligence mode: ${e.message}")
                                isIntelligenceMode = false
                                prefs.edit().remove("play_mode_type").apply()
                            }
                        )
                    }
                } else {
                    prefs.edit().remove("play_mode_type").apply()
                }
            }
        }
    }

    private fun startProgressUpdate() {
        viewModelScope.launch {
            while (true) {
                delay(100)
                if (playerState.isPlaying) {
                    mediaPlaybackService?.let { service ->
                        val position = service.getCurrentPosition()
                        val duration = service.getDuration()
                        playerState = playerState.copy(
                            currentPosition = position,
                            duration = duration
                        )
                        // 同步进度到Lyricon
                        service.lyriconManager?.setPosition(position + playerState.lyricOffsetMs)
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
                if (!trackBelongsToCurrentPlatform(track)) return
                playerState = playerState.copy(currentTrack = track, isPlaying = false)
                // 加载歌词、检查喜欢状态（使用数据账号）
                loadLyrics()
                checkIfCurrentTrackLiked()
                // 最后再准备播放地址（内部会临时切换到播放账号）
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

    private fun trackBelongsToCurrentPlatform(track: Track): Boolean = track.source == PlatformManager.current.trackSource ||
        !track.localDownloadId.isNullOrBlank()

    private fun playbackAccountFor(track: Track): String? = when (track.source) {
        "qqmusic" -> QqMusicApi.playbackAccountId
        "netease" -> NeteaseApi.getAccounts().getOrNull(if (track.platform.isCloudDisk) dataAccountIndex else playbackAccountIndex)?.userId
        "bodian" -> com.hypermusic.miuix.data.api.BodianAuth(getApplication(), com.hypermusic.miuix.data.api.BodianClient(getApplication())).currentAccount(playback = true)?.uid
        "kugou" -> com.hypermusic.miuix.data.api.KugouAuth(getApplication(), com.hypermusic.miuix.data.api.KugouClient(getApplication())).account(playback = true)?.userId
        "qishui" -> com.hypermusic.miuix.data.api.QishuiAuth(getApplication(), com.hypermusic.miuix.data.api.QishuiClient(getApplication())).account(playback = true)?.userId
        else -> null
    }
    private fun requestedQualityFor(track: Track): Int = when (track.source) {
        "qqmusic" -> qqAudioQualityIndex
        "netease" -> neteaseAudioQualityIndex
        "bodian" -> bodianAudioQualityIndex
        "kugou" -> kugouAudioQualityIndex
        "qishui" -> qishuiAudioQualityIndex
        else -> 1
    }
    private fun rememberCachedFormat(track: Track, account: String?, requested: Int, format: String, bitrate: Int, quality: String) {
        com.hypermusic.miuix.data.cache.AudioCache.record(getApplication(), track, account, requested, format, bitrate, quality)
    }
    private suspend fun completeCachedAudio(track: Track, account: String?, requested: Int) = withContext(Dispatchers.IO) {
        com.hypermusic.miuix.data.cache.AudioCache.complete(getApplication(), track, account, requested)
    }

    private data class NewSourceAudio(val url: String, val preview: Boolean, val quality: String, val bitrate: Int, val lyricOffsetMs: Long = 0L)
    private suspend fun resolveNewSourceAudio(track: Track, requested: Int): NewSourceAudio = when (track.source) {
        "bodian" -> {
            val quality = when (requested) { 0 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.LOSSLESS; 1 -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.HIGH; else -> com.hypermusic.miuix.data.api.BodianPlayback.Quality.STANDARD }
            val audio = com.hypermusic.miuix.data.api.BodianPlayback(com.hypermusic.miuix.data.api.BodianClient(getApplication(), playback = true)).resolve(track, quality)
            // Zero-start audition URLs were verified to contain only the preview segment.
            // Non-zero starts still need verification before applying a lyric offset.
            check(audio.preview == null || audio.preview.startSeconds == 0L) { "该歌曲的试听片段暂不支持播放" }
            NewSourceAudio(audio.url, audio.preview != null, "未知音质", 0)
        }
        "kugou" -> {
            val api = com.hypermusic.miuix.data.api.KugouClient(getApplication())
            val quality = when (requested) { 0 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIRES; 1 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.LOSSLESS; 2 -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.HIGH; else -> com.hypermusic.miuix.data.api.KugouPlayback.Quality.STANDARD }
            val audio = com.hypermusic.miuix.data.api.KugouPlayback(api, com.hypermusic.miuix.data.api.KugouAuth(getApplication(), api)).resolve(track, quality)
            NewSourceAudio(audio.url, audio.preview, audio.qualityLabel(), audio.bitrateBps())
        }
        "qishui" -> {
            val api = com.hypermusic.miuix.data.api.QishuiClient(getApplication(), playback = true)
            val quality = when (requested) { 0 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIRES; 1 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.LOSSLESS; 2 -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.HIGH; else -> com.hypermusic.miuix.data.api.QishuiPlayback.Quality.STANDARD }
            val detail = com.hypermusic.miuix.data.api.QishuiCatalog(api).songDetail(track.id.removePrefix("qishui_"))
            val audio = com.hypermusic.miuix.data.api.QishuiPlayback(api).resolve(detail, quality)
            NewSourceAudio(audio.url, audio.preview, audio.qualityLabel(), 0, if (audio.preview) audio.clipStartMs else 0L)
        }
        else -> error("不支持该音乐平台")
    }

    private var activeCrossPlayback: SharedPlaybackAccounts.Route? = null

    private fun prepareCrossPlayback(track: Track, position: Long, resume: Boolean, restore: Boolean) {
        val generation = playbackGeneration
        val requestId = playTrackRequestId
        viewModelScope.launch {
            fun stillCurrent() = generation == playbackGeneration && requestId == playTrackRequestId && playerState.currentTrack == track
            try {
                val route = SharedPlaybackAccounts.route(getApplication(), track, requestedQualityFor(track))
                    ?: error("播放账号已切换，请重试")
                if (!stillCurrent()) return@launch
                activeCrossPlayback = route
                val downloaded = com.hypermusic.miuix.data.download.OfflineLibrary.find(route.track)
                val cached = if (downloaded == null) completeCachedAudio(route.track, route.account.id, route.qualityIndex) else null
                val audio = if (cached == null && downloaded == null) SharedPlaybackAccounts.audio(getApplication(), route) else null
                if (!stillCurrent() || SharedPlaybackAccounts.selected(getApplication(), track.source) != route.account) return@launch
                val service = mediaPlaybackService ?: error("播放器尚未就绪")
                service.setPlaylist(currentPlaylist)
                val format = downloaded?.let { "download:${it.id}" } ?: cached?.format
                    ?: "${route.account.id}:${route.qualityIndex}:${audio!!.quality}:${audio.bitrate}:${audio.extension}"
                if (audio != null && !audio.preview) rememberCachedFormat(route.track, route.account.id, route.qualityIndex,
                    format, audio.bitrate, audio.quality)
                val url = downloaded?.let { java.io.File(it.path).toURI().toString() } ?: audio?.url ?: "https://127.0.0.1/cached"
                if (restore) service.restoreTrackWithUrl(track, url, position, resume, format, audio?.preview == true, route.track)
                else service.playTrackWithUrl(track, url, format, audio?.preview == true, route.track)
                playerState = playerState.copy(isLoading = false, isPlaying = resume, error = null,
                    bitrate = downloaded?.bitrate ?: cached?.bitrate ?: audio!!.bitrate,
                    audioQuality = downloaded?.quality ?: cached?.quality ?: audio!!.quality, lyricOffsetMs = 0L)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (stillCurrent()) {
                    playerState = playerState.copy(isLoading = false, isPlaying = false, error = e.message)
                    _toastMessage.trySend(e.message ?: "跨平台播放失败")
                }
            }
        }
    }

    private fun prepareTrack(track: Track, position: Long = 0L, resume: Boolean = false) {
        // Restoring an in-app record must not recreate media dismissed by the user.
        if (!resume && prefs.getBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, false)) return
        if (!isBound) {
            pendingTrack = track
            pendingPosition = position
            pendingResume = resume
            return
        }
        val generation = playbackGeneration
        viewModelScope.launch {
            val downloaded = if (SharedPlaybackAccounts.isCrossPlatform(getApplication(), track)) null
                else com.hypermusic.miuix.data.download.OfflineLibrary.find(track)
            if (downloaded != null) {
                if (generation != playbackGeneration || playerState.currentTrack != track) return@launch
                mediaPlaybackService?.let { service ->
                    service.setPlaylist(currentPlaylist)
                    service.restoreTrackWithUrl(track, java.io.File(downloaded.path).toURI().toString(), position, resume, "download:${downloaded.id}")
                }
                playerState = playerState.copy(bitrate = downloaded.bitrate, audioQuality = downloaded.quality, lyricOffsetMs = 0)
                return@launch
            }
            if (!track.localDownloadId.isNullOrBlank()) return@launch
            activeCrossPlayback = null
            if (SharedPlaybackAccounts.isCrossPlatform(getApplication(), track)) {
                prepareCrossPlayback(track, position, resume, restore = true)
                return@launch
            }
            val cacheAccount = try { playbackAccountFor(track) } catch (e: Exception) {
                if (generation == playbackGeneration && playerState.currentTrack == track) {
                    playerState = playerState.copy(isLoading = false, error = e.message)
                    _toastMessage.trySend(e.message ?: "无法读取账号信息")
                }
                return@launch
            }
            val cacheRequest = requestedQualityFor(track)
            val cached = completeCachedAudio(track, cacheAccount, cacheRequest)
            val currentAccount = try { playbackAccountFor(track) } catch (e: Exception) {
                if (generation == playbackGeneration && playerState.currentTrack == track) {
                    playerState = playerState.copy(isLoading = false, error = e.message)
                    _toastMessage.trySend(e.message ?: "无法读取账号信息")
                }
                return@launch
            }
            if (generation != playbackGeneration || playerState.currentTrack != track || currentAccount != cacheAccount) return@launch
            if (cached != null) {
                mediaPlaybackService?.let { it.setPlaylist(currentPlaylist); it.restoreTrackWithUrl(track, "https://127.0.0.1/cached", position, resume, cached.format) }
                playerState = playerState.copy(bitrate = cached.bitrate, audioQuality = cached.quality, lyricOffsetMs = 0L)
                return@launch
            }
            if (track.source in setOf("bodian", "kugou", "qishui")) {
                try {
                    val audio = resolveNewSourceAudio(track, cacheRequest)
                    if (generation != playbackGeneration || playerState.currentTrack != track || playbackAccountFor(track) != cacheAccount) return@launch
                    val format = "${cacheAccount ?: "guest"}:$cacheRequest:${audio.quality}:${audio.bitrate}"
                    if (!audio.preview) rememberCachedFormat(track, cacheAccount, cacheRequest, format, audio.bitrate, audio.quality)
                    mediaPlaybackService?.let { it.setPlaylist(currentPlaylist); it.restoreTrackWithUrl(track, audio.url, position, resume, format, audio.preview) }
                    playerState = playerState.copy(bitrate = audio.bitrate, audioQuality = audio.quality, lyricOffsetMs = audio.lyricOffsetMs)
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    if (generation == playbackGeneration && playerState.currentTrack == track) {
                        playerState = playerState.copy(isLoading = false, error = e.message)
                        _toastMessage.trySend(e.message ?: "无法加载歌曲")
                    }
                }
                return@launch
            }
            if (track.source == "qqmusic") {
                QqMusicApi.getSongAudio(track.id, track.platform.qqMediaMid, qqAudioQualityIndex, cacheAccount).onSuccess { audio ->
                    if (generation != playbackGeneration || playerState.currentTrack != track || playbackAccountFor(track) != cacheAccount) return@onSuccess
                    val format = "${cacheAccount ?: "guest"}:${audio.quality}:${audio.bitrate}:${audio.extension}"
                    rememberCachedFormat(track, cacheAccount, cacheRequest, format, audio.bitrate, audio.quality)
                    mediaPlaybackService?.let { it.setPlaylist(currentPlaylist); it.restoreTrackWithUrl(track, audio.url, position, resume, format) }
                    playerState = playerState.copy(bitrate = audio.bitrate, audioQuality = audio.quality)
                }.onFailure { error ->
                    if (generation == playbackGeneration && playerState.currentTrack == track) {
                        playerState = playerState.copy(isLoading = false, error = error.message)
                        _toastMessage.trySend(error.message ?: "无法加载歌曲")
                    }
                }
                return@launch
            }
            // 云盘歌曲属于「获取数据」账号，必须用该账号 Cookie 获取播放地址；
            // 其他歌曲使用「播放歌曲」账号，且不切换全局账号，避免影响并发中的数据请求
            val accounts = NeteaseApi.getAccounts()
            val accountIndex = if (track.platform.isCloudDisk) dataAccountIndex else playbackAccountIndex
            val accountUserId = accounts.getOrNull(accountIndex)?.userId
            val accountCookie = accountUserId?.let { NeteaseApi.getAccountCookie(it) }

            NeteaseApi.getSongUrl(track.id, AudioQuality.neteaseBitrate(neteaseAudioQualityIndex), accountCookie = accountCookie).fold(
                onSuccess = { (url, bitrate, type) ->
                    if (generation != playbackGeneration || playerState.currentTrack != track) return@fold
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            val format = "${cacheAccount ?: "guest"}:$bitrate:$type"
                            rememberCachedFormat(track, cacheAccount, cacheRequest, format, bitrate, AudioQuality.neteaseActual(bitrate, type))
                            service.restoreTrackWithUrl(track, url, position, resume, format)
                            playerState = playerState.copy(bitrate = bitrate, audioQuality = AudioQuality.neteaseActual(bitrate, type))
                        }
                    } catch (e: Exception) {
                        // Ignore errors
                    }
                },
                onFailure = { error ->
                    if (generation == playbackGeneration && playerState.currentTrack == track) {
                        playerState = playerState.copy(isLoading = false, error = error.message)
                        _toastMessage.trySend(error.message ?: "无法加载歌曲")
                    }
                }
            )
        }
    }

    private fun loadLastPlaylist() {
        val playlistJson = prefs.getString("last_playlist", null)
        if (playlistJson != null) {
            try {
                val tracks = gson.fromJson(playlistJson, Array<Track>::class.java).toList()
                currentPlaylist = tracks.filter(::trackBelongsToCurrentPlatform)
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

    fun loadRecommendPlaylists(showLoading: Boolean = true) {
        loadNeteaseHomePart<List<RecommendPlaylist>>("playlists", apply = {
            recommendState = recommendState.copy(playlists = it, isLoading = false, error = null)
        }, fetch = {
            val result = if (NeteaseApi.isLoggedIn()) NeteaseApi.getRecommendResource() else NeteaseApi.getRecommendPlaylists()
            if (result.isSuccess) result else NeteaseApi.getRecommendPlaylists()
        }, failed = { recommendState = recommendState.copy(isLoading = false, error = it.message) })
    }

    private fun prepareNeteaseHomeScope() {
        val scope = PageCache.key(getApplication(), MusicPlatform.NETEASE, "home")
        if (neteaseHomeScope != scope) { neteaseHomeScope = scope; neteaseHomeGeneration++; recommendState = RecommendState(); neteaseHomePartRevisions.clear() }
    }
    private inline fun <reified T> loadNeteaseHomePart(part: String, crossinline apply: (T) -> Unit,
        crossinline fetch: suspend () -> Result<T>, crossinline failed: (Throwable) -> Unit = {}) {
        prepareNeteaseHomeScope()
        val key = PageCache.key(getApplication(), MusicPlatform.NETEASE, "home", part)
        val scopeGeneration = neteaseHomeGeneration
        val revision = (neteaseHomePartRevisions[part] ?: 0) + 1
        neteaseHomePartRevisions[part] = revision
        viewModelScope.launch {
            val current = { scopeGeneration == neteaseHomeGeneration && revision == neteaseHomePartRevisions[part] && key == PageCache.key(getApplication(), MusicPlatform.NETEASE, "home", part) }
            (if (revision == 1) PageCache.read<T>(getApplication(), key) else null)?.let { if (current()) apply(it) }
            if (!current()) return@launch
            val result = fetch()
            if (!current()) return@launch
            result.fold(onSuccess = { apply(it) }, onFailure = { failed(it) })
            result.getOrNull()?.let { PageCache.write(getApplication(), key, it as Any) }
        }
    }


    /** 雷达歌单：固定歌单 id 逐个取详情 */
    fun loadRadarPlaylists() {
        loadNeteaseHomePart<List<RecommendPlaylist>>("radar", apply = { recommendState = recommendState.copy(radarPlaylists = it) }, fetch = {
            val playlists = coroutineScope { radarPlaylistIds.map { id -> async { NeteaseApi.getPlaylistSummary(id).getOrNull() } }.awaitAll().filterNotNull() }
            if (playlists.isEmpty()) Result.failure(IllegalStateException("雷达歌单加载失败")) else Result.success(playlists)
        })
    }

    /** 歌手推荐（热门歌手） */
    fun loadTopArtists() {
        loadNeteaseHomePart<List<com.hypermusic.miuix.data.model.ArtistInfo>>("artists", apply = { recommendState = recommendState.copy(recommendArtists = it) }, fetch = {
            val result = NeteaseApi.getTopArtists(12)
            val artists = result.getOrNull().orEmpty().ifEmpty { deriveArtistsFromDaily() }
            if (artists.isNotEmpty()) Result.success(artists) else result
        })
    }

    /** 从每日推荐歌曲中提炼歌手作为兜底推荐 */
    private fun deriveArtistsFromDaily(): List<com.hypermusic.miuix.data.model.ArtistInfo> {
        val map = LinkedHashMap<String, com.hypermusic.miuix.data.model.ArtistInfo>()
        for (track in recommendState.dailySongs) {
            track.artistIds.forEachIndexed { index, id ->
                if (id.isNotEmpty() && !map.containsKey(id)) {
                    val name = track.artistNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: track.artist
                    map[id] = com.hypermusic.miuix.data.model.ArtistInfo(
                        id = id.toLongOrNull() ?: 0,
                        name = name,
                        picUrl = track.imgUrl
                    )
                }
            }
            if (map.size >= 12) break
        }
        return map.values.take(12).toList()
    }

    /** 新碟上架 */
    fun loadNewAlbums() {
        loadNeteaseHomePart<List<com.hypermusic.miuix.data.model.AlbumInfo>>("albums", apply = { recommendState = recommendState.copy(newAlbums = it) },
            fetch = { NeteaseApi.getNewAlbums(12) })
    }

    fun openUpdateLog(info: com.hypermusic.miuix.data.model.UpdateInfo) {
        selectedUpdateLog = info
        showUpdateLog = true
    }

    fun closeUpdateLog() {
        showUpdateLog = false
    }

    fun loadDailyRecommendSongs() {
        prepareNeteaseHomeScope()
        val requestUserId = NeteaseApi.getCurrentUserId()
        val revision = ++neteaseDailyRevision
        val key = PageCache.key(getApplication(), MusicPlatform.NETEASE, "home", "daily")
        viewModelScope.launch {
            (if (recommendState.dailySongs.isEmpty()) PageCache.read<List<Track>>(getApplication(), key) else null)?.let {
                if (NeteaseApi.getCurrentUserId() == requestUserId && revision == neteaseDailyRevision) recommendState = recommendState.copy(dailySongs = it)
            }
            NeteaseApi.getDailyRecommendSongs().fold(
                onSuccess = { songs ->
                    if (NeteaseApi.getCurrentUserId() != requestUserId || revision != neteaseDailyRevision) return@fold
                    recommendState = recommendState.copy(dailySongs = songs)
                    viewModelScope.launch { PageCache.write(getApplication(), key, songs) }
                    // 若热门歌手接口未返回，兜底从每日推荐提炼歌手
                    if (recommendState.recommendArtists.isEmpty()) {
                        val derived = deriveArtistsFromDaily()
                        if (derived.isNotEmpty()) {
                            recommendState = recommendState.copy(recommendArtists = derived)
                        }
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("MusicPlayerViewModel", "loadDailyRecommendSongs error: ${e.message}")
                }
            )
        }
    }

    /** 点击后从每日推荐和喜欢的音乐中随机选择一首。 */
    fun playRandomOne() {
        val accountId = NeteaseApi.getCurrentUserId() ?: return
        viewModelScope.launch {
            val dailySongs = recommendState.dailySongs
            val pool = dailySongs.toMutableList()
            val likedId = resolveLikedPlaylistId()
            if (likedId.isNotEmpty()) {
                NeteaseApi.getPlaylistDetail(likedId).fold(
                    onSuccess = { pool.addAll(it.tracks) },
                    onFailure = { Log.e("MusicPlayerViewModel", "playRandomOne load liked failed: ${it.message}") }
                )
            }
            if (NeteaseApi.getCurrentUserId() != accountId || PlatformManager.current != MusicPlatform.NETEASE) return@launch
            val track = pool.distinctBy { it.id }.randomOrNull()
            if (track == null) {
                _toastMessage.trySend("暂无可随机播放的歌曲")
                return@launch
            }
            playTrackWithMode(track, listOf(track) + dailySongs.filter { it.id != track.id })
        }
    }

    private var neteaseFavoriteAccountId: Long? = null
    private var neteaseFavoritesLoaded = false
    private var neteaseFavoriteJob: Job? = null
    private var neteaseFavoriteGeneration = 0
    private var neteaseFavoriteSucceeded by mutableStateOf(false)
    private var neteaseFavoriteLoading by mutableStateOf(false)
    var playlistFavoriteInProgress by mutableStateOf(false)
        private set
    private var kugouPlaylistCollected by mutableStateOf<Boolean?>(null)
    private var kugouPlaylistAccountRevision: Long? = null
    private var bodianPlaylistCollected by mutableStateOf<Boolean?>(null)
    private var bodianPlaylistAccountRevision: Long? = null

    private fun bodianPlaylistId(id: String): Pair<Int, String>? {
        val parts = id.split(':')
        if (parts.size != 3 || parts[0] != "bodian") return null
        val source = parts[1].toIntOrNull()?.takeIf { it in 1..5 } ?: return null
        val raw = parts[2].toLongOrNull()?.takeIf { it in 1..9_007_199_254_740_991L } ?: return null
        return source to raw.toString()
    }

    private fun validFavoritePlaylistId(id: String, platform: MusicPlatform): Boolean {
        val raw = if (platform == MusicPlatform.NETEASE) id.removePrefix("neplaylist_") else id
        return raw.toLongOrNull()?.let { it > 0 } == true
    }

    fun isPlaylistCollected(detail: PlaylistDetail?): Boolean {
        val id = detail?.info?.id ?: return false
        return when (PlatformManager.current) {
            MusicPlatform.NETEASE -> recommendState.userPlaylists.any { it.id.removePrefix("neplaylist_") == id.removePrefix("neplaylist_") }
            MusicPlatform.QQ -> qqFavoritePlaylists.any { it.id == id }
            MusicPlatform.KUGOU -> kugouPlaylistCollected == true
            MusicPlatform.BODIAN -> bodianPlaylistCollected == true
            MusicPlatform.QISHUI -> false
        }
    }
    fun canFavoritePlaylist(detail: PlaylistDetail?, isTopChart: Boolean): Boolean {
        val id = detail?.info?.id ?: return false
        if (id.startsWith("extra:")) return false
        val platform = PlatformManager.current
        if (isTopChart || (platform !in setOf(MusicPlatform.KUGOU, MusicPlatform.BODIAN) && !validFavoritePlaylistId(id, platform))) return false
        if (platform == MusicPlatform.NETEASE && id.removePrefix("neplaylist_") in radarPlaylistIds) return false
        return when (platform) {
            MusicPlatform.QQ -> {
                val account = QqMusicApi.dataAccountId?.takeIf { QqMusicApi.isLoggedIn() }
                if (account == null) true
                else qqFavoriteAccountKey == account && !qqFavoriteLoading && qqFavoriteError == null &&
                    qqFavoritePlaylists.none { it.id == id && it.isOwned }
            }
            MusicPlatform.NETEASE -> {
                val account = NeteaseApi.getCurrentUserId()
                if (account == null) true
                else neteaseFavoriteAccountId == account && neteaseFavoriteSucceeded && !neteaseFavoriteLoading &&
                    detail.info.creatorId != account.toString() &&
                    recommendState.userPlaylists.none { it.id.removePrefix("neplaylist_") == id.removePrefix("neplaylist_") && it.isOwned }
            }
            MusicPlatform.KUGOU -> {
                val info = detail.info
                val client = com.hypermusic.miuix.data.api.KugouClient(getApplication())
                val revision = com.hypermusic.miuix.data.api.KugouAuth(getApplication(), client).sessionGeneration()
                (kugouPlaylistCollected == true || (info.kugouCreatorId.matches(Regex("[0-9]{1,20}")) &&
                    info.kugouOriginalListId.matches(Regex("[A-Za-z0-9_-]{1,128}")))) &&
                    info.kugouCreatorId != com.hypermusic.miuix.data.api.KugouAuth(getApplication(), client).account()?.userId &&
                    kugouPlaylistAccountRevision == revision && kugouPlaylistCollected != null
            }
            MusicPlatform.BODIAN -> {
                val client = com.hypermusic.miuix.data.api.BodianClient(getApplication())
                val revision = com.hypermusic.miuix.data.api.BodianAuth(getApplication(), client).sessionRevision()
                bodianPlaylistId(id)?.first?.let { it != 5 } == true && bodianPlaylistAccountRevision == revision && bodianPlaylistCollected != null
            }
            MusicPlatform.QISHUI -> false
        }
    }

    fun favoriteCurrentPlaylist() {
        val detail = playlistDetailState.detail ?: return
        val platform = PlatformManager.current
        if (playlistFavoriteInProgress || !canFavoritePlaylist(detail, false)) return
        val favorite = !isPlaylistCollected(detail)
        val qqAccount = QqMusicApi.dataAccountId
        val neteaseAccount = NeteaseApi.getCurrentUserId()
        val kugouAccountRevision = kugouPlaylistAccountRevision
        val bodianAccountRevision = bodianPlaylistAccountRevision
        if ((platform == MusicPlatform.QQ && !QqMusicApi.isLoggedIn()) ||
            (platform == MusicPlatform.NETEASE && neteaseAccount == null) ||
            (platform == MusicPlatform.BODIAN && com.hypermusic.miuix.data.api.BodianAuth(getApplication(), com.hypermusic.miuix.data.api.BodianClient(getApplication())).currentAccount() == null)) {
            _toastMessage.trySend("请先登录后收藏歌单")
            return
        }
        playlistFavoriteInProgress = true
        viewModelScope.launch {
            try {
                val result = when (platform) {
                    MusicPlatform.QQ -> QqMusicApi.favoritePlaylist(detail.info.id, favorite)
                    MusicPlatform.NETEASE -> NeteaseApi.favoritePlaylist(detail.info.id, favorite)
                    MusicPlatform.KUGOU -> try {
                        val api = com.hypermusic.miuix.data.api.KugouClient(getApplication())
                        com.hypermusic.miuix.data.api.KugouLibrary(api, com.hypermusic.miuix.data.api.KugouAuth(getApplication(), api)).collectPlaylist(detail.info, expectedGeneration = kugouAccountRevision, favorite = favorite)
                        Result.success(Unit)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                    MusicPlatform.BODIAN -> try {
                        val (source, id) = requireNotNull(bodianPlaylistId(detail.info.id))
                        val api = com.hypermusic.miuix.data.api.BodianClient(getApplication())
                        val auth = com.hypermusic.miuix.data.api.BodianAuth(getApplication(), api)
                        com.hypermusic.miuix.data.api.BodianLibrary(api, auth, com.hypermusic.miuix.data.api.BodianCatalog(api)).collect(id, source, bodianAccountRevision, favorite = favorite)
                        Result.success(Unit)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                    MusicPlatform.QISHUI -> Result.failure(IllegalStateException("当前平台暂不支持收藏歌单"))
                }
                result.fold(onSuccess = {
                    val info = detail.info
                    val item = RecommendPlaylist(info.id, info.title, info.coverImgUrl, trackCount = info.trackCount,
                        isPersonalCollection = platform == MusicPlatform.QQ &&
                            (info.title == "我喜欢" || info.title.endsWith("喜欢的音乐") || info.title.endsWith("收藏的音乐") || info.title.endsWith("的我喜欢")))
                    when (platform) {
                        MusicPlatform.QQ -> if (QqMusicApi.dataAccountId == qqAccount && qqFavoriteAccountKey == qqAccount)
                            qqFavoritePlaylists = qqFavoritePlaylists.filterNot { it.id == info.id } + if (favorite) listOf(item) else emptyList()
                        MusicPlatform.NETEASE -> if (NeteaseApi.getCurrentUserId() == neteaseAccount && neteaseFavoriteAccountId == neteaseAccount)
                            recommendState = recommendState.copy(userPlaylists = recommendState.userPlaylists.filterNot { it.id.removePrefix("neplaylist_") == info.id.removePrefix("neplaylist_") } + if (favorite) listOf(item) else emptyList())
                        MusicPlatform.KUGOU -> {
                            com.hypermusic.miuix.data.api.AdditionalLibraryChanges.changed(platform)
                            if (playlistDetailState.detail?.info?.id == info.id && PlatformManager.current == platform && kugouPlaylistAccountRevision == kugouAccountRevision) kugouPlaylistCollected = favorite
                        }
                        MusicPlatform.BODIAN -> {
                            com.hypermusic.miuix.data.api.AdditionalLibraryChanges.changed(platform)
                            if (playlistDetailState.detail?.info?.id == info.id && PlatformManager.current == platform && bodianPlaylistAccountRevision == bodianAccountRevision) bodianPlaylistCollected = favorite
                        }
                        MusicPlatform.QISHUI -> Unit
                    }
                    _toastMessage.trySend(if (favorite) "已添加到个人收藏" else "已取消收藏")
                }, onFailure = { _toastMessage.trySend(it.message ?: "收藏歌单失败") })
            } finally {
                playlistFavoriteInProgress = false
            }
        }
    }

    fun loadUserPlaylists(force: Boolean = false) {
        val userId = NeteaseApi.getCurrentUserId()
        val accountChanged = neteaseFavoriteAccountId != userId
        if (!force && !accountChanged && neteaseFavoritesLoaded) return
        if (accountChanged) {
            neteaseFavoriteSucceeded = false
            recommendState = recommendState.copy(userPlaylists = emptyList(), userAlbums = emptyList(), cloudDiskCoverUrl = "")
            if (neteaseFavoriteAccountId != null) {
                recommendState = recommendState.copy(dailySongs = emptyList())
                if (userId != null) loadDailyRecommendSongs()
            }
        }
        neteaseFavoriteAccountId = userId
        neteaseFavoritesLoaded = true
        neteaseFavoriteJob?.cancel()
        val generation = ++neteaseFavoriteGeneration
        neteaseFavoriteLoading = userId != null
        if (userId == null) return
        neteaseFavoriteJob = viewModelScope.launch {
            try {
            NeteaseApi.getUserPlaylists(userId).fold(
                onSuccess = { playlists ->
                    if (generation != neteaseFavoriteGeneration || NeteaseApi.getCurrentUserId() != userId) return@fold
                    recommendState = recommendState.copy(userPlaylists = playlists)
                    neteaseFavoriteSucceeded = true
                    loadCloudDiskCover(force = force)
                    loadUserAlbums()
                },
                onFailure = { e ->
                    if (generation == neteaseFavoriteGeneration) neteaseFavoritesLoaded = false
                    Log.e("MusicPlayerViewModel", "loadUserPlaylists error: ${e.message}")
                }
            )
            } finally {
                if (generation == neteaseFavoriteGeneration) neteaseFavoriteLoading = false
            }
        }
    }

    /** 拉取云盘第一首歌封面，供歌单页云盘卡片展示 */
    fun loadCloudDiskCover(force: Boolean = false) {
        if (!force && recommendState.cloudDiskCoverUrl.isNotEmpty()) return
        val requestUserId = NeteaseApi.getCurrentUserId() ?: return
        viewModelScope.launch {
            NeteaseApi.getCloudDiskSongs(offset = 0, limit = 1).fold(
                onSuccess = { result ->
                    val cover = result.tracks.firstOrNull()?.imgUrl ?: ""
                    if (NeteaseApi.getCurrentUserId() == requestUserId && cover.isNotEmpty()) {
                        recommendState = recommendState.copy(cloudDiskCoverUrl = cover)
                    }
                },
                onFailure = { e ->
                    android.util.Log.e("MusicPlayerViewModel", "loadCloudDiskCover error: ${e.message}")
                }
            )
        }
    }

    /** 拉取收藏的专辑 */
    fun loadUserAlbums() {
        val requestUserId = NeteaseApi.getCurrentUserId() ?: return
        viewModelScope.launch {
            NeteaseApi.getUserAlbums().fold(
                onSuccess = { albums ->
                    if (NeteaseApi.getCurrentUserId() == requestUserId) recommendState = recommendState.copy(userAlbums = albums)
                },
                onFailure = { e ->
                    android.util.Log.e("MusicPlayerViewModel", "loadUserAlbums error: ${e.message}")
                }
            )
        }
    }

    fun loadUserInfo() {
        viewModelScope.launch {
            val currentUserId = NeteaseApi.getCurrentUserId()
            if (currentUserId != null) {
                userId = currentUserId.toString()
                NeteaseApi.getUserProfile().fold(
                    onSuccess = { profile ->
                        userNickname = profile.nickname
                        userAvatarUrl = profile.avatarUrl
                        userVipType = profile.vipType
                    },
                    onFailure = {
                        userNickname = "未知用户"
                        userAvatarUrl = ""
                        userVipType = 0
                    }
                )
            } else {
                userId = null
                userNickname = null
                userAvatarUrl = null
                userVipType = 0
            }
        }
    }

    fun updatePlaybackAccountIndex(index: Int) {
        playbackAccountIndex = index
        // 保存到SharedPreferences
        prefs.edit().putInt("playback_account_index", index).apply()
        // 切换播放账号的Cookie并获取VIP状态
        val accounts = NeteaseApi.getAccounts()
        if (index < accounts.size) {
            // 获取播放账号的VIP状态（缓存，供播放时本地判断）
            viewModelScope.launch {
                NeteaseApi.getUserProfile(accounts[index].cookies).fold(
                    onSuccess = { profile -> playbackVipType = profile.vipType },
                    onFailure = { error ->
                        playbackVipType = 0
                        if (error is NeteaseApi.CredentialExpiredException) NeteaseApi.notifyCredentialExpired()
                    }
                )
            }
        }
    }

    fun updateDataAccountIndex(index: Int) {
        dataAccountIndex = index
        // 保存到SharedPreferences
        prefs.edit().putInt("data_account_index", index).apply()
        // 切换数据账号的Cookie
        val accounts = NeteaseApi.getAccounts()
        if (index < accounts.size) {
            NeteaseApi.switchToAccount(accounts[index].userId)
            loadUserInfo()
            loadRecommendPlaylists()
            loadUserPlaylists()
            loadUserAlbums()
        }
    }

    fun switchPlayMode() {
        val oldMode = playMode
        playMode = (playMode + 1) % 3
        
        when {
            // 切换到顺序播放：恢复原始播放列表
            playMode == 0 && oldMode == 1 -> {
                if (originalPlaylist.isNotEmpty()) {
                    currentPlaylist = originalPlaylist
                    originalPlaylist = emptyList()
                }
            }
            // 切换到乱序播放：打乱播放列表
            playMode == 1 -> {
                if (originalPlaylist.isEmpty()) {
                    originalPlaylist = currentPlaylist.toList()
                }
                val currentTrack = playerState.currentTrack
                val shuffled = currentPlaylist.toMutableList()
                shuffled.shuffle()
                // 保持当前播放歌曲在第一位
                if (currentTrack != null) {
                    shuffled.remove(currentTrack)
                    shuffled.add(0, currentTrack)
                }
                currentPlaylist = shuffled
            }
            // 切换到单曲循环：恢复原始播放列表（如果有）
            playMode == 2 && oldMode == 1 -> {
                if (originalPlaylist.isNotEmpty()) {
                    currentPlaylist = originalPlaylist
                    originalPlaylist = emptyList()
                }
            }
        }
        // 保存到SharedPreferences
        prefs.edit().putInt("play_mode", playMode).apply()
    }

    fun onTrackCompleted() {
        if (isFMMode) {
            fmPlayNext()
            return
        }
        if (isIntelligenceMode) {
            intelligencePlayNext()
            return
        }
        when (playMode) {
            0, 1 -> playNext() // 顺序播放和乱序播放都播放下一首
            2 -> {
                // 单曲循环：重新播放当前歌曲
                playerState.currentTrack?.let { playTrack(it) }
            }
        }
    }

    /**
     * 播放歌曲并根据播放模式处理播放列表
     * 当乱序模式下点击歌曲时，打乱播放列表
     */
    fun playDailyRecommendationShortcut() {
        dailyShortcutJob?.cancel()
        dailyShortcutJob = viewModelScope.launch {
            initialPlaybackReady.await()
            val restoreFinished = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                while (pendingPlatformRestore != null) kotlinx.coroutines.delay(100)
                true
            } == true
            if (!restoreFinished) {
                _toastMessage.trySend("播放器尚未就绪，请重试")
                return@launch
            }
            val platform = PlatformManager.current
            val generation = playbackGeneration
            val result = if (platform == MusicPlatform.QQ) {
                qqHomeFeatures[QqMusicApi.HomeFeature.RADAR]?.getOrNull()?.tracks?.takeIf { it.isNotEmpty() }
                    ?.let { Result.success(it) }
                    ?: QqMusicApi.getHomeFeature(QqMusicApi.HomeFeature.RADAR).map { it.tracks }
            } else {
                recommendState.dailySongs.takeIf { it.isNotEmpty() }?.let { Result.success(it) }
                    ?: NeteaseApi.getDailyRecommendSongs()
            }
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            if (platform != PlatformManager.current || generation != playbackGeneration) return@launch
            result.fold(onSuccess = { tracks ->
                if (tracks.isEmpty()) {
                    _toastMessage.trySend("暂无推荐歌曲，请稍后再试")
                    return@fold
                }
                val ready = kotlinx.coroutines.withTimeoutOrNull(10_000) {
                    while (mediaPlaybackService == null) kotlinx.coroutines.delay(100)
                    true
                } == true
                if (platform != PlatformManager.current || generation != playbackGeneration) return@fold
                if (ready) {
                    dailyShortcutJob = null
                    playTrackWithMode(tracks.first(), tracks)
                }
                else _toastMessage.trySend("播放器尚未就绪，请重试")
            }, onFailure = { error ->
                _toastMessage.trySend(error.message ?: "获取推荐歌曲失败，请稍后重试")
            })
        }
    }

    fun shufflePlaylist(playlist: List<Track>, fromAlbum: Boolean = false) {
        if (playlist.isEmpty()) return
        val first = playlist.random()
        if (com.hypermusic.miuix.data.download.OfflineLibrary.versions(first).size > 1) {
            com.hypermusic.miuix.data.download.Downloads.requestLocalVersion(first) { chosen ->
                val keptTrack = com.hypermusic.miuix.data.download.OfflineLibrary.localTrack(chosen)
                startShuffledTrack(keptTrack, playlist.map { if (it.source == first.source && it.id == first.id) keptTrack else it }
                    .distinctBy { it.source to it.id }, fromAlbum)
            }
            return
        }
        startShuffledTrack(first, playlist, fromAlbum)
    }

    private fun startShuffledTrack(first: Track, playlist: List<Track>, fromAlbum: Boolean) {
        if (playMode != 1) {
            playMode = 1
            prefs.edit().putInt("play_mode", playMode).apply()
        }
        playTrackWithMode(first, playlist, fromAlbum)
    }

    fun playTrackWithMode(track: Track, playlist: List<Track>, fromAlbum: Boolean = false) {
        if (com.hypermusic.miuix.data.download.OfflineLibrary.versions(track).size > 1) {
            com.hypermusic.miuix.data.download.Downloads.requestLocalVersion(track) { chosen ->
                val keptTrack = com.hypermusic.miuix.data.download.OfflineLibrary.localTrack(chosen)
                playTrackWithMode(keptTrack, playlist.map { if (it.source == track.source && it.id == track.id) keptTrack else it }
                    .distinctBy { it.source to it.id }, fromAlbum)
            }
            return
        }
        additionalRadioJob?.cancel()
        additionalRadioId = null
        albumPlayback = fromAlbum
        mediaPlaybackService?.setAlbumPlayback(fromAlbum)
        dailyShortcutJob?.cancel()
        dailyShortcutJob = null
        // 如果当前在FM模式或心动模式，退出
        if (isFMMode) {
            exitFMMode()
        }
        if (isIntelligenceMode) {
            exitIntelligenceMode()
        }
        if (playMode == 1) {
            // 乱序播放：打乱播放列表，保持当前歌曲在第一位
            originalPlaylist = playlist.toList()
            val shuffled = playlist.toMutableList()
            shuffled.shuffle()
            shuffled.remove(track)
            shuffled.add(0, track)
            currentPlaylist = shuffled
        } else {
            // 顺序播放或单曲循环：按原顺序加载
            currentPlaylist = playlist
        }
        playTrack(track)
    }

    fun enterFMMode() {
        Log.d("MusicPlayerViewModel", "enterFMMode called")
        if (isFMMode) {
            android.widget.Toast.makeText(
                getApplication(),
                "已经在私人FM模式中",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            return
        }
        // FM / 心动 互斥
        if (isIntelligenceMode) {
            exitIntelligenceMode()
        }
        albumPlayback = false
        mediaPlaybackService?.setAlbumPlayback(false)
        isFMMode = true
        fmIndex = 0
        // 保存FM模式状态
        prefs.edit().putString("play_mode_type", "fm").apply()
        val generation = playbackGeneration
        viewModelScope.launch {
            Log.d("MusicPlayerViewModel", "Loading FM songs...")
            NeteaseApi.getPersonalFM().fold(
                onSuccess = { tracks ->
                    if (generation != playbackGeneration) return@fold
                    Log.d("MusicPlayerViewModel", "FM songs loaded: ${tracks.size} tracks")
                    fmPlaylist = tracks
                    if (tracks.isNotEmpty()) {
                        currentPlaylist = tracks
                        playTrack(tracks[0])
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to load FM: ${e.message}")
                }
            )
        }
    }

    fun exitFMMode() {
        additionalRadioJob?.cancel()
        additionalRadioId = null
        fmModeLabel = "私人FM"
        isFMMode = false
        fmPlaylist = emptyList()
        fmIndex = 0
        // 清除FM模式状态
        prefs.edit().remove("play_mode_type").apply()
    }

    fun fmPlayNext() {
        if (additionalRadioId != null) { playAdditionalRadioNext(); return }
        fmIndex++
        // 如果播放到最后一首，加载更多
        if (fmIndex >= fmPlaylist.size) {
            val generation = playbackGeneration
            viewModelScope.launch {
                NeteaseApi.getPersonalFM().fold(
                    onSuccess = { tracks ->
                        if (generation != playbackGeneration) return@fold
                        fmPlaylist = fmPlaylist + tracks
                        if (fmIndex < fmPlaylist.size) {
                            playTrack(fmPlaylist[fmIndex])
                        }
                    },
                    onFailure = { e ->
                        Log.e("MusicPlayerViewModel", "Failed to load more FM: ${e.message}")
                    }
                )
            }
        } else {
            playTrack(fmPlaylist[fmIndex])
        }
    }

    fun fmPlayPrevious() {
        if (fmIndex > 0) {
            fmIndex--
            playTrack(fmPlaylist[fmIndex])
        }
    }

    /** Recommendation streams share FM's controls but retain their provider / scene on restore. */
    fun enterAdditionalRadio(id: String, onStarted: () -> Unit) {
        val platform = PlatformManager.current
        if (!id.startsWith("extra:${platform.storageKey}:")) return
        additionalRadioJob?.cancel()
        val modeLabel = com.hypermusic.miuix.data.model.radioModeLabel(id, additionalRecommendations.card(id)?.title)
        val openImmediately = platform == MusicPlatform.QISHUI
        if (openImmediately) {
            clearPlaybackForPlatformSwitch(clearSaved = false)
            additionalRadioId = id
            fmModeLabel = modeLabel
            isFMMode = true
            playerState = PlayerState(isLoading = true)
            lyricState = LyricState(isLoading = true, loadingStartedAt = android.os.SystemClock.elapsedRealtime())
            onStarted()
        }
        val generation = playbackGeneration
        val stamp = additionalRecommendations.accountStamp(platform)
        additionalRadioJob = viewModelScope.launch {
            try {
                val tracks = additionalRecommendations.radio(id, first = true)
                if (generation != playbackGeneration || platform != PlatformManager.current || stamp != additionalRecommendations.accountStamp(platform)) return@launch
                if (isIntelligenceMode) exitIntelligenceMode()
                // Do not call exitFMMode here: it would cancel this start request.
                albumPlayback = false; mediaPlaybackService?.setAlbumPlayback(false)
                additionalRadioId = id; isFMMode = true
                fmModeLabel = modeLabel
                fmPlaylist = tracks; fmIndex = 0; currentPlaylist = tracks
                playTrack(tracks.first())
                if (!openImmediately) onStarted()
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) {
                if (generation == playbackGeneration && platform == PlatformManager.current) {
                    if (openImmediately) {
                        playerState = playerState.copy(isLoading = false, error = e.message)
                        lyricState = lyricState.copy(isLoading = false)
                    }
                    _toastMessage.trySend(e.message ?: "电台加载失败")
                }
            }
        }
    }

    private fun playAdditionalRadioNext() {
        if (additionalRadioJob?.isActive == true) return
        val id = additionalRadioId ?: return
        if (fmIndex + 1 < fmPlaylist.size) {
            fmIndex++; playTrack(fmPlaylist[fmIndex]); return
        }
        val generation = playbackGeneration
        val platform = PlatformManager.current
        val stamp = additionalRecommendations.accountStamp(platform)
        additionalRadioJob = viewModelScope.launch {
            try {
                var next = additionalRecommendations.radio(id, first = false)
                val recent = fmPlaylist.takeLast(20).map { it.id }.toSet()
                val fresh = next.filterNot { it.id in recent }
                if (fresh.isNotEmpty()) next = fresh
                if (generation != playbackGeneration || platform != PlatformManager.current || id != additionalRadioId ||
                    stamp != additionalRecommendations.accountStamp(platform)) return@launch
                // Bound retained radio history while keeping previous-track navigation.
                fmPlaylist = fmPlaylist.takeLast(50) + next
                fmIndex = fmPlaylist.size - next.size
                currentPlaylist = fmPlaylist
                playTrack(fmPlaylist[fmIndex])
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _toastMessage.trySend(e.message ?: "电台续播失败，请重试") }
        }
    }

    /**
     * 解析「我喜欢的音乐」歌单 ID（与 SPlayer 的 likedPlaylistId 对齐：
     * 优先标题含「喜欢」的歌单，否则取用户歌单第一项）
     */
    fun resolveLikedPlaylistId(): String {
        val playlists = recommendState.userPlaylists
        val liked = playlists.firstOrNull { it.title.contains("喜欢") } ?: playlists.firstOrNull()
        return liked?.id ?: ""
    }

    private fun ensureUserPlaylistsLoaded() {
        if (recommendState.userPlaylists.isEmpty() && NeteaseApi.getCurrentUserId() != null) {
            loadUserPlaylists()
        }
    }

    /**
     * 进入心动模式
     * @param songId 种子歌曲 id（可空：自动取每日推荐首首 / 当前播放 / 我喜欢的音乐第一首）
     * @param playlistId 「我喜欢的音乐」歌单 id（可空：自动解析）
     */
    fun enterIntelligenceMode(songId: String, playlistId: String, onReady: () -> Unit = {}) {
        if (isIntelligenceMode) {
            android.widget.Toast.makeText(
                getApplication(),
                "已经在心动模式中",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            onReady()
            return
        }
        // 先进入心动模式状态并立刻回调（打开播放页），接口在后台加载
        albumPlayback = false
        mediaPlaybackService?.setAlbumPlayback(false)
        isIntelligenceMode = true
        if (isFMMode) exitFMMode()
        // 加载过程复用「等待歌曲」态：播放按钮转圈，进度条/上下首等禁用
        playerState = playerState.copy(isLoading = true, error = null)
        onReady()
        ensureUserPlaylistsLoaded()
        val generation = playbackGeneration
        viewModelScope.launch {
            Log.d("MusicPlayerViewModel", "Entering intelligence mode seed=$songId pid=$playlistId")
            val resolved = resolveHeartModeParams(songId, playlistId)
            if (generation != playbackGeneration) return@launch
            if (resolved == null) {
                Log.e("MusicPlayerViewModel", "Failed to resolve heart mode seed/playlist")
                isIntelligenceMode = false
                playerState = playerState.copy(isLoading = false)
                android.widget.Toast.makeText(
                    getApplication(),
                    "心动模式需要登录，且存在「我喜欢的音乐」歌单",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val (seedId, pid) = resolved
            NeteaseApi.getIntelligenceList(seedId, pid).fold(
                onSuccess = { tracks ->
                    if (generation != playbackGeneration) return@fold
                    Log.d("MusicPlayerViewModel", "Intelligence songs loaded: ${tracks.size} tracks")
                    if (tracks.isEmpty()) {
                        isIntelligenceMode = false
                        playerState = playerState.copy(isLoading = false)
                        android.widget.Toast.makeText(
                            getApplication(),
                            "心动模式开启失败：未获取到推荐歌曲",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                        return@fold
                    }
                    isIntelligenceMode = true
                    if (isFMMode) exitFMMode()
                    intelligenceIndex = 0
                    intelligencePlaylist = tracks
                    intelligencePlaylistId = pid
                    intelligenceSeedSongId = seedId
                    currentPlaylist = tracks
                    prefs.edit()
                        .putString("play_mode_type", "intelligence")
                        .putString("intelligence_song_id", seedId)
                        .putString("intelligence_playlist_id", pid)
                        .apply()
                    // playTrack 内部会继续走 isLoading 流程
                    playTrack(tracks[0])
                },
                onFailure = { e ->
                    if (generation != playbackGeneration) return@fold
                    Log.e("MusicPlayerViewModel", "Failed to load intelligence list: ${e.message}")
                    isIntelligenceMode = false
                    playerState = playerState.copy(isLoading = false)
                    android.widget.Toast.makeText(
                        getApplication(),
                        "心动模式开启失败：${e.message ?: "未知错误"}",
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    /**
     * 解析心动模式所需的种子歌曲 ID 与「我喜欢的音乐」歌单 ID
     * 种子优先取「我喜欢的音乐」里的歌（接口更稳），再退到入参/每日推荐/当前播放
     */
    private suspend fun resolveHeartModeParams(songId: String, playlistId: String): Pair<String, String>? {
        // 确保用户歌单已加载（心动模式必须有 pid）
        if (recommendState.userPlaylists.isEmpty()) {
            val userId = NeteaseApi.getCurrentUserId() ?: return null
            NeteaseApi.getUserPlaylists(userId).fold(
                onSuccess = { playlists ->
                    recommendState = recommendState.copy(userPlaylists = playlists)
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "resolveHeartModeParams load playlists failed: ${e.message}")
                }
            )
        }

        var pid = playlistId
        if (pid.isBlank()) {
            pid = resolveLikedPlaylistId()
        }
        if (pid.isBlank()) return null

        // 优先从「我喜欢的音乐」取种子
        var seed = ""
        NeteaseApi.getPlaylistDetail(pid).fold(
            onSuccess = { detail ->
                seed = detail.tracks.firstOrNull()?.id ?: ""
            },
            onFailure = { e ->
                Log.e("MusicPlayerViewModel", "resolveHeartModeParams load liked playlist failed: ${e.message}")
            }
        )

        // 入参种子
        if (seed.isBlank()) seed = songId
        // 每日推荐
        if (seed.isBlank()) seed = recommendState.dailySongs.firstOrNull()?.id ?: ""
        // 当前播放中的网易云歌曲
        if (seed.isBlank()) {
            val current = playerState.currentTrack
            if (current != null && current.id.startsWith("netrack_")) {
                seed = current.id
            }
        }
        if (seed.isBlank()) return null
        Log.d("MusicPlayerViewModel", "resolveHeartModeParams -> seed=$seed pid=$pid")
        return seed to pid
    }

    fun exitIntelligenceMode() {
        isIntelligenceMode = false
        intelligencePlaylist = emptyList()
        intelligenceIndex = 0
        intelligencePlaylistId = ""
        intelligenceSeedSongId = ""
        // 清除心动模式状态
        prefs.edit()
            .remove("play_mode_type")
            .remove("intelligence_song_id")
            .remove("intelligence_playlist_id")
            .apply()
    }

    fun intelligencePlayNext() {
        if (intelligencePlaylist.isEmpty()) return
        if (intelligenceIndex < intelligencePlaylist.size - 1) {
            intelligenceIndex++
            playTrack(intelligencePlaylist[intelligenceIndex])
            return
        }
        // 到末尾：以当前曲目为种子续拉下一批推荐
        val last = intelligencePlaylist.last()
        val pid = intelligencePlaylistId.ifBlank { resolveLikedPlaylistId() }
        if (pid.isBlank()) return
        viewModelScope.launch {
            NeteaseApi.getIntelligenceList(last.id, pid).fold(
                onSuccess = { more ->
                    val known = intelligencePlaylist.map { it.id }.toSet()
                    val fresh = more.filter { it.id !in known }
                    if (fresh.isNotEmpty()) {
                        intelligencePlaylist = intelligencePlaylist + fresh
                        intelligenceIndex++
                        playTrack(intelligencePlaylist[intelligenceIndex])
                    } else {
                        Log.d("MusicPlayerViewModel", "Intelligence mode: no more recommendations")
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Intelligence mode load more failed: ${e.message}")
                }
            )
        }
    }

    fun intelligencePlayPrevious() {
        if (intelligenceIndex > 0) {
            intelligenceIndex--
            playTrack(intelligencePlaylist[intelligenceIndex])
        }
    }

    private var currentPlaylistDetailId: String? = null
    private var playlistPageJob: Job? = null
    private var playlistPageGeneration = 0
    private var playlistPageKey: String? = null

    fun showQqHomeFeature(detail: PlaylistDetail) {
        playlistPageGeneration++
        playlistPageJob?.cancel()
        playlistPageKey = PageCache.key(getApplication(), MusicPlatform.QQ, "playlist", detail.info.id)
        currentPlaylistDetailId = detail.info.id
        playlistDetailState = PlaylistDetailState(detail = detail)
        val key = requireNotNull(playlistPageKey)
        viewModelScope.launch { PageCache.write(getApplication(), key, detail) }
    }

    suspend fun refreshPlaylistDetail() {
        currentPlaylistDetailId?.let { loadPlaylistDetail(it, refresh = true).join() }
    }

    fun loadPlaylistDetail(playlistId: String, refresh: Boolean = false): Job {
        val pageKey = PageCache.key(getApplication(), PlatformManager.current, "playlist", playlistId)
        val previousDetail = if (playlistPageKey == pageKey) playlistDetailState.detail else null
        playlistPageKey = pageKey
        val pageGeneration = ++playlistPageGeneration
        playlistPageJob?.cancel()
        currentPlaylistDetailId = playlistId
        playlistDetailState = PlaylistDetailState(detail = previousDetail, isLoading = true)
        val requestedPlatform = PlatformManager.current
        val qqAccount = QqMusicApi.dataAccountId
        val neteaseAccount = NeteaseApi.getCurrentUserId()
        val feature = if (requestedPlatform == MusicPlatform.QQ && playlistId.startsWith("qqfeature:"))
            QqMusicApi.HomeFeature.entries.firstOrNull { it.name == playlistId.removePrefix("qqfeature:") } else null
        val featureRevision = if (refresh && feature != null) {
            ((qqFeatureRevisions[feature] ?: 0) + 1).also { qqFeatureRevisions[feature] = it }
        } else null
        val dailyRevision = if (refresh && requestedPlatform == MusicPlatform.NETEASE && playlistId == "daily_recommend") ++neteaseDailyRevision else null
        return viewModelScope.launch {
            val cached = previousDetail ?: PageCache.read<PlaylistDetail>(getApplication(), pageKey)
            if (pageGeneration != playlistPageGeneration) return@launch
            playlistDetailState = PlaylistDetailState(detail = cached, isLoading = true)
            try {
            if (playlistId.startsWith("extra:")) {
                val stamp = additionalRecommendations.accountStamp(requestedPlatform)
                try {
                    require(playlistId.startsWith("extra:${requestedPlatform.storageKey}:")) { "推荐平台不匹配" }
                    val card = additionalRecommendations.collection(playlistId, refresh)
                    if (PlatformManager.current != requestedPlatform || currentPlaylistDetailId != playlistId ||
                        stamp != additionalRecommendations.accountStamp(requestedPlatform)) return@launch
                    playlistDetailState = PlaylistDetailState(detail = PlaylistDetail(
                        Playlist(card.id, card.title, card.cover, "", card.tracks.size, card.subtitle), card.tracks))
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    if (PlatformManager.current == requestedPlatform && currentPlaylistDetailId == playlistId)
                        playlistDetailState = PlaylistDetailState(error = e.message ?: "推荐加载失败")
                }
                return@launch
            }
            if (requestedPlatform == MusicPlatform.BODIAN || requestedPlatform == MusicPlatform.QISHUI) {
                try {
                    bodianPlaylistCollected = null
                    bodianPlaylistAccountRevision = null
                    val tracks = mutableListOf<Track>()
                    var offset = 0
                    var more = true
                    var title = ""; var cover = ""; var description = ""; var total = 0
                    if (requestedPlatform == MusicPlatform.BODIAN) {
                        val parts = playlistId.split(':')
                        require(parts.size == 3 && parts[0] == "bodian") { "波点音乐歌单标识无效" }
                        val source = parts[1].toIntOrNull() ?: error("波点音乐歌单来源无效")
                        val api = com.hypermusic.miuix.data.api.BodianCatalog(com.hypermusic.miuix.data.api.BodianClient(getApplication()))
                        val info = api.playlistDetail(parts[2], source)
                        fun field(key: String) = info.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                        title = field("name"); cover = field("pic"); description = field("info").ifBlank { field("description") }
                        while (more && offset < 20_000) {
                            val page = api.playlistSongs(parts[2], source, offset, 100)
                            if (PlatformManager.current != requestedPlatform || currentPlaylistDetailId != playlistId) return@launch
                            tracks += page.items; total = page.total ?: tracks.size; more = page.hasMore
                            check(!more || page.nextOffset > offset) { "波点音乐歌单分页未前进" }; offset = page.nextOffset
                        }
                    } else {
                        val parts = playlistId.split(':')
                        require(parts.size == 3 && parts[0] == "qishui") { "汽水音乐歌单标识无效" }
                        val api = com.hypermusic.miuix.data.api.QishuiCatalog(com.hypermusic.miuix.data.api.QishuiClient(getApplication()))
                        val page = api.playlistSongs(parts[1], parts[2], limit = 20_000)
                        if (PlatformManager.current != requestedPlatform || currentPlaylistDetailId != playlistId) return@launch
                        page.playlist?.let { info ->
                            val base = info.getAsJsonObject("base_info")
                            val entity = com.hypermusic.miuix.data.api.QishuiCatalog.playlistEntity(info)
                            title = entity?.title.orEmpty()
                            description = base?.get("desc")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
                            total = entity?.songCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: total
                            cover = entity?.imageUrl.orEmpty()
                        }
                        tracks += page.songs.items; more = page.songs.hasMore
                    }
                    check(!more) { "歌单过长，未能完整加载" }
                    cover = cover.replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kuwo\\.cn/)"), "https://")
                    playlistDetailState = PlaylistDetailState(detail = PlaylistDetail(Playlist(playlistId, title, cover, "", maxOf(total, tracks.size), description), tracks))
                    if (requestedPlatform == MusicPlatform.BODIAN) {
                        val api = com.hypermusic.miuix.data.api.BodianClient(getApplication())
                        val auth = com.hypermusic.miuix.data.api.BodianAuth(getApplication(), api)
                        val revision = auth.sessionRevision()
                        val parsed = bodianPlaylistId(playlistId)
                        val collected = if (auth.currentAccount() == null) false else if (parsed == null) null else try {
                            com.hypermusic.miuix.data.api.BodianLibrary(api, auth, com.hypermusic.miuix.data.api.BodianCatalog(api)).isCollected(parsed.second, album = false)
                        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                        catch (_: Exception) { null }
                        if (PlatformManager.current == requestedPlatform && currentPlaylistDetailId == playlistId && auth.sessionRevision() == revision) {
                            bodianPlaylistAccountRevision = revision
                            bodianPlaylistCollected = collected
                        }
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    if (PlatformManager.current == requestedPlatform && currentPlaylistDetailId == playlistId)
                        playlistDetailState = PlaylistDetailState(error = e.message ?: "歌单加载失败")
                }
                return@launch
            }
            if (requestedPlatform == MusicPlatform.KUGOU) {
                try {
                    kugouPlaylistCollected = null
                    kugouPlaylistAccountRevision = null
                    val catalog = com.hypermusic.miuix.data.api.KugouCatalog(com.hypermusic.miuix.data.api.KugouClient(getApplication()))
                    var offset = 0
                    val tracks = mutableListOf<Track>()
                    var first: com.hypermusic.miuix.data.api.KugouCatalog.PlaylistPage? = null
                    var more = true
                    while (more && offset < 20_000) {
                        val page = catalog.playlist(playlistId, offset, 100)
                        if (PlatformManager.current != requestedPlatform || currentPlaylistDetailId != playlistId) return@launch
                        if (first == null) first = page
                        tracks.addAll(page.songs.items)
                        more = page.songs.hasMore
                        check(!more || page.songs.nextOffset > offset) { "酷狗音乐歌单分页未前进" }
                        offset = page.songs.nextOffset
                    }
                    check(!more) { "酷狗音乐歌单过长，未能完整加载" }
                    val page = requireNotNull(first)
                    playlistDetailState = PlaylistDetailState(detail = PlaylistDetail(
                        com.hypermusic.miuix.data.api.KugouCatalog.playlistInfo(playlistId, page), tracks))
                    val client = com.hypermusic.miuix.data.api.KugouClient(getApplication())
                    val auth = com.hypermusic.miuix.data.api.KugouAuth(getApplication(), client)
                    val revision = auth.sessionGeneration()
                    val collected = if (auth.account() == null) false else try {
                        com.hypermusic.miuix.data.api.KugouLibrary(client, auth).isPlaylistCollected(playlistId)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (_: Exception) { null }
                    if (PlatformManager.current == requestedPlatform && currentPlaylistDetailId == playlistId && auth.sessionGeneration() == revision) {
                        kugouPlaylistAccountRevision = revision
                        kugouPlaylistCollected = collected
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    if (PlatformManager.current == requestedPlatform && currentPlaylistDetailId == playlistId)
                        playlistDetailState = PlaylistDetailState(error = e.message ?: "酷狗音乐歌单加载失败")
                }
                return@launch
            }
            if (requestedPlatform != MusicPlatform.NETEASE && requestedPlatform != MusicPlatform.QQ) {
                playlistDetailState = PlaylistDetailState(error = "该平台的歌单页正在接入")
                return@launch
            }
            if (PlatformManager.current == MusicPlatform.QQ) {
                val result = if (playlistId.startsWith("qqfeature:")) {
                    if (feature != null) QqMusicApi.getHomeFeature(feature) else Result.failure(IllegalArgumentException("推荐标识无效"))
                } else if (playlistId.startsWith("qqtop:")) {
                    val id = playlistId.removePrefix("qqtop:").toIntOrNull()
                    if (id != null) QqMusicApi.getTopChartDetail(id) else Result.failure(IllegalArgumentException("榜单标识无效"))
                } else QqMusicApi.getPlaylistDetail(playlistId)
                if (PlatformManager.current != requestedPlatform || QqMusicApi.dataAccountId != qqAccount || currentPlaylistDetailId != playlistId) return@launch
                if (featureRevision != null && featureRevision != qqFeatureRevisions[feature]) return@launch
                result.fold(
                    onSuccess = { detail ->
                        playlistDetailState = PlaylistDetailState(detail = detail)
                        if (refresh && feature != null && featureRevision != null && featureRevision == qqFeatureRevisions[feature]) {
                            qqFeatureRevisions[feature] = featureRevision + 1
                            qqHomeFeatures = qqHomeFeatures + (feature to Result.success(detail))
                        }
                    },
                    onFailure = { playlistDetailState = PlaylistDetailState(error = it.message) }
                )
                return@launch
            }
            // 每日推荐歌单特殊处理
            if (playlistId == "daily_recommend") {
                val dailyResult = NeteaseApi.getDailyRecommendSongs()
                if (PlatformManager.current != requestedPlatform || NeteaseApi.getCurrentUserId() != neteaseAccount ||
                    currentPlaylistDetailId != playlistId || (dailyRevision != null && dailyRevision != neteaseDailyRevision)) return@launch
                if (dailyResult.isFailure) {
                    playlistDetailState = PlaylistDetailState(error = dailyResult.exceptionOrNull()?.message ?: "每日推荐加载失败")
                    return@launch
                }
                val dailySongs = dailyResult.getOrThrow()
                recommendState = recommendState.copy(dailySongs = dailySongs)
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
            // 云盘歌单特殊处理：首页立即展示，随后后台把剩余分页全部拉完
            else if (playlistId == "cloud_disk") {
                cloudDiskLoadJob?.cancel()
                cloudDiskOffset = 0
                cloudDiskHasMore = true
                cloudDiskTotalCount = 0
                NeteaseApi.getCloudDiskSongs(offset = 0, limit = cloudDiskLimit).fold(
                    onSuccess = { result ->
                        cloudDiskOffset = result.rawFetched
                        // 不要只信 hasMore：count / 满页也要继续
                        cloudDiskHasMore = result.hasMore ||
                            (result.count > 0 && result.rawFetched < result.count) ||
                            result.rawFetched >= cloudDiskLimit
                        cloudDiskTotalCount = if (result.count > 0) result.count else result.tracks.size
                        // 使用第一首歌的封面作为歌单封面
                        val coverUrl = result.tracks.firstOrNull()?.imgUrl ?: ""
                        if (coverUrl.isNotEmpty()) {
                            recommendState = recommendState.copy(cloudDiskCoverUrl = coverUrl)
                        }
                        val displayCount = maxOf(cloudDiskTotalCount, result.tracks.size)
                        playlistDetailState = playlistDetailState.copy(
                            detail = PlaylistDetail(
                                info = Playlist(
                                    id = "cloud_disk",
                                    title = "云盘",
                                    coverImgUrl = coverUrl,
                                    sourceUrl = "",
                                    trackCount = displayCount,
                                    description = ""
                                ),
                                tracks = result.tracks
                            ),
                            isLoading = false
                        )
                        // 只要可能还有剩余就后台续拉（不依赖服务端 hasMore 是否正确）
                        cloudDiskLoadJob = viewModelScope.launch {
                            loadRemainingCloudDiskSongs()
                        }
                    },
                    onFailure = { e ->
                        playlistDetailState = playlistDetailState.copy(
                            isLoading = false,
                            error = e.message ?: "Unknown error"
                        )
                    }
                )
            } else {
                cloudDiskLoadJob?.cancel()
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
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { playlistDetailState = playlistDetailState.copy(isLoading = false, error = e.message) }
            finally {
                if (pageGeneration == playlistPageGeneration && pageKey == PageCache.key(getApplication(), requestedPlatform, "playlist", playlistId)) {
                    val fresh = playlistDetailState.detail
                    val shown = fresh?.copy(tracks = reconcilePageItems(cached?.tracks.orEmpty(), fresh.tracks) { it.source to it.id }) ?: cached
                    playlistDetailState = playlistDetailState.copy(detail = shown, isLoading = false)
                    if (fresh != null && playlistDetailState.error == null) PageCache.write(getApplication(), pageKey, fresh)
                    if (refresh && requestedPlatform == MusicPlatform.NETEASE && playlistId == "daily_recommend" && shown != null && playlistDetailState.error == null)
                        PageCache.write(getApplication(), PageCache.key(getApplication(), requestedPlatform, "home", "daily"), shown.tracks)
                    if (refresh && feature != null && playlistDetailState.error == null)
                        PageCache.write(getApplication(), PageCache.key(getApplication(), MusicPlatform.QQ, "home"), CachedQqHome(qqHomePlaylists, qqHomeCharts,
                            qqHomeFeatures.mapNotNull { (name, result) -> result.getOrNull()?.let { name.name to it } }.toMap()))
                }
            }
        }.also { playlistPageJob = it }
    }

    // 云盘分段加载相关变量
    private var cloudDiskOffset = 0
    private var cloudDiskHasMore = true
    private var cloudDiskTotalCount = 0
    private var cloudDiskLoadJob: Job? = null
    private val cloudDiskLimit = 30
    var isCloudDiskLoadingMore by mutableStateOf(false)
        private set

    /**
     * 后台拉取云盘剩余分页。
     * 终止条件不要只看 hasMore：满页 / count 未拉完都继续，直到拉空或安全上限。
     */
    private suspend fun loadRemainingCloudDiskSongs() {
        var guard = 0
        isCloudDiskLoadingMore = true
        try {
            while (guard < 80) {
                // 用户已离开云盘页则停止
                if (playlistDetailState.detail?.info?.id != "cloud_disk") break
                guard++
                val offset = cloudDiskOffset
                val page = NeteaseApi.getCloudDiskSongs(offset = offset, limit = cloudDiskLimit)
                if (page.isFailure) {
                    Log.e("MusicPlayerViewModel", "loadRemainingCloudDiskSongs failed at offset=$offset: ${page.exceptionOrNull()?.message}")
                    cloudDiskHasMore = false
                    break
                }
                val result = page.getOrNull() ?: break
                if (result.rawFetched <= 0 && result.tracks.isEmpty()) {
                    cloudDiskHasMore = false
                    break
                }
                cloudDiskOffset += result.rawFetched
                if (result.count > 0) cloudDiskTotalCount = result.count
                appendCloudDiskTracks(result.tracks)

                val countSaysMore = cloudDiskTotalCount > 0 && cloudDiskOffset < cloudDiskTotalCount
                val pageWasFull = result.rawFetched >= cloudDiskLimit
                val serverSaysMore = result.hasMore
                cloudDiskHasMore = serverSaysMore || countSaysMore || pageWasFull
                Log.d(
                    "MusicPlayerViewModel",
                    "cloud page offset=$cloudDiskOffset total=$cloudDiskTotalCount raw=${result.rawFetched} hasMore=$cloudDiskHasMore"
                )
                if (!cloudDiskHasMore) break
            }
        } finally {
            isCloudDiskLoadingMore = false
        }
    }

    private fun appendCloudDiskTracks(newTracks: List<Track>) {
        if (newTracks.isEmpty()) return
        val detail = playlistDetailState.detail ?: return
        if (detail.info.id != "cloud_disk") return
        val existingIds = detail.tracks.map { it.id }.toSet()
        val merged = detail.tracks + newTracks.filter { it.id !in existingIds }
        val displayCount = maxOf(cloudDiskTotalCount, merged.size)
        playlistDetailState = playlistDetailState.copy(
            detail = detail.copy(
                info = detail.info.copy(trackCount = displayCount),
                tracks = merged
            )
        )
        // 若当前正在播放云盘里的歌，同步更新播放队列
        val currentId = playerState.currentTrack?.id
        if (currentId != null && merged.any { it.id == currentId }) {
            currentPlaylist = merged
        }
    }

    fun loadMoreCloudDiskSongs() {
        if (!cloudDiskHasMore || isCloudDiskLoadingMore) return
        if (playlistDetailState.detail?.info?.id != "cloud_disk") return
        cloudDiskLoadJob?.cancel()
        cloudDiskLoadJob = viewModelScope.launch {
            isCloudDiskLoadingMore = true
            try {
                NeteaseApi.getCloudDiskSongs(offset = cloudDiskOffset, limit = cloudDiskLimit).fold(
                    onSuccess = { result ->
                        cloudDiskOffset += result.rawFetched
                        cloudDiskHasMore = result.hasMore
                        if (result.count > 0) cloudDiskTotalCount = result.count
                        appendCloudDiskTracks(result.tracks)
                        // UI 触发的加载更多若仍有剩余，继续后台拉完
                        if (cloudDiskHasMore) {
                            loadRemainingCloudDiskSongs()
                        }
                    },
                    onFailure = { e ->
                        Log.e("MusicPlayerViewModel", "loadMoreCloudDiskSongs failed: ${e.message}")
                    }
                )
            } finally {
                isCloudDiskLoadingMore = false
            }
        }
    }

    fun clearPlaylistDetail() {
        cloudDiskLoadJob?.cancel()
        cloudDiskLoadJob = null
        isCloudDiskLoadingMore = false
        playlistDetailState = PlaylistDetailState()
    }

    private var searchJob: Job? = null
    private var searchNextOffset = 0
    private var searchKeyword = ""
    private var searchPlatform: MusicPlatform? = null
    private data class SearchPageData(val tracks: List<Track>, val next: Int, val more: Boolean)

    private suspend fun searchNewPlatform(platform: MusicPlatform, keyword: String, offset: Int): SearchPageData = when (platform) {
        MusicPlatform.BODIAN -> com.hypermusic.miuix.data.api.BodianCatalog(com.hypermusic.miuix.data.api.BodianClient(getApplication())).searchSongs(keyword, offset).let { SearchPageData(it.items, it.nextOffset, it.hasMore) }
        MusicPlatform.KUGOU -> com.hypermusic.miuix.data.api.KugouCatalog(com.hypermusic.miuix.data.api.KugouClient(getApplication())).searchSongs(keyword, offset).let { SearchPageData(it.items, it.nextOffset, it.hasMore) }
        MusicPlatform.QISHUI -> com.hypermusic.miuix.data.api.QishuiCatalog(com.hypermusic.miuix.data.api.QishuiClient(getApplication())).searchSongs(keyword, offset).let { SearchPageData(it.items, it.nextOffset, it.hasMore) }
        else -> error("该平台暂不支持分页搜索")
    }

    fun searchSongs(keyword: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            searchState = searchState.copy(isLoading = true, error = null)
            val platform = PlatformManager.current
            searchKeyword = keyword
            searchPlatform = platform
            val result: Result<SearchPageData> = try {
                when (platform) {
                    MusicPlatform.QQ -> QqMusicApi.searchSongs(keyword).map { SearchPageData(it, 0, false) }
                    MusicPlatform.NETEASE -> NeteaseApi.searchSongs(keyword).map { SearchPageData(it, 0, false) }
                    else -> Result.success(searchNewPlatform(platform, keyword, 0))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { Result.failure(e) }
            if (platform != PlatformManager.current) return@launch
            result.fold(
                onSuccess = { page ->
                    searchNextOffset = page.next
                    searchState = searchState.copy(
                        results = page.tracks,
                        isLoading = false,
                        hasMore = page.more && page.next > 0
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

    fun loadMoreSearchSongs() {
        val platform = searchPlatform ?: return
        if (!searchState.hasMore || searchState.isLoading || searchState.isLoadingMore || platform != PlatformManager.current) return
        val offset = searchNextOffset
        val keyword = searchKeyword
        searchState = searchState.copy(isLoadingMore = true, loadMoreError = null)
        searchJob = viewModelScope.launch {
            try {
                val page = searchNewPlatform(platform, keyword, offset)
                if (platform != PlatformManager.current || keyword != searchKeyword || offset != searchNextOffset) return@launch
                searchNextOffset = page.next
                searchState = searchState.copy(results = (searchState.results + page.tracks).distinctBy { it.source to it.id },
                    isLoadingMore = false, hasMore = page.more && page.next > offset)
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { searchState = searchState.copy(isLoadingMore = false, loadMoreError = e.message ?: "加载失败") }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        searchJob = null
        searchNextOffset = 0
        searchKeyword = ""
        searchPlatform = null
        searchState = SearchState()
    }

    fun playTrack(track: Track) {
        if (!trackBelongsToCurrentPlatform(track)) return
        if (com.hypermusic.miuix.data.download.OfflineLibrary.versions(track).size > 1) {
            com.hypermusic.miuix.data.download.Downloads.requestLocalVersion(track) { chosen ->
                val keptTrack = com.hypermusic.miuix.data.download.OfflineLibrary.localTrack(chosen)
                currentPlaylist = currentPlaylist.map { if (it.source == track.source && it.id == track.id) keptTrack else it }.distinctBy { it.source to it.id }
                originalPlaylist = originalPlaylist.map { if (it.source == track.source && it.id == track.id) keptTrack else it }.distinctBy { it.source to it.id }
                playTrack(keptTrack)
            }
            return
        }
        activeCrossPlayback = null
        val requestId = ++playTrackRequestId
        val generation = playbackGeneration
        val downloaded = if (SharedPlaybackAccounts.isCrossPlatform(getApplication(), track)) null
            else com.hypermusic.miuix.data.download.OfflineLibrary.find(track)
        if (downloaded != null) {
            val service = mediaPlaybackService
            if (service == null) { _toastMessage.trySend("播放器尚未就绪"); return }
            playerState = playerState.copy(currentTrack = track, currentPosition = 0, isPlaying = true,
                isLoading = false, error = null, bitrate = downloaded.bitrate, audioQuality = downloaded.quality, lyricOffsetMs = 0)
            saveLastPlayedTrack(track); saveLastPlaylist()
            savePlatformPlayback(PlatformManager.current, positionOverride = 0)
            service.setPlaylist(currentPlaylist)
            service.playTrackWithUrl(track, java.io.File(downloaded.path).toURI().toString(), "download:${downloaded.id}")
            isCurrentTrackLiked = false
            loadLyrics()
            return
        }
        if (!track.localDownloadId.isNullOrBlank()) {
            playerState = playerState.copy(isPlaying = false, isLoading = false, error = "本地歌曲文件已删除")
            _toastMessage.trySend("本地歌曲文件已删除")
            return
        }
        if (SharedPlaybackAccounts.isCrossPlatform(getApplication(), track)) {
            if (mediaPlaybackService?.isPlaying() == true) mediaPlaybackService?.togglePlayPause()
            playerState = playerState.copy(isLoading = true, isPlaying = false, error = null, currentTrack = track,
                currentPosition = 0, bitrate = 0, audioQuality = "", lyricOffsetMs = 0)
            saveLastPlayedTrack(track); saveLastPlaylist()
            savePlatformPlayback(PlatformManager.current, positionOverride = 0)
            isCurrentTrackLiked = false
            loadLyrics(); checkIfCurrentTrackLiked()
            prepareCrossPlayback(track, 0L, resume = true, restore = false)
            return
        }
        if (track.source in setOf("bodian", "kugou", "qishui")) {
            viewModelScope.launch {
                if (generation != playbackGeneration || requestId != playTrackRequestId) return@launch
                playerState = playerState.copy(isLoading = true, error = null, currentTrack = track, currentPosition = 0, bitrate = 0, audioQuality = "", lyricOffsetMs = 0)
                saveLastPlayedTrack(track); saveLastPlaylist()
                savePlatformPlayback(PlatformManager.current, positionOverride = 0)
                isCurrentTrackLiked = false
                loadLyrics(); checkIfCurrentTrackLiked()
                try {
                    val account = playbackAccountFor(track)
                    val requested = requestedQualityFor(track)
                    val cached = completeCachedAudio(track, account, requested)
                    val audio = if (cached == null) resolveNewSourceAudio(track, requested) else null
                    if (generation != playbackGeneration || requestId != playTrackRequestId || playerState.currentTrack != track || playbackAccountFor(track) != account) return@launch
                    val service = mediaPlaybackService ?: error("播放器尚未就绪")
                    service.setPlaylist(currentPlaylist)
                    if (cached != null) {
                        service.playTrackWithUrl(track, "https://127.0.0.1/cached", cached.format)
                        playerState = playerState.copy(isPlaying = true, isLoading = false, bitrate = cached.bitrate, audioQuality = cached.quality)
                    } else {
                        val resolved = requireNotNull(audio)
                        val format = "${account ?: "guest"}:$requested:${resolved.quality}:${resolved.bitrate}"
                        if (!resolved.preview) rememberCachedFormat(track, account, requested, format, resolved.bitrate, resolved.quality)
                        service.playTrackWithUrl(track, resolved.url, format, resolved.preview)
                        playerState = playerState.copy(isPlaying = true, isLoading = false, bitrate = resolved.bitrate, audioQuality = resolved.quality, lyricOffsetMs = resolved.lyricOffsetMs)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) {
                    if (generation == playbackGeneration && requestId == playTrackRequestId && playerState.currentTrack == track) {
                        playerState = playerState.copy(isLoading = false, error = e.message)
                        _toastMessage.trySend(e.message ?: "播放失败")
                    }
                }
            }
            return
        }
        if (track.source == "qqmusic") {
            viewModelScope.launch {
                if (generation != playbackGeneration) return@launch
                playerState = playerState.copy(isLoading = true, error = null, currentTrack = track, currentPosition = 0, bitrate = 0, audioQuality = "", lyricOffsetMs = 0)
                saveLastPlayedTrack(track)
                saveLastPlaylist()
                savePlatformPlayback(PlatformManager.current, positionOverride = 0)
                isCurrentTrackLiked = false
                checkIfCurrentTrackLiked()
                loadLyrics()
                val cacheAccount = playbackAccountFor(track)
                val cacheRequest = requestedQualityFor(track)
                val cached = completeCachedAudio(track, cacheAccount, cacheRequest)
                if (generation != playbackGeneration || playerState.currentTrack != track) return@launch
                if (cached != null) {
                    mediaPlaybackService?.let { service ->
                        service.setPlaylist(currentPlaylist)
                        service.playTrackWithUrl(track, "https://127.0.0.1/cached", cached.format)
                        playerState = playerState.copy(isPlaying = true, isLoading = false, bitrate = cached.bitrate, audioQuality = cached.quality)
                    }
                    return@launch
                }
                QqMusicApi.getSongAudio(track.id, track.platform.qqMediaMid, cacheRequest).fold(
                    onSuccess = { audio ->
                        if (generation != playbackGeneration || playerState.currentTrack != track) return@fold
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            val format = "${cacheAccount ?: "guest"}:${audio.quality}:${audio.bitrate}:${audio.extension}"
                            rememberCachedFormat(track, cacheAccount, cacheRequest, format, audio.bitrate, audio.quality)
                            service.playTrackWithUrl(track, audio.url, format)
                            playerState = playerState.copy(isPlaying = true, isLoading = false, bitrate = audio.bitrate, audioQuality = audio.quality)
                        } ?: run { playerState = playerState.copy(isLoading = false, error = "播放器尚未就绪") }
                    },
                    onFailure = { e ->
                        if (generation != playbackGeneration || playerState.currentTrack != track) return@fold
                        playerState = playerState.copy(isLoading = false, error = e.message)
                        _toastMessage.trySend(e.message ?: "QQ音乐播放失败")
                    }
                )
            }
            return
        }
        viewModelScope.launch {
            val cacheAccount = playbackAccountFor(track)
            val cacheRequest = requestedQualityFor(track)
            val cached = completeCachedAudio(track, cacheAccount, cacheRequest)
            if (generation != playbackGeneration || requestId != playTrackRequestId) return@launch
            if (track.isVip && cached == null && (!NeteaseApi.isLoggedIn() || playbackVipType == 0)) {
                _toastMessage.trySend("非VIP用户无法播放VIP歌曲")
                return@launch
            }
            playerState = playerState.copy(isLoading = true, error = null, currentTrack = track, currentPosition = 0, bitrate = 0, audioQuality = "", lyricOffsetMs = 0)
            saveLastPlayedTrack(track)
            saveLastPlaylist()
            savePlatformPlayback(PlatformManager.current, positionOverride = 0)

            // 第一阶段：立即用元数据（无歌词）更新 Lyricon
            loadLyrics()
            checkIfCurrentTrackLiked()

            // 云盘歌曲属于「获取数据」账号，必须用该账号 Cookie 获取播放地址；
            // 其他歌曲使用「播放歌曲」账号，且不切换全局账号影响其他请求
            val accounts = NeteaseApi.getAccounts()
            val accountIndex = if (track.platform.isCloudDisk) dataAccountIndex else playbackAccountIndex
            val accountUserId = accounts.getOrNull(accountIndex)?.userId
            val accountCookie = accountUserId?.let { NeteaseApi.getAccountCookie(it) }

            if (generation != playbackGeneration || playerState.currentTrack != track) return@launch
            if (cached != null) {
                mediaPlaybackService?.let { service ->
                    service.setPlaylist(currentPlaylist)
                    service.playTrackWithUrl(track, "https://127.0.0.1/cached", cached.format)
                    playerState = playerState.copy(isPlaying = true, isLoading = false, bitrate = cached.bitrate, audioQuality = cached.quality)
                }
                return@launch
            }
            val selectedBitrate = AudioQuality.neteaseBitrate(cacheRequest)

            NeteaseApi.getSongUrl(track.id, selectedBitrate, accountCookie = accountCookie).fold(
                onSuccess = { (url, bitrate, type) ->
                    if (generation != playbackGeneration || playerState.currentTrack != track) return@fold
                    try {
                        mediaPlaybackService?.let { service ->
                            service.setPlaylist(currentPlaylist)
                            val format = "${cacheAccount ?: "guest"}:$bitrate:$type"
                            rememberCachedFormat(track, cacheAccount, cacheRequest, format, bitrate, AudioQuality.neteaseActual(bitrate, type))
                            service.playTrackWithUrl(track, url, format)
                            playerState = playerState.copy(
                                isPlaying = true,
                                isLoading = false,
                                bitrate = bitrate,
                                audioQuality = AudioQuality.neteaseActual(bitrate, type)
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
                    if (generation != playbackGeneration || playerState.currentTrack != track) return@fold
                    playerState = playerState.copy(
                        isLoading = false,
                        error = e.message
                    )
                }
            )
        }
    }

    /** 按最新歌词设置重新同步当前歌词到词幕。 */
    fun resyncLyricsToLyricon() {
        lyricState.lyrics?.let { mediaPlaybackService?.updateLyrics(it) }
    }

    /** 设置播放速度，限制在 0.5x - 3.0x，精度为 0.1x。 */
    fun updatePlaybackSpeed(speed: Float) {
        val normalized = (speed.coerceIn(0.5f, 3f) * 10f).roundToInt() / 10f
        if (normalized == playbackSpeed) return
        playbackSpeed = normalized
        prefs.edit().putFloat("playback_speed", normalized).apply()
        mediaPlaybackService?.setPlaybackSpeed(normalized)
    }

    fun updatePlatformAudioQualityIndex(platform: MusicPlatform, index: Int) {
        val selected = index.coerceIn(AudioQuality.options(platform).indices)
        platformQualityIndices = platformQualityIndices + (platform to selected)
        prefs.edit().putInt(com.hypermusic.miuix.data.model.PlaybackQualityPreferences.key(platform), selected).apply()
    }
    fun updateAudioQualityIndex(index: Int) = updatePlatformAudioQualityIndex(PlatformManager.current, index)
    fun togglePlayPause() {
        mediaPlaybackService?.let { service ->
            if (service.mediaNotificationDismissed || prefs.getBoolean(MediaPlaybackService.PREF_MEDIA_DISMISSED, false)) {
                playerState.currentTrack?.let { track ->
                    playerState = playerState.copy(isLoading = true, error = null)
                    prepareTrack(track, playerState.currentPosition, resume = true)
                }
                return
            }
            service.togglePlayPause()
            // 查询实际的播放状态，而不是简单反转
            val actualIsPlaying = service.isPlaying()
            playerState = playerState.copy(isPlaying = actualIsPlaying)
            
            // 开始播放时，确保歌词已同步到Lyricon
            if (actualIsPlaying) {
                lyricState.lyrics?.let { lyrics ->
                    service.updateLyrics(lyrics)
                    Log.d("MusicPlayerViewModel", "Synced lyrics on play: ${lyrics.lines.size} lines")
                }
            }
        }
    }

    fun seekTo(position: Long) {
        mediaPlaybackService?.seekTo(position)
        playerState = playerState.copy(currentPosition = position)
    }

    /** 供歌词引擎逐帧读取的实时播放位置（毫秒）。 */
    fun getPlaybackPositionMs(): Long {
        return (mediaPlaybackService?.getCurrentPosition() ?: playerState.currentPosition) + playerState.lyricOffsetMs
    }

    fun playNext() {
        if (isFMMode) { fmPlayNext(); return }
        val current = playerState.currentTrack ?: return
        val index = currentPlaylist.indexOfFirst { it.id == current.id }
        if (index >= 0 && index < currentPlaylist.size - 1) {
            playTrack(currentPlaylist[index + 1])
        }
    }

    fun playPrevious() {
        if (isFMMode) { fmPlayPrevious(); return }
        val current = playerState.currentTrack ?: return
        val index = currentPlaylist.indexOfFirst { it.id == current.id }
        if (index > 0) {
            playTrack(currentPlaylist[index - 1])
        }
    }

    fun downloadCurrentTrack(track: Track? = playerState.currentTrack) {
        track ?: return
        val accountId = if (track.source == "qqmusic") QqMusicApi.playbackAccountId else {
            val index = if (track.platform.isCloudDisk) dataAccountIndex else playbackAccountIndex
            NeteaseApi.getAccounts().getOrNull(index)?.userId
        }
        viewModelScope.launch {
            try { com.hypermusic.miuix.data.download.Downloads.enqueue(getApplication(), track, accountId) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { _toastMessage.trySend(e.message ?: "无法创建下载任务") }
        }
    }

    fun downloadCover(track: Track?) {
        if (track == null || track.imgUrl.isBlank()) return
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val context = getApplication<Application>()
                    val request = okhttp3.Request.Builder().url(track.imgUrl).build()
                    okhttp3.OkHttpClient().newCall(request).execute().use { response ->
                        check(response.isSuccessful) { "下载封面失败 (HTTP ${response.code})" }
                        val body = response.body ?: error("封面为空")
                        val values = android.content.ContentValues().apply {
                            put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "${track.title.replace(Regex("[\\\\/:*?\"<>|]"), "_")}.jpg")
                            put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                            put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/HyperMusic")
                            put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                        }
                        val resolver = context.contentResolver
                        val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("无法创建图片")
                        try {
                            resolver.openOutputStream(uri)?.use { output -> body.byteStream().use { it.copyTo(output) } } ?: error("无法保存图片")
                            values.clear(); values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0); resolver.update(uri, values, null, null)
                        } catch (e: Exception) { resolver.delete(uri, null, null); throw e }
                    }
                }
            }
            result.fold({ _toastMessage.trySend("封面已保存到相册") }, { _toastMessage.trySend(it.message ?: "保存封面失败") })
        }
    }

    fun loadLyrics(preferDownload: Boolean = true) {
        val track = playerState.currentTrack ?: return
        com.hypermusic.miuix.data.diagnostics.CrashDiagnostics.update("歌曲", "平台=${track.source}, ID=${track.id}, 本地=${!track.localDownloadId.isNullOrBlank()}, 时长=${track.duration}ms")
        com.hypermusic.miuix.data.diagnostics.CrashDiagnostics.update("歌词", "正在加载，优先本地=$preferDownload")
        lyricsLoadJob?.cancel()
        val generation = ++lyricsLoadGeneration
        val key = track.source to track.id
        val loadingStartedAt = android.os.SystemClock.elapsedRealtime()
        if (lyricsTrackKey != key) {
            lyricsTrackKey = key
            lyricState = LyricState(isLoading = true, loadingStartedAt = loadingStartedAt)
            mediaPlaybackService?.updateLyrics(null)
        } else {
            lyricState = lyricState.copy(isLoading = true, error = null, loadingStartedAt = loadingStartedAt)
        }
        fun isCurrent() = generation == lyricsLoadGeneration &&
            playerState.currentTrack?.let { it.source to it.id } == key
        fun publish(result: LyricsRepository.LyricsLoadResult) {
            if (!isCurrent()) return
            val lines = result.lyrics.lines
            com.hypermusic.miuix.data.diagnostics.CrashDiagnostics.update("歌词",
                "来源=${result.source}, 行数=${lines.size}, 逐字行=${lines.count { !it.words.isNullOrEmpty() }}, " +
                    "衬词=${lines.count { it.isBackingVocal }}, 对唱=${lines.count { it.amllAgent.isNotBlank() }}, " +
                    "异常行时序=${lines.count { it.endAt > 0 && it.endAt < it.startAt }}, " +
                    "匹配=${result.matchedTrack?.source}:${result.matchedTrack?.id}, 字体=${ThemeSettings.lyricFontSize}, 逐字=${ThemeSettings.wordByWordLyrics}")
            lyricState = lyricState.copy(
                lyrics = result.lyrics, source = result.source, rawDebugText = result.rawDebugText,
                matchedTrack = result.matchedTrack, upgraded = result.upgraded,
                isLoading = false, error = null
            )
            mediaPlaybackService?.updateLyrics(result.lyrics)
        }
        lyricsLoadJob = viewModelScope.launch {
            LyricsRepository.load(track, preferDownload = preferDownload) { provisional ->
                withContext(Dispatchers.Main.immediate) { publish(provisional) }
            }.fold(
                onSuccess = { publish(it) },
                onFailure = { error ->
                    if (isCurrent()) {
                        // A failed explicit match must not hide this song's native fallback.
                        lyricState = lyricState.copy(isLoading = false, error = error.message)
                        if (com.hypermusic.miuix.data.lyrics.LyricMatches.find(track) != null) {
                            _toastMessage.trySend(error.message ?: "已匹配的歌词暂时不可用，请重试或重新匹配")
                        }
                    }
                    Log.e("MusicPlayerViewModel", "Failed to load lyrics: ${error.message}")
                }
            )
        }
    }
    private suspend fun newSourceLiked(track: Track): Boolean = when (track.source) {
        "bodian" -> {
            val api = com.hypermusic.miuix.data.api.BodianClient(getApplication())
            val auth = com.hypermusic.miuix.data.api.BodianAuth(getApplication(), api)
            val library = com.hypermusic.miuix.data.api.BodianLibrary(api, auth, com.hypermusic.miuix.data.api.BodianCatalog(api))
            val accountRevision = auth.sessionRevision()
            var offset = 0; var found = false; var more = true
            while (more && offset < 20_000) {
                check(auth.sessionRevision() == accountRevision) { "波点音乐账号已切换，请重试" }
                val page = library.likedSongs(offset, 100)
                check(auth.sessionRevision() == accountRevision) { "波点音乐账号已切换，请重试" }
                if (page.items.any { it.id == track.id }) { found = true; break }
                more = page.hasMore
                check(!more || page.nextOffset > offset) { "波点音乐收藏分页异常" }
                offset = page.nextOffset
            }
            check(!more || found) { "波点音乐收藏列表过长" }
            found
        }
        "qishui" -> {
            val api = com.hypermusic.miuix.data.api.QishuiClient(getApplication())
            val auth = com.hypermusic.miuix.data.api.QishuiAuth(getApplication(), api)
            com.hypermusic.miuix.data.api.QishuiLibrary(api, auth).isSongLiked(track.id.removePrefix("qishui_"))
        }
        "kugou" -> {
            val api = com.hypermusic.miuix.data.api.KugouClient(getApplication())
            com.hypermusic.miuix.data.api.KugouLibrary(api, com.hypermusic.miuix.data.api.KugouAuth(getApplication(), api)).isLiked(track)
        }
        else -> error("该平台的歌曲收藏正在接入")
    }

    private suspend fun setNewSourceLiked(track: Track, liked: Boolean, expectedUserId: String) {
        when (track.source) {
            "bodian" -> {
                val api = com.hypermusic.miuix.data.api.BodianClient(getApplication())
                com.hypermusic.miuix.data.api.BodianLibrary(api, com.hypermusic.miuix.data.api.BodianAuth(getApplication(), api), com.hypermusic.miuix.data.api.BodianCatalog(api))
                    .setSongLiked(track.id, liked, expectedUserId)
            }
            "qishui" -> {
                val api = com.hypermusic.miuix.data.api.QishuiClient(getApplication())
                com.hypermusic.miuix.data.api.QishuiLibrary(api, com.hypermusic.miuix.data.api.QishuiAuth(getApplication(), api))
                    .setSongLiked(track.id.removePrefix("qishui_"), liked, expectedUserId)
            }
            "kugou" -> {
                val api = com.hypermusic.miuix.data.api.KugouClient(getApplication())
                com.hypermusic.miuix.data.api.KugouLibrary(api, com.hypermusic.miuix.data.api.KugouAuth(getApplication(), api)).setLiked(track, liked, expectedUserId)
            }
            else -> error("该平台的歌曲收藏正在接入")
        }
        com.hypermusic.miuix.data.api.MusicPlatform.fromTrackSource(track.source)?.let {
            com.hypermusic.miuix.data.api.AdditionalLibraryChanges.changed(it)
        }
    }

    suspend fun trackLiked(track: Track): Result<Boolean> = try {
        when (track.source) {
            "netease" -> NeteaseApi.checkSongLiked(track.id)
            "qqmusic" -> QqMusicApi.isLiked(track)
            else -> Result.success(newSourceLiked(track))
        }
    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
    catch (e: Exception) { Result.failure(e) }

    fun setTrackLiked(track: Track, liked: Boolean) {
        when (track.source) {
            "netease" -> setNeteaseTrackLiked(track, liked)
            "qqmusic" -> setQqTrackLiked(track, liked) { _toastMessage.trySend(if (it) "已收藏" else "已取消收藏") }
            else -> viewModelScope.launch {
                try {
                    val account = playbackAccountFor(track) ?: error("请先登录后收藏歌曲")
                    setNewSourceLiked(track, liked, account)
                    check(playbackAccountFor(track) == account) { "账号已切换，请刷新收藏状态" }
                    if (playerState.currentTrack == track) {
                        ++likeStateGeneration
                        isCurrentTrackLiked = liked
                        mediaPlaybackService?.setFavoriteState(track, liked)
                    }
                    _toastMessage.trySend(if (liked) "已收藏" else "已取消收藏")
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { _toastMessage.trySend(e.message ?: "收藏操作失败") }
            }
        }
    }

    fun likeCurrentTrack(onResult: ((Boolean) -> Unit)? = null) {
        val track = playerState.currentTrack ?: return
        if (track.source !in setOf("qqmusic", "netease")) {
            if (likeMutationInProgress) return
            likeMutationInProgress = true
            val generation = ++likeStateGeneration
            viewModelScope.launch {
                try {
                    val account = playbackAccountFor(track) ?: error("请先登录后收藏歌曲")
                    val current = newSourceLiked(track)
                    if (generation != likeStateGeneration || playerState.currentTrack != track) return@launch
                    check(playbackAccountFor(track) == account) { "账号已切换，请重试" }
                    setNewSourceLiked(track, !current, account)
                    check(playbackAccountFor(track) == account) { "账号已切换，请刷新收藏状态" }
                    if (generation == likeStateGeneration && playerState.currentTrack == track) {
                        isCurrentTrackLiked = !current
                        mediaPlaybackService?.setFavoriteState(track, !current)
                        onResult?.invoke(!current)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (e: Exception) { _toastMessage.trySend(e.message ?: "收藏操作失败") }
                finally { likeMutationInProgress = false }
            }
            return
        }
        if (track.source == "qqmusic") {
            if (likeMutationInProgress) return
            if (!QqMusicApi.isLoggedIn()) {
                _toastMessage.trySend("请先登录QQ音乐")
                return
            }
            likeMutationInProgress = true
            val generation = ++likeStateGeneration
            viewModelScope.launch {
                try {
                    val currentLike = QqMusicApi.isLiked(track).getOrThrow()
                    if (generation != likeStateGeneration || playerState.currentTrack != track) return@launch
                    if (currentLike != isCurrentTrackLiked) {
                        isCurrentTrackLiked = currentLike
                        mediaPlaybackService?.setFavoriteState(track, currentLike)
                        _toastMessage.trySend("收藏状态已更新，请重试")
                        return@launch
                    }
                    val liked = QqMusicApi.setLiked(track, !currentLike).getOrThrow()
                    if (generation == likeStateGeneration && playerState.currentTrack == track) {
                        isCurrentTrackLiked = liked
                        mediaPlaybackService?.setFavoriteState(track, liked)
                        onResult?.invoke(liked)
                    }
                } catch (e: Exception) {
                    _toastMessage.trySend(e.message ?: "QQ音乐收藏失败")
                } finally {
                    likeMutationInProgress = false
                }
            }
            return
        }
        viewModelScope.launch {
            val newLikeState = !isCurrentTrackLiked
            NeteaseApi.likeSong(track.id, newLikeState).fold(
                onSuccess = { success ->
                    if (success && playerState.currentTrack == track) {
                        isCurrentTrackLiked = newLikeState
                        mediaPlaybackService?.setFavoriteState(track, newLikeState)
                        Log.d("MusicPlayerViewModel", "Track ${if (newLikeState) "liked" else "unliked"}: ${track.title}")
                        onResult?.invoke(newLikeState)
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to like track: ${e.message}")
                    _toastMessage.trySend(e.message ?: "收藏操作失败")
                }
            )
        }
    }

    fun setNeteaseTrackLiked(track: Track, liked: Boolean) {
        viewModelScope.launch {
            NeteaseApi.likeSong(track.id, liked).fold(
                onSuccess = { success ->
                    if (success && playerState.currentTrack?.id == track.id && playerState.currentTrack?.source == track.source) {
                        ++likeStateGeneration
                        isCurrentTrackLiked = liked
                        mediaPlaybackService?.setFavoriteState(track, liked)
                    }
                    if (success) _toastMessage.trySend(if (liked) "已收藏" else "已取消收藏")
                },
                onFailure = { _toastMessage.trySend(it.message ?: "收藏操作失败") }
            )
        }
    }

    fun setQqTrackLiked(track: Track, liked: Boolean, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            QqMusicApi.setLiked(track, liked).fold(
                onSuccess = { result ->
                    if (playerState.currentTrack?.id == track.id && playerState.currentTrack?.source == track.source) {
                        ++likeStateGeneration
                        isCurrentTrackLiked = result
                        mediaPlaybackService?.setFavoriteState(track, result)
                    }
                    onResult?.invoke(result)
                },
                onFailure = { _toastMessage.trySend(it.message ?: "收藏操作失败") }
            )
        }
    }

    fun addTrackToFavorites(track: Track, onResult: ((Boolean) -> Unit)? = null) {
        if (track.id == playerState.currentTrack?.id && track.source == playerState.currentTrack?.source) {
            likeCurrentTrack(onResult)
            return
        }
        viewModelScope.launch {
            val result = when (track.source) {
                "qqmusic" -> QqMusicApi.setLiked(track, true)
                "netease" -> NeteaseApi.likeSong(track.id, true)
                else -> try {
                    val account = playbackAccountFor(track) ?: error("请先登录后收藏歌曲")
                    setNewSourceLiked(track, true, account)
                    check(playbackAccountFor(track) == account) { "账号已切换，请刷新收藏状态" }
                    Result.success(true)
                }
                    catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
            }
            result.fold({ success -> if (success) onResult?.invoke(true) },
                { _toastMessage.trySend(it.message ?: "收藏失败") })
        }
    }

    fun checkIfCurrentTrackLiked() {
        val track = playerState.currentTrack ?: return
        val generation = ++likeStateGeneration
        if (track.source !in setOf("qqmusic", "netease")) {
            isCurrentTrackLiked = false
            mediaPlaybackService?.setFavoriteState(track, false)
            viewModelScope.launch {
                try {
                    val liked = newSourceLiked(track)
                    if (generation == likeStateGeneration && playerState.currentTrack == track) {
                        isCurrentTrackLiked = liked
                        mediaPlaybackService?.setFavoriteState(track, liked)
                    }
                } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                catch (_: Exception) { /* 保持未知状态，点击时重新读取。 */ }
            }
            return
        }
        if (track.source == "qqmusic") {
            isCurrentTrackLiked = false
            viewModelScope.launch {
                QqMusicApi.isLiked(track).onSuccess {
                    if (generation == likeStateGeneration && playerState.currentTrack == track) {
                        isCurrentTrackLiked = it
                        mediaPlaybackService?.setFavoriteState(track, it)
                    }
                }
            }
            return
        }
        viewModelScope.launch {
            NeteaseApi.checkSongLiked(track.id).fold(
                onSuccess = { isLiked ->
                    if (generation == likeStateGeneration && playerState.currentTrack == track) {
                        isCurrentTrackLiked = isLiked
                        mediaPlaybackService?.setFavoriteState(track, isLiked)
                    }
                    Log.d("MusicPlayerViewModel", "Track liked status: $isLiked for ${track.title}")
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to check liked status: ${e.message}")
                }
            )
        }
    }

    override fun onCleared() {
        if (pendingPlatformRestore != PlatformManager.current) savePlatformPlayback(PlatformManager.current)
        super.onCleared()
        if (isBound) {
            mediaPlaybackService?.onTrackChangedListener = null
            mediaPlaybackService?.onPlaybackStateChanged = null
            mediaPlaybackService?.onAudioFormatChanged = null
            mediaPlaybackService?.onCachedPlaybackError = null
            mediaPlaybackService?.onNotificationClose = null
            mediaPlaybackService?.onNotificationFavorite = null
            getApplication<Application>().unbindService(serviceConnection)
            isBound = false
        }
    }
}
