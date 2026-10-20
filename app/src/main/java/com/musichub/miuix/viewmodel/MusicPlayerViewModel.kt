package com.musichub.miuix.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.model.Playlist
import com.musichub.miuix.data.model.PlaylistDetail
import com.musichub.miuix.data.model.RecommendPlaylist
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.data.lyrics.LyricsRepository
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.service.MediaPlaybackService
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.roundToInt
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    /** 雷达歌单 */
    val radarPlaylists: List<RecommendPlaylist> = emptyList(),
    /** 歌手推荐 */
    val recommendArtists: List<com.musichub.miuix.data.model.ArtistInfo> = emptyList(),
    /** 新碟上架 */
    val newAlbums: List<com.musichub.miuix.data.model.AlbumInfo> = emptyList(),
    /** 收藏的专辑 */
    val userAlbums: List<com.musichub.miuix.data.model.AlbumInfo> = emptyList(),
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
    val error: String? = null
)

data class UpdateState(
    val latest: com.musichub.miuix.data.model.UpdateInfo? = null,
    val hasUpdate: Boolean = false,
    val dismissed: Boolean = false
)

data class LyricState(
    val lyrics: SongLyrics? = null,
    val source: String? = null,
    val upgraded: Boolean = false,
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

    var updateState by mutableStateOf(UpdateState())
        private set

    var showUpdateLog by mutableStateOf(false)
        private set
    var selectedUpdateLog by mutableStateOf<com.musichub.miuix.data.model.UpdateInfo?>(null)
        private set

    private var updateCheckJob: Job? = null
    private var autoUpdateChecked = false

    var lyricState by mutableStateOf(LyricState())
        private set

    var isCurrentTrackLiked by mutableStateOf(false)
        private set

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

    // 音质选择索引（对应 999000 / 740000 / 320000 / 128000）
    var audioQualityIndex by mutableIntStateOf(0)
        private set

    // 随身听FM模式
    var isFMMode by mutableStateOf(false)
        private set
    private var fmPlaylist: List<Track> = emptyList()
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
        // 恢复FM模式或心动模式状态
        val savedMode = prefs.getString("play_mode_type", "normal")
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

    private var pendingTrack: Track? = null
    private var lyricsLoadJob: kotlinx.coroutines.Job? = null
    private var lyricsLoadGeneration = 0

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as MediaPlaybackService.LocalBinder
            mediaPlaybackService = binder.getService()
            isBound = true
            mediaPlaybackService?.setPlaybackSpeed(playbackSpeed)
            
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
                playerState = playerState.copy(isPlaying = isPlaying)
            }
            
            // 如果有待播放的歌曲，准备播放
            pendingTrack?.let { track ->
                prepareTrack(track)
                pendingTrack = null
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
        playbackSpeed = prefs.getFloat("playback_speed", 1.0f).coerceIn(0.1f, 5f)
        audioQualityIndex = prefs.getInt("audio_quality_index", 0)

        bindMediaService()
        startProgressUpdate()

        // 先应用保存的账号，再发起所有依赖登录态的数据/播放请求，
        // 否则启动瞬间仍会使用最后登录账号（NeteaseApi.init 载入的 cookies）。
        viewModelScope.launch {
            applySavedAccounts()
            loadUserInfo()

            loadLastPlayedTrack()
            loadLastPlaylist()
            loadRecommendPlaylists()
            loadDailyRecommendSongs()
            loadUserPlaylists()
            loadRadarPlaylists()
            loadTopArtists()
            loadNewAlbums()

            // 打开应用时自动检查更新（可在关于页关闭）
            if (com.musichub.miuix.ui.theme.ThemeSettings.autoCheckUpdate && !autoUpdateChecked) {
                autoUpdateChecked = true
                checkForUpdates()
            }

            restorePlaybackMode()
        }
    }

    /** 应用保存的播放账号 / 数据账号，并刷新播放账号的 VIP 状态。 */
    private suspend fun applySavedAccounts() {
        val savedAccounts = NeteaseApi.getAccounts()
        if (savedAccounts.isEmpty()) return

        // 账号列表可能变化（删除账号等），越界时回退到第一个账号
        if (playbackAccountIndex !in savedAccounts.indices) playbackAccountIndex = 0
        if (dataAccountIndex !in savedAccounts.indices) dataAccountIndex = 0

        if (playbackAccountIndex < savedAccounts.size) {
            NeteaseApi.switchToAccount(savedAccounts[playbackAccountIndex].userId)
            NeteaseApi.getUserProfile().fold(
                onSuccess = { profile -> playbackVipType = profile.vipType },
                onFailure = { playbackVipType = 0 }
            )
        }

        val dataUserId = if (dataAccountIndex < savedAccounts.size) {
            savedAccounts[dataAccountIndex].userId
        } else {
            null
        }
        if (dataUserId != null) {
            NeteaseApi.switchToAccount(dataUserId)
        }
    }

    /**
     * 登录完成 / 账号列表变化后刷新账号状态：
     * 重新应用播放账号（读取 VIP）、加载用户信息与收藏。
     */
    fun refreshAccountState() {
        viewModelScope.launch {
            applySavedAccounts()
            loadUserInfo()
            loadRecommendPlaylists()
            loadUserPlaylists()
        }
    }

    /** 恢复私人FM / 心动模式，需在账号应用完成后调用。 */
    private fun restorePlaybackMode() {
        val savedMode = prefs.getString("play_mode_type", "normal")
        when (savedMode) {
            "fm" -> {
                isFMMode = true
                viewModelScope.launch {
                    NeteaseApi.getPersonalFM().fold(
                        onSuccess = { tracks ->
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
                        service.lyriconManager?.setPosition(position)
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

    private fun prepareTrack(track: Track) {
        if (!isBound) {
            pendingTrack = track
            return
        }
        viewModelScope.launch {
            // 用播放账号的 Cookie 获取播放地址，不切换全局账号，避免影响并发中的数据请求
            val accounts = NeteaseApi.getAccounts()
            val playbackUserId = if (playbackAccountIndex < accounts.size) accounts[playbackAccountIndex].userId else null
            val playbackCookie = playbackUserId?.let { NeteaseApi.getAccountCookie(it) }

            NeteaseApi.getSongUrl(track.id, accountCookie = playbackCookie).fold(
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
                onFailure = { }
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

    fun loadRecommendPlaylists(showLoading: Boolean = true) {
        viewModelScope.launch {
            // 下拉刷新时不显示全屏加载态，避免整页被刷新
            if (showLoading) {
                recommendState = recommendState.copy(isLoading = true, error = null)
            }
            // 已登录取「专属歌单」，未登录取通用推荐
            val result = if (NeteaseApi.isLoggedIn()) {
                NeteaseApi.getRecommendResource()
            } else {
                NeteaseApi.getRecommendPlaylists()
            }
            result.fold(
                onSuccess = { playlists ->
                    recommendState = recommendState.copy(
                        playlists = playlists,
                        isLoading = false
                    )
                },
                onFailure = { e ->
                    // 回退到通用推荐
                    NeteaseApi.getRecommendPlaylists().fold(
                        onSuccess = { playlists ->
                            recommendState = recommendState.copy(playlists = playlists, isLoading = false)
                        },
                        onFailure = { e2 ->
                            recommendState = recommendState.copy(isLoading = false, error = e2.message)
                        }
                    )
                }
            )
        }
    }

    /** 雷达歌单：固定歌单 id 逐个取详情 */
    fun loadRadarPlaylists() {
        viewModelScope.launch {
            val ids = listOf(
                "3136952023", "8402996200", "5320167908", "5327906368",
                "5362359247", "5300458264", "5341776086"
            )
            val playlists = coroutineScope {
                ids.map { id ->
                    async { NeteaseApi.getPlaylistSummary(id).getOrNull() }
                }.awaitAll().filterNotNull()
            }
            recommendState = recommendState.copy(radarPlaylists = playlists)
        }
    }

    /** 歌手推荐（热门歌手） */
    fun loadTopArtists() {
        viewModelScope.launch {
            NeteaseApi.getTopArtists(12).fold(
                onSuccess = { artists ->
                    recommendState = if (artists.isNotEmpty()) {
                        recommendState.copy(recommendArtists = artists)
                    } else {
                        recommendState.copy(recommendArtists = deriveArtistsFromDaily())
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "loadTopArtists error: ${e.message}")
                    recommendState = recommendState.copy(recommendArtists = deriveArtistsFromDaily())
                }
            )
        }
    }

    /** 从每日推荐歌曲中提炼歌手作为兜底推荐 */
    private fun deriveArtistsFromDaily(): List<com.musichub.miuix.data.model.ArtistInfo> {
        val map = LinkedHashMap<String, com.musichub.miuix.data.model.ArtistInfo>()
        for (track in recommendState.dailySongs) {
            track.artistIds.forEachIndexed { index, id ->
                if (id.isNotEmpty() && !map.containsKey(id)) {
                    val name = track.artistNames.getOrNull(index)?.takeIf { it.isNotBlank() } ?: track.artist
                    map[id] = com.musichub.miuix.data.model.ArtistInfo(
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
        viewModelScope.launch {
            NeteaseApi.getNewAlbums(12).fold(
                onSuccess = { albums ->
                    recommendState = recommendState.copy(newAlbums = albums)
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "loadNewAlbums error: ${e.message}")
                }
            )
        }
    }

    /** 检查更新：manual=true 时无论结果都会 toast 提示 */
    fun checkForUpdates(manual: Boolean = false) {
        if (updateCheckJob?.isActive == true) return
        updateCheckJob = viewModelScope.launch {
            com.musichub.miuix.data.api.UpdateApi.fetchUpdateLog().fold(
                onSuccess = { info ->
                    val current = getCurrentVersionName()
                    val newer = isVersionNewer(info.version, current)
                    updateState = updateState.copy(latest = info, hasUpdate = newer)
                    if (manual) {
                        if (newer) {
                            _toastMessage.trySend("发现新版本 v${info.version}")
                            openUpdateLog(info)
                        } else {
                            _toastMessage.trySend("已是最新版本")
                        }
                    }
                },
                onFailure = { e ->
                    // 超时/失败不重试
                    Log.e("MusicPlayerViewModel", "checkForUpdates error", e)
                    if (manual) {
                        val reason = e.message?.take(60)?.ifBlank { null } ?: e.javaClass.simpleName
                        _toastMessage.trySend("检查更新失败：$reason")
                    }
                }
            )
        }
    }

    fun dismissUpdateCard() {
        updateState = updateState.copy(dismissed = true)
    }

    fun openUpdateLog() {
        updateState.latest?.let { openUpdateLog(it) }
    }

    fun openUpdateLog(info: com.musichub.miuix.data.model.UpdateInfo) {
        selectedUpdateLog = info
        showUpdateLog = true
    }

    fun closeUpdateLog() {
        showUpdateLog = false
    }

    private fun getCurrentVersionName(): String {
        return try {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    private fun parseVersionParts(v: String): List<Int> {
        // 去掉 v 前缀与 -release/-beta1/+build 等后缀，并取每段前导数字（如 3-beta1 → 3）
        val main = v.trim().removePrefix("v").removePrefix("V")
            .substringBefore("-").substringBefore("+")
        return main.split(".").map { part ->
            part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    private fun isVersionNewer(latest: String, current: String): Boolean {
        val l = parseVersionParts(latest)
        val c = parseVersionParts(current)
        val n = maxOf(l.size, c.size)
        for (i in 0 until n) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    fun loadDailyRecommendSongs() {
        viewModelScope.launch {
            NeteaseApi.getDailyRecommendSongs().fold(
                onSuccess = { songs ->
                    recommendState = recommendState.copy(dailySongs = songs)
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

    /** 随机播放一首（试试手气）：从每日推荐 / 我喜欢的音乐里随机挑一首 */
    fun playRandomOne() {
        viewModelScope.launch {
            val pool = mutableListOf<Track>()
            pool.addAll(recommendState.dailySongs)

            val likedId = resolveLikedPlaylistId()
            if (likedId.isNotEmpty()) {
                NeteaseApi.getPlaylistDetail(likedId).fold(
                    onSuccess = { detail -> pool.addAll(detail.tracks) },
                    onFailure = { e ->
                        Log.e("MusicPlayerViewModel", "playRandomOne load liked failed: ${e.message}")
                    }
                )
            }

            // 去重后随机
            val unique = pool.distinctBy { it.id }
            if (unique.isEmpty()) {
                android.widget.Toast.makeText(
                    getApplication(),
                    "暂无可随机播放的歌曲",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                return@launch
            }
            val randomTrack = unique.random()
            // 队列 = 随机曲 + 每日推荐，保证上一首/下一首可用
            val queue = mutableListOf(randomTrack)
            queue.addAll(recommendState.dailySongs.filter { it.id != randomTrack.id })
            playTrackWithMode(randomTrack, queue)
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
                        loadCloudDiskCover()
                        loadUserAlbums()
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

    /** 拉取云盘第一首歌封面，供歌单页云盘卡片展示 */
    fun loadCloudDiskCover(force: Boolean = false) {
        if (!force && recommendState.cloudDiskCoverUrl.isNotEmpty()) return
        viewModelScope.launch {
            NeteaseApi.getCloudDiskSongs(offset = 0, limit = 1).fold(
                onSuccess = { result ->
                    val cover = result.tracks.firstOrNull()?.imgUrl ?: ""
                    if (cover.isNotEmpty()) {
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
        viewModelScope.launch {
            NeteaseApi.getUserAlbums().fold(
                onSuccess = { albums ->
                    recommendState = recommendState.copy(userAlbums = albums)
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
            NeteaseApi.switchToAccount(accounts[index].userId)
            // 获取播放账号的VIP状态（缓存，供播放时本地判断）
            viewModelScope.launch {
                NeteaseApi.getUserProfile().fold(
                    onSuccess = { profile -> playbackVipType = profile.vipType },
                    onFailure = { playbackVipType = 0 }
                )
                // 恢复数据账号Cookie
                val dataUserId = if (dataAccountIndex < accounts.size) accounts[dataAccountIndex].userId else null
                if (dataUserId != null) {
                    NeteaseApi.switchToAccount(dataUserId)
                }
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
    fun playTrackWithMode(track: Track, playlist: List<Track>) {
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
        isFMMode = true
        fmIndex = 0
        // 保存FM模式状态
        prefs.edit().putString("play_mode_type", "fm").apply()
        viewModelScope.launch {
            Log.d("MusicPlayerViewModel", "Loading FM songs...")
            NeteaseApi.getPersonalFM().fold(
                onSuccess = { tracks ->
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
        isFMMode = false
        fmPlaylist = emptyList()
        fmIndex = 0
        // 清除FM模式状态
        prefs.edit().remove("play_mode_type").apply()
    }

    fun fmPlayNext() {
        fmIndex++
        // 如果播放到最后一首，加载更多
        if (fmIndex >= fmPlaylist.size) {
            viewModelScope.launch {
                NeteaseApi.getPersonalFM().fold(
                    onSuccess = { tracks ->
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
        isIntelligenceMode = true
        if (isFMMode) exitFMMode()
        // 加载过程复用「等待歌曲」态：播放按钮转圈，进度条/上下首等禁用
        playerState = playerState.copy(isLoading = true, error = null)
        onReady()
        ensureUserPlaylistsLoaded()
        viewModelScope.launch {
            Log.d("MusicPlayerViewModel", "Entering intelligence mode seed=$songId pid=$playlistId")
            val resolved = resolveHeartModeParams(songId, playlistId)
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
        }
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
        // VIP歌曲限制：纯本地判断，不发网络请求，不替换当前播放歌曲
        if (track.isVip) {
            if (!NeteaseApi.isLoggedIn()) {
                _toastMessage.trySend("非VIP用户无法播放VIP歌曲")
                return
            }
            if (playbackVipType == 0) {
                _toastMessage.trySend("非VIP用户无法播放VIP歌曲")
                return
            }
        }
        
        viewModelScope.launch {
            playerState = playerState.copy(isLoading = true, error = null, currentTrack = track)
            saveLastPlayedTrack(track)
            saveLastPlaylist()

            // 第一阶段：立即用元数据（无歌词）更新 Lyricon
            loadLyrics()
            checkIfCurrentTrackLiked()

            // 使用播放账号的 Cookie 获取播放地址，避免临时切换全局账号影响其他请求
            val accounts = NeteaseApi.getAccounts()
            val playbackUserId = if (playbackAccountIndex < accounts.size) accounts[playbackAccountIndex].userId else null
            val playbackCookie = playbackUserId?.let { NeteaseApi.getAccountCookie(it) }

            // 获取用户选择的音质
            val qualityBitrates = listOf(999000, 999000, 740000, 320000, 128000)
            val qualityIndex = audioQualityIndex
            val selectedBitrate = if (qualityIndex < qualityBitrates.size) qualityBitrates[qualityIndex] else 999000

            NeteaseApi.getSongUrl(track.id, selectedBitrate, accountCookie = playbackCookie).fold(
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

    /** 按最新歌词设置重新同步当前歌词到词幕。 */
    fun resyncLyricsToLyricon() {
        lyricState.lyrics?.let { mediaPlaybackService?.updateLyrics(it) }
    }

    /** 设置播放速度，限制在 0.1x - 5.0x，并保留最多两位小数。 */
    fun updatePlaybackSpeed(speed: Float) {
        val normalized = (speed.coerceIn(0.1f, 5f) * 100f).roundToInt() / 100f
        if (normalized == playbackSpeed) return
        playbackSpeed = normalized
        prefs.edit().putFloat("playback_speed", normalized).apply()
        mediaPlaybackService?.setPlaybackSpeed(normalized)
    }

    /** 设置音质选择索引。 */
    fun updateAudioQualityIndex(index: Int) {
        audioQualityIndex = index
        prefs.edit().putInt("audio_quality_index", index).apply()
    }

    fun togglePlayPause() {
        mediaPlaybackService?.let { service ->
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
        return mediaPlaybackService?.getCurrentPosition() ?: playerState.currentPosition
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

    fun loadLyrics() {
        val track = playerState.currentTrack ?: return
        lyricsLoadJob?.cancel() // 取消之前的歌词加载任务
        val generation = ++lyricsLoadGeneration
        lyricState = lyricState.copy(isLoading = true, error = null)
        lyricsLoadJob = viewModelScope.launch {
            LyricsRepository.load(track).fold(
                onSuccess = { result ->
                    // 检查歌曲是否已切换，以及是否有更新的加载请求
                    if (generation == lyricsLoadGeneration && playerState.currentTrack?.id == track.id) {
                        lyricState = lyricState.copy(
                            lyrics = result.lyrics,
                            source = result.source,
                            upgraded = result.upgraded,
                            isLoading = false
                        )
                        // 同步歌词到Lyricon
                        mediaPlaybackService?.updateLyrics(result.lyrics)
                        Log.d("MusicPlayerViewModel", "Lyrics loaded and synced: ${result.lyrics.lines.size} lines, source=${result.source}")
                    } else {
                        Log.d("MusicPlayerViewModel", "Lyrics loaded but stale or track changed, skipping sync")
                    }
                },
                onFailure = { e ->
                    if (generation == lyricsLoadGeneration) {
                        lyricState = lyricState.copy(isLoading = false, error = e.message)
                    }
                    Log.e("MusicPlayerViewModel", "Failed to load lyrics: ${e.message}")
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

    fun downloadAndShareCurrentTrack() {
        val track = playerState.currentTrack ?: return
        val context = getApplication<Application>()
        android.widget.Toast.makeText(context, "正在下载，请稍等……", android.widget.Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            NeteaseApi.getSongUrl(track.id).fold(
                onSuccess = { (url, _, type) ->
                    if (url.isNotEmpty()) {
                        downloadAndShareFile(url, "${track.title} - ${track.artist}.$type", "audio/$type")
                    } else {
                        android.widget.Toast.makeText(context, "获取下载链接失败，请检查账号权限", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to get download URL: ${e.message}")
                    android.widget.Toast.makeText(context, "获取下载链接失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            )
        }
    }

    fun downloadAndShareCover() {
        val track = playerState.currentTrack ?: return
        val coverUrl = track.imgUrl
        if (coverUrl.isNotEmpty()) {
            val context = getApplication<Application>()
            android.widget.Toast.makeText(context, "正在下载，请稍等……", android.widget.Toast.LENGTH_SHORT).show()
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val fileName = "${track.title} - ${track.artist}_cover.jpg"
                    
                    // 下载封面到缓存目录
                    val url = java.net.URL(coverUrl)
                    val connection = url.openConnection()
                    connection.connect()
                    val inputStream = connection.getInputStream()
                    val file = java.io.File(context.cacheDir, fileName)
                    file.outputStream().use { output ->
                        inputStream.copyTo(output)
                    }
                    inputStream.close()
                    
                    // 分享文件（需要在主线程执行）
                    withContext(Dispatchers.Main) {
                        val uri = androidx.core.content.FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.fileprovider",
                            file
                        )
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "image/jpeg"
                            putExtra(Intent.EXTRA_STREAM, uri)
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "分享封面").apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        })
                    }
                } catch (e: Exception) {
                    Log.e("MusicPlayerViewModel", "Download and share cover failed: ${e.message}")
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "下载封面并分享失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    private fun downloadAndShareFile(url: String, fileName: String, mimeType: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            try {
                
                // 下载文件到缓存目录
                val urlObj = java.net.URL(url)
                val connection = urlObj.openConnection()
                connection.connect()
                val inputStream = connection.getInputStream()
                val file = java.io.File(context.cacheDir, fileName)
                file.outputStream().use { output ->
                    inputStream.copyTo(output)
                }
                inputStream.close()
                
                // 分享文件（需要在主线程执行）
                withContext(Dispatchers.Main) {
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        file
                    )
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "分享歌曲").apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
            } catch (e: Exception) {
                Log.e("MusicPlayerViewModel", "Download and share failed: ${e.message}")
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "下载并分享失败: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun likeCurrentTrack(onResult: ((Boolean) -> Unit)? = null) {
        val track = playerState.currentTrack ?: return
        viewModelScope.launch {
            val newLikeState = !isCurrentTrackLiked
            NeteaseApi.likeSong(track.id, newLikeState).fold(
                onSuccess = { success ->
                    if (success) {
                        isCurrentTrackLiked = newLikeState
                        Log.d("MusicPlayerViewModel", "Track ${if (newLikeState) "liked" else "unliked"}: ${track.title}")
                        onResult?.invoke(newLikeState)
                    }
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to like track: ${e.message}")
                }
            )
        }
    }

    fun checkIfCurrentTrackLiked() {
        val track = playerState.currentTrack ?: return
        viewModelScope.launch {
            NeteaseApi.checkSongLiked(track.id).fold(
                onSuccess = { isLiked ->
                    isCurrentTrackLiked = isLiked
                    Log.d("MusicPlayerViewModel", "Track liked status: $isLiked for ${track.title}")
                },
                onFailure = { e ->
                    Log.e("MusicPlayerViewModel", "Failed to check liked status: ${e.message}")
                }
            )
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
