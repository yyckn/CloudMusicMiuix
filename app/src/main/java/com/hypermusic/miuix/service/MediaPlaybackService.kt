package com.hypermusic.miuix.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Binder
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import androidx.media3.common.AudioAttributes as Media3AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Metadata
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.extractor.metadata.flac.VorbisComment
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.id3.InternalFrame
import androidx.media3.extractor.metadata.id3.CommentFrame
import androidx.media3.container.MdtaMetadataEntry
import com.hypermusic.miuix.data.audio.AudioSettings
import com.hypermusic.miuix.data.audio.ReplayGainInfo
import com.hypermusic.miuix.data.audio.SoundAudioProcessor
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import com.hypermusic.miuix.data.cache.AudioCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.hypermusic.miuix.MainActivity
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class MediaPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var player: ExoPlayer? = null
    private var audioProcessor: SoundAudioProcessor? = null
    @Volatile private var albumPlayback = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private lateinit var historyTracker: com.hypermusic.miuix.data.history.PlaybackHistoryTracker

    private var currentTrack: Track? = null
    private var currentUrl: String? = null
    var mediaNotificationDismissed = false
        private set
    private var mediaRequestGeneration = 0L
    private var currentCacheFormat = "unknown"
    private var currentIsPreview = false
    private var playlist: List<Track> = emptyList()
    private var isPlaying = false
    private var playbackPosition = 0L
    private var playbackSpeed = 1.0f
    private var completionHandled = false
    private var errorRetryCount = 0
    private var fadeJob: Job? = null
    private var pendingFadeIn = false

    private val media3AudioAttributes = Media3AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
        .build()

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    private fun applyReplayGainMetadata(metadata: Metadata?) {
        if (metadata == null) return
        var info = audioProcessor?.replayGain ?: ReplayGainInfo()
        for (index in 0 until metadata.length()) {
            when (val entry = metadata[index]) {
                is VorbisComment -> info = info.withTag(entry.key, entry.value)
                is TextInformationFrame -> entry.description?.let { key -> entry.values.firstOrNull()?.let { info = info.withTag(key, it) } }
                is InternalFrame -> info = info.withTag(entry.description, entry.text)
                is CommentFrame -> info = info.withTag(entry.description, entry.text)
                is MdtaMetadataEntry -> info = info.withTag(entry.key.substringAfterLast(':'), entry.value.toString(Charsets.UTF_8))
            }
        }
        audioProcessor?.replayGain = info
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (::historyTracker.isInitialized) historyTracker.update(isPlaying, player?.playbackParameters?.speed ?: 1f, player?.duration ?: 0L)
        }

        override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) {
            if (::historyTracker.isInitialized) historyTracker.update(player?.isPlaying == true, playbackParameters.speed)
        }
        override fun onMetadata(metadata: Metadata) = applyReplayGainMetadata(metadata)

        override fun onTracksChanged(tracks: Tracks) {
            for (group in tracks.groups) {
                if (group.type != C.TRACK_TYPE_AUDIO) continue
                for (index in 0 until group.length) {
                    if (group.isTrackSelected(index)) {
                        val format = group.getTrackFormat(index)
                        applyReplayGainMetadata(format.metadata)
                        currentTrack?.let { onAudioFormatChanged?.invoke(it, format, currentCacheFormat) }
                    }
                }
            }
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                }
                Player.STATE_READY -> {
                    val state = if (isPlaying) {
                        PlaybackStateCompat.STATE_PLAYING
                    } else {
                        PlaybackStateCompat.STATE_PAUSED
                    }
                    updatePlaybackState(state)
                    if (pendingFadeIn && isPlaying) {
                        pendingFadeIn = false
                        player?.volume = 0f
                        fadeToVolume(1f)
                    }
                }
                Player.STATE_ENDED -> {
                    if (::historyTracker.isInitialized) historyTracker.finish(completed = true)
                    if (!completionHandled) {
                        completionHandled = true
                        Log.d(TAG, "Track completed normally")
                        isPlaying = false
                        onPlaybackStateChanged?.invoke(false)
                        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
                        lyriconManager?.setPlaybackState(false)
                        playNext()
                    }
                }
                Player.STATE_IDLE -> Unit
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(
                TAG,
                "onPlayerError: ${error.errorCodeName} (${error.errorCode}) at ${player?.currentPosition}",
                error
            )
            val resumeAfterFallback = isPlaying
            val position = player?.currentPosition ?: 0L
            isPlaying = false
            onPlaybackStateChanged?.invoke(false)
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            lyriconManager?.setPlaybackState(false)
            val track = currentTrack
            if (currentUrl == "https://127.0.0.1/cached" && track != null && onCachedPlaybackError != null) {
                onCachedPlaybackError?.invoke(track, position, resumeAfterFallback, currentCacheFormat)
            } else recoverFromError()
        }
    }
    private var currentBitmap: Bitmap? = null
    private var currentLyrics: com.hypermusic.miuix.data.model.SongLyrics? = null
    private var wasPlayingBeforeFocusLoss = false
    
    // 回调接口，用于通知ViewModel歌曲切换
    var onTrackChangedListener: OnTrackChangedListener? = null
    
    // 回调接口，用于通知ViewModel播放状态变化
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    var onAudioFormatChanged: ((Track, androidx.media3.common.Format, String) -> Unit)? = null
    var onCachedPlaybackError: ((Track, Long, Boolean, String) -> Unit)? = null
    var onNotificationClose: (() -> Unit)? = null
    var onNotificationFavorite: ((Track) -> Unit)? = null
    private var favoriteTrack: Track? = null
    private var favorite = false
    private var favoriteJob: Job? = null
    
    // Lyricon词幕管理器
    var lyriconManager: LyriconProviderManager? = null
        private set

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): MediaPlaybackService = this@MediaPlaybackService
    }

    interface OnTrackChangedListener {
        fun onNextTrack()
        fun onPreviousTrack()
    }

    companion object {
        const val CHANNEL_ID = "media_playback_channel"
        const val NOTIFICATION_ID = 1
        const val PREF_MEDIA_DISMISSED = "media_notification_dismissed"
        const val MEDIA_SESSION_TAG = "CloudMusicMediaSession"
        const val ACTION_PLAY = "com.hypermusic.miuix.PLAY"
        const val ACTION_PAUSE = "com.hypermusic.miuix.PAUSE"
        const val ACTION_NEXT = "com.hypermusic.miuix.NEXT"
        const val ACTION_PREVIOUS = "com.hypermusic.miuix.PREVIOUS"
        const val ACTION_CLOSE = "com.hypermusic.miuix.CLOSE_MEDIA"
        const val ACTION_FAVORITE = "com.hypermusic.miuix.FAVORITE_MEDIA"
        private const val TAG = "MediaPlaybackService"
        private const val MAX_ERROR_RETRIES = 2
        private const val FADE_DURATION_MS = 300L
        private const val FADE_STEP_MS = 16L
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override fun onBind(intent: Intent): android.os.IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        com.hypermusic.miuix.data.history.ListeningHistory.init(this)
        historyTracker = com.hypermusic.miuix.data.history.PlaybackHistoryTracker(this)
        serviceScope.launch {
            while (isActive) {
                delay(1000)
                historyTracker.update(player?.isPlaying == true, player?.playbackParameters?.speed ?: 1f, player?.duration ?: 0L)
            }
        }
        com.hypermusic.miuix.data.download.Downloads.init(this)
        ThemeSettings.init(this)
        AudioSettings.init(this)
        com.hypermusic.miuix.data.lyrics.LyricMatches.init(this)
        createNotificationChannel()
        mediaNotificationDismissed = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            .getBoolean(PREF_MEDIA_DISMISSED, false)
        initializeMediaSession()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        
        // 初始化Lyricon词幕
        lyriconManager = LyriconProviderManager(this)
        lyriconManager?.initialize()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            ACTION_PLAY -> resumePlayback()
            ACTION_PAUSE -> pausePlayback()
            ACTION_NEXT -> playNext()
            ACTION_PREVIOUS -> playPrevious()
            ACTION_CLOSE -> closeFromNotification()
            ACTION_FAVORITE -> favoriteFromNotification()
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "音乐播放",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "显示正在播放的音乐"
            setShowBadge(false)
        }
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun initializeMediaSession() {
        mediaSession = MediaSessionCompat(this, MEDIA_SESSION_TAG).apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setCallback(mediaSessionCallback)
            isActive = !mediaNotificationDismissed
        }
        sessionToken = mediaSession.sessionToken
    }

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPlay() {
            resumePlayback()
        }

        override fun onPause() {
            pausePlayback()
        }

        override fun onSkipToNext() {
            playNext()
        }

        override fun onSkipToPrevious() {
            playPrevious()
        }

        override fun onStop() {
            closeFromNotification()
        }

        override fun onCustomAction(action: String?, extras: Bundle?) {
            when (action) {
                ACTION_CLOSE -> closeFromNotification()
                ACTION_FAVORITE -> favoriteFromNotification()
            }
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }
    }

    fun setAlbumPlayback(enabled: Boolean) {
        albumPlayback = enabled
        audioProcessor?.albumPlayback = enabled
    }

    fun setPlaylist(tracks: List<Track>) {
        playlist = tracks
    }

    fun playTrack(track: Track) {
        val generation = ++mediaRequestGeneration
        mediaNotificationDismissed = false
        currentAudioTrack = track
        currentTrack = track
        serviceScope.launch {
            if (generation != mediaRequestGeneration) return@launch
            try {
                if (requestAudioFocus()) {
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                    loadAlbumArtAndShowNotification(track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "playTrack error", e)
            }
        }
    }

    var currentAudioTrack: Track? = null
        private set

    fun playTrackWithUrl(track: Track, url: String, cacheFormat: String = "unknown", isPreview: Boolean = false, audioTrack: Track = track) {
        val generation = ++mediaRequestGeneration
        mediaNotificationDismissed = false
        currentAudioTrack = audioTrack
        currentCacheFormat = cacheFormat
        currentIsPreview = isPreview
        currentTrack = track
        errorRetryCount = 0
        serviceScope.launch {
            if (generation != mediaRequestGeneration) return@launch
            try {
                if (requestAudioFocus()) {
                    // 第一阶段：立即用元数据（无歌词）更新 Lyricon
                    lyriconManager?.setTrack(track)
                    lyriconManager?.setPlaybackState(true)

                    startPlayback(track, url, autoplay = true)
                    loadAlbumArtAndShowNotification(track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "playTrackWithUrl error", e)
            }
        }
    }

    fun restoreTrackWithUrl(track: Track, url: String, position: Long, resume: Boolean, cacheFormat: String = "unknown", isPreview: Boolean = false, audioTrack: Track = track) {
        val generation = ++mediaRequestGeneration
        mediaNotificationDismissed = false
        currentAudioTrack = audioTrack
        currentCacheFormat = cacheFormat
        currentIsPreview = isPreview
        currentTrack = track
        errorRetryCount = 0
        serviceScope.launch {
            if (generation != mediaRequestGeneration) return@launch
            try {
                lyriconManager?.setTrack(track)
                if (resume && !requestAudioFocus()) return@launch
                startPlayback(track, url, autoplay = resume, startPosition = position)
                lyriconManager?.setPlaybackState(resume)
                loadAlbumArtAndShowNotification(track)
            } catch (e: Exception) { Log.e(TAG, "restoreTrackWithUrl error", e) }
        }
    }

    fun prepareTrackWithUrl(track: Track, url: String) {
        val generation = ++mediaRequestGeneration
        mediaNotificationDismissed = false
        currentAudioTrack = track
        currentIsPreview = false
        currentTrack = track
        errorRetryCount = 0
        serviceScope.launch {
            if (generation != mediaRequestGeneration) return@launch
            try {
                // 设置词幕歌曲信息
                lyriconManager?.setTrack(track)
                startPlayback(track, url, autoplay = false)
                loadAlbumArtAndShowNotification(track)
            } catch (e: Exception) {
                Log.e(TAG, "prepareTrackWithUrl error", e)
            }
        }
    }

    /**
     * 使用 ExoPlayer 加载一首歌，可复用于首次播放、错误重试和播放结束后重新开始。
     */
    private fun startPlayback(
        track: Track,
        url: String,
        autoplay: Boolean,
        startPosition: Long = 0L
    ) {
        currentTrack = track
        mediaSession.isActive = true
        mediaNotificationDismissed = false
        if (autoplay) getSharedPreferences("player_prefs", Context.MODE_PRIVATE).edit().putBoolean(PREF_MEDIA_DISMISSED, false).apply()
        currentUrl = url
        playbackPosition = startPosition
        completionHandled = false
        isPlaying = autoplay

        releasePlayer()
        val newPlayer = createPlayer(track)
        historyTracker.begin(track)
        player = newPlayer
        pendingFadeIn = autoplay && ThemeSettings.fadeEnabled
        newPlayer.volume = if (pendingFadeIn) 0f else 1f

        updatePlaybackState(
            if (autoplay) PlaybackStateCompat.STATE_BUFFERING
            else PlaybackStateCompat.STATE_PAUSED
        )

        val audioTrack = currentAudioTrack ?: track
        val mediaItem = MediaItem.Builder().setUri(url)
            .setCustomCacheKey(AudioCache.key(audioTrack.source, audioTrack.id, currentCacheFormat)).build()
        newPlayer.setMediaItem(mediaItem)
        if (startPosition > 0L) {
            newPlayer.seekTo(startPosition)
        }
        newPlayer.prepare()
        newPlayer.playWhenReady = autoplay

        if (autoplay) {
            onPlaybackStateChanged?.invoke(true)
        }
    }

    private fun createPlayer(track: Track): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient).setUserAgent(USER_AGENT)
        when ((currentAudioTrack ?: track).source) {
            "qqmusic" -> dataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to "https://y.qq.com/"))
            "netease" -> dataSourceFactory.setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com"))
        }
        val sourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, dataSourceFactory)
        val localFile = android.net.Uri.parse(currentUrl).scheme in setOf("file", "content")
        val cachedDataSource = if (currentIsPreview || localFile) null else runCatching {
            CacheDataSource.Factory().setCache(AudioCache.get(applicationContext))
                .setUpstreamDataSourceFactory(sourceFactory)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrNull()
        val soundProcessor = SoundAudioProcessor().also { it.albumPlayback = albumPlayback }
        audioProcessor = soundProcessor
        val renderers = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(context: Context, enableFloatOutput: Boolean, enableAudioTrackPlaybackParams: Boolean): AudioSink =
                DefaultAudioSink.Builder(context)
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessors(arrayOf<AudioProcessor>(soundProcessor))
                    .build()
        }
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cachedDataSource ?: sourceFactory))
            .build()
            .apply {
                setAudioAttributes(media3AudioAttributes, false)
                setWakeMode(C.WAKE_MODE_NETWORK)
                setHandleAudioBecomingNoisy(true)
                setPlaybackSpeed(playbackSpeed)
                addListener(playerListener)
            }
    }

    private fun releasePlayer() {
        if (::historyTracker.isInitialized) historyTracker.finish()
        cancelFade()
        pendingFadeIn = false
        player?.removeListener(playerListener)
        player?.release()
        player = null
        audioProcessor = null
    }

    private fun recoverFromError() {
        val track = currentTrack ?: return
        val url = currentUrl ?: return
        if (errorRetryCount >= MAX_ERROR_RETRIES) {
            Log.e(TAG, "Playback failed after $errorRetryCount retries")
            return
        }
        val retryPosition = player?.currentPosition ?: playbackPosition
        errorRetryCount++
        serviceScope.launch {
            delay(800)
            if (currentTrack?.id != track.id) return@launch
            Log.d(TAG, "Retrying playback ($errorRetryCount/$MAX_ERROR_RETRIES) from $retryPosition")
            startPlayback(track, url, autoplay = true, startPosition = retryPosition)
        }
    }

    fun togglePlayPause() {
        wasPlayingBeforeFocusLoss = false
        if (isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: playbackPosition
    }

    fun getCurrentTrack(): Track? = currentTrack

    fun getDuration(): Long {
        val duration = player?.duration ?: C.TIME_UNSET
        return if (duration == C.TIME_UNSET || duration <= 0L) {
            currentTrack?.duration ?: 0L
        } else {
            duration
        }
    }

    /** 设置播放速度（ExoPlayer 支持 0.1x - 5.0x）。 */
    fun setPlaybackSpeed(speed: Float) {
        playbackSpeed = speed.coerceIn(0.5f, 3f)
        player?.setPlaybackSpeed(playbackSpeed)
        updatePlaybackState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        )
    }

    fun getPlaybackSpeed(): Float = playbackSpeed

    fun isPlaying(): Boolean {
        return this.isPlaying
    }

    /**
     * 播放/暂停时的音量渐变：取消上一次渐变，从当前音量平滑过渡到目标音量。
     */
    private fun fadeToVolume(target: Float) {
        val current = player ?: return
        cancelFade()
        if (!ThemeSettings.fadeEnabled) {
            current.volume = target
            return
        }
        val startVolume = current.volume
        if (startVolume == target) return
        fadeJob = serviceScope.launch {
            val steps = (FADE_DURATION_MS / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                if (player !== current || !isActive) return@launch
                val t = i.toFloat() / steps
                val eased = t * t * (3f - 2f * t)
                current.volume = startVolume + (target - startVolume) * eased
                delay(FADE_STEP_MS)
            }
            if (player === current) current.volume = target
        }
    }

    /**
     * 暂停时先淡出再真正暂停；淡出期间若恢复播放会取消该任务并淡入。
     */
    private fun fadeOutThenPause(current: ExoPlayer) {
        cancelFade()
        val startVolume = current.volume
        fadeJob = serviceScope.launch {
            val steps = (FADE_DURATION_MS / FADE_STEP_MS).toInt().coerceAtLeast(1)
            for (i in 1..steps) {
                if (player !== current || !isActive) return@launch
                val t = i.toFloat() / steps
                val eased = t * t * (3f - 2f * t)
                current.volume = startVolume * (1f - eased)
                delay(FADE_STEP_MS)
            }
            if (player === current) {
                current.volume = 0f
                if (!isPlaying) {
                    current.playWhenReady = false
                }
            }
        }
    }

    private fun cancelFade() {
        fadeJob?.cancel()
        fadeJob = null
    }

    private fun resumePlayback() {
        Log.d(TAG, "resumePlayback")
        val current = player
        if (current == null ||
            current.playbackState == Player.STATE_IDLE ||
            current.playbackState == Player.STATE_ENDED
        ) {
            val track = currentTrack
            val url = currentUrl
            if (track != null && url != null) {
                if (!requestAudioFocus()) return
                val fromStart = current?.playbackState == Player.STATE_ENDED
                startPlayback(
                    track,
                    url,
                    autoplay = true,
                    startPosition = if (fromStart) 0L else playbackPosition
                )
                updateMetadata(track)
                showNotification()
                onPlaybackStateChanged?.invoke(true)
                lyriconManager?.setPlaybackState(true)
            }
            return
        }

        cancelFade()
        if (ThemeSettings.fadeEnabled) {
            current.volume = 0f
            if (current.playbackState == Player.STATE_READY) {
                pendingFadeIn = false
                fadeToVolume(1f)
            } else {
                pendingFadeIn = true
            }
        } else {
            pendingFadeIn = false
            current.volume = 1f
        }
        current.playWhenReady = true
        isPlaying = true
        updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
        showNotification()

        // 通知ViewModel播放状态变化
        onPlaybackStateChanged?.invoke(true)

        // 同步播放状态到Lyricon
        lyriconManager?.setPlaybackState(true)
    }

    private fun pausePlayback() {
        Log.d(TAG, "pausePlayback")
        val current = player
        val wasPlaying = isPlaying
        isPlaying = false
        pendingFadeIn = false
        if (current != null) {
            playbackPosition = current.currentPosition
            if (wasPlaying && ThemeSettings.fadeEnabled) {
                fadeOutThenPause(current)
            } else {
                cancelFade()
                current.volume = 1f
                current.playWhenReady = false
            }
        } else {
            cancelFade()
        }
        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
        showNotification()

        // 通知ViewModel播放状态变化
        onPlaybackStateChanged?.invoke(false)

        // 同步播放状态到Lyricon
        lyriconManager?.setPlaybackState(false)
    }

    private fun playNext() {
        Log.d(TAG, "playNext")
        // 通知ViewModel处理下一首
        onTrackChangedListener?.onNextTrack()
    }

    private fun playPrevious() {
        Log.d(TAG, "playPrevious")
        // 通知ViewModel处理上一首
        onTrackChangedListener?.onPreviousTrack()
    }

    fun clearPlayback() {
        mediaRequestGeneration++
        mediaNotificationDismissed = false
        favoriteJob?.cancel()
        favoriteTrack = null
        favorite = false
        currentTrack = null
        currentAudioTrack = null
        currentUrl = null
        currentIsPreview = false
        currentLyrics = null
        currentBitmap = null
        playlist = emptyList()
        isPlaying = false
        playbackPosition = 0L
        releasePlayer()
        lyriconManager?.setPlaybackState(false)
        mediaSession.setMetadata(null)
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        mediaSession.isActive = false
        abandonAudioFocus()
        stopForeground(true)
        onPlaybackStateChanged?.invoke(false)
    }

    fun setFavoriteState(track: Track, liked: Boolean) {
        favoriteTrack = track
        favorite = liked
        if (currentTrack == track) {
            updatePlaybackState(if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED)
            showNotification()
        }
    }

    private fun closeFromNotification() {
        mediaRequestGeneration++
        playbackPosition = getCurrentPosition()
        mediaNotificationDismissed = true
        getSharedPreferences("player_prefs", Context.MODE_PRIVATE).edit().putBoolean(PREF_MEDIA_DISMISSED, true).apply()
        isPlaying = false
        wasPlayingBeforeFocusLoss = false
        releasePlayer()
        lyriconManager?.setPlaybackState(false)
        mediaSession.setPlaybackState(PlaybackStateCompat.Builder()
            .setState(PlaybackStateCompat.STATE_STOPPED, playbackPosition, 0f).build())
        mediaSession.setMetadata(null)
        mediaSession.isActive = false
        abandonAudioFocus()
        stopForeground(true)
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(NOTIFICATION_ID)
        onPlaybackStateChanged?.invoke(false)
        if (onNotificationClose != null) onNotificationClose?.invoke() else {
            // The UI may be gone. Keep its saved queue/mode and only update the position.
            val platform = com.hypermusic.miuix.data.api.MusicPlatform.fromTrackSource(currentTrack?.source)
            val prefs = getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
            platform?.let { source -> runCatching {
                val key = "playback_snapshot_${source.storageKey}"
                val raw = prefs.getString(key, null) ?: return@runCatching
                val json = com.google.gson.JsonParser.parseString(raw).asJsonObject
                val track = json.getAsJsonObject("track") ?: json.getAsJsonObject("a")
                if (track?.get("id")?.asString == currentTrack?.id) {
                    json.remove("d"); json.addProperty("position", playbackPosition)
                    prefs.edit().putString(key, json.toString()).apply()
                }
            } }
            stopSelf()
        }
    }

    private fun favoriteFromNotification() {
        val track = currentTrack ?: return
        if (onNotificationFavorite != null) {
            onNotificationFavorite?.invoke(track)
            return
        }
        if (favoriteJob?.isActive == true) return
        favoriteJob = serviceScope.launch {
            val result = try {
                when (track.source) {
                    "qqmusic" -> QqMusicApi.isLiked(track).fold({ QqMusicApi.setLiked(track, !it) }, { kotlin.Result.failure(it) })
                    "netease" -> NeteaseApi.checkSongLiked(track.id).fold({ liked -> NeteaseApi.likeSong(track.id, !liked).map { success ->
                        check(success) { "收藏操作失败" }; !liked
                    } }, { kotlin.Result.failure(it) })
                    else -> kotlin.Result.success(toggleAdditionalFavorite(track))
                }
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { kotlin.Result.failure(e) }
            result.fold({ liked ->
                if (currentTrack == track) setFavoriteState(track, liked)
                android.widget.Toast.makeText(this@MediaPlaybackService, if (liked) "已收藏" else "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
            }, { error ->
                android.widget.Toast.makeText(this@MediaPlaybackService, error.message ?: "收藏操作失败", android.widget.Toast.LENGTH_SHORT).show()
            })
        }
    }

    private suspend fun toggleAdditionalFavorite(track: Track): Boolean {
        return when (track.source) {
            "bodian" -> {
                val client = com.hypermusic.miuix.data.api.BodianClient(this)
                val auth = com.hypermusic.miuix.data.api.BodianAuth(this, client)
                val account = auth.currentAccount()?.uid ?: error("请先登录波点音乐")
                val revision = auth.sessionRevision()
                val library = com.hypermusic.miuix.data.api.BodianLibrary(client, auth, com.hypermusic.miuix.data.api.BodianCatalog(client))
                var offset = 0
                var found = false
                var more = true
                while (more && offset < 20_000) {
                    val page = library.likedSongs(offset, 100)
                    check(auth.sessionRevision() == revision && auth.currentAccount()?.uid == account) { "波点音乐账号已切换，请重试" }
                    if (page.items.any { it.id == track.id }) { found = true; break }
                    more = page.hasMore
                    check(!more || page.nextOffset > offset) { "波点音乐收藏分页异常" }
                    offset = page.nextOffset
                }
                check(!more || found) { "波点音乐收藏列表过长" }
                library.setSongLiked(track.id, !found, account)
                check(auth.sessionRevision() == revision) { "账号已切换，请刷新收藏状态" }
                !found
            }
            "kugou" -> {
                val client = com.hypermusic.miuix.data.api.KugouClient(this)
                val auth = com.hypermusic.miuix.data.api.KugouAuth(this, client)
                val account = auth.account()?.userId ?: error("请先登录酷狗音乐")
                val generation = auth.sessionGeneration()
                val library = com.hypermusic.miuix.data.api.KugouLibrary(client, auth)
                val liked = library.isLiked(track)
                check(auth.sessionGeneration() == generation) { "酷狗音乐账号已切换，请重试" }
                library.setLiked(track, !liked, account)
                check(auth.sessionGeneration() == generation) { "账号已切换，请刷新收藏状态" }
                !liked
            }
            "qishui" -> {
                val client = com.hypermusic.miuix.data.api.QishuiClient(this)
                val auth = com.hypermusic.miuix.data.api.QishuiAuth(this, client)
                val account = auth.account()?.userId ?: error("请先登录汽水音乐")
                val generation = auth.sessionGeneration()
                val library = com.hypermusic.miuix.data.api.QishuiLibrary(client, auth)
                val id = track.id.removePrefix("qishui_")
                val liked = library.isSongLiked(id)
                check(auth.sessionGeneration() == generation) { "汽水音乐账号已切换，请重试" }
                library.setSongLiked(id, !liked, account)
                check(auth.sessionGeneration() == generation) { "账号已切换，请刷新收藏状态" }
                !liked
            }
            else -> error("不支持的音乐平台")
        }
    }

    private fun stopPlayback() {
        releasePlayer()
        currentUrl = null
        isPlaying = false
        playbackPosition = 0
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        abandonAudioFocus()
        stopForeground(true)
        stopSelf()
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: position=$position, isPlaying=$isPlaying")
        val current = player ?: return
        try {
            current.seekTo(position)
            playbackPosition = position
            updatePlaybackState(
                if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
            )

            // 同步进度到Lyricon
            lyriconManager?.seekTo(position)
        } catch (e: Exception) {
            Log.e(TAG, "seekTo failed: ${e.message}")
        }
    }

    fun updateLyrics(lyrics: com.hypermusic.miuix.data.model.SongLyrics?) {
        val track = currentTrack ?: return
        // 只有当歌曲未切换时才更新歌词
        if (lyriconManager?.isCurrentTrack(track.id) == true) {
            // 更新当前歌词
            currentLyrics = lyrics
            // 关闭「推送歌词到词幕」时不向词幕传歌词
            val payload = if (com.hypermusic.miuix.service.LyriconProviderManager.pushLyricsEnabled) lyrics else null
            lyriconManager?.setTrack(track, payload)
            Log.d(TAG, "Updated lyrics for track: ${track.title}, pushLyrics=${com.hypermusic.miuix.service.LyriconProviderManager.pushLyricsEnabled}, lines: ${payload?.lines?.size ?: 0}")
            
            // 重新更新metadata以包含歌词信息
            serviceScope.launch {
                withContext(Dispatchers.Main) {
                    updateMetadata(track)
                }
            }
        } else {
            Log.d(TAG, "Skipped lyrics update - track changed")
        }
    }

    private fun requestAudioFocus(): Boolean {
        val focusRequest = audioFocusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setAudioAttributes(
                AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                }
            )
            setOnAudioFocusChangeListener(audioFocusChangeListener)
            build()
        }.also { audioFocusRequest = it }

        val result = audioManager.requestAudioFocus(focusRequest)
        return result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonAudioFocus() {
        audioFocusRequest?.let {
            audioManager.abandonAudioFocusRequest(it)
        }
    }

    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                wasPlayingBeforeFocusLoss = isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                wasPlayingBeforeFocusLoss = isPlaying
                pausePlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                cancelFade()
                player?.volume = 0.3f
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                cancelFade()
                player?.volume = 1.0f
                if (wasPlayingBeforeFocusLoss) {
                    resumePlayback()
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        if (mediaNotificationDismissed) return
        val position = player?.currentPosition ?: playbackPosition
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .addCustomAction(ACTION_CLOSE, "关闭", R.drawable.ic_media_close)
            .addCustomAction(ACTION_FAVORITE, if (favoriteTrack == currentTrack && favorite) "取消收藏" else "收藏",
                if (favoriteTrack == currentTrack && favorite) R.drawable.ic_media_favorite_filled else R.drawable.ic_media_favorite)
            .setState(state, position, playbackSpeed)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun loadAlbumArtAndShowNotification(track: Track) {
        serviceScope.launch(Dispatchers.IO) {
            val bitmap = try {
                val local = com.hypermusic.miuix.data.download.OfflineLibrary.cover(track)
                if (local != null) BitmapFactory.decodeFile(local.absolutePath) else {
                val connection = URL(track.imgUrl).openConnection() as HttpURLConnection
                try {
                    connection.doInput = true
                    connection.connect()
                    connection.inputStream.use { BitmapFactory.decodeStream(it) }
                } finally {
                    connection.disconnect()
                }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load album art", e)
                null
            }
            withContext(Dispatchers.Main) {
                if (currentTrack != track) return@withContext
                currentBitmap = bitmap
                updateMetadata(track)
                showNotification()
            }
        }
    }

    private fun updateMetadata(track: Track) {
        if (mediaNotificationDismissed) return
        val metadataBuilder = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, track.title)
            .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, track.artist)
            .putString(MediaMetadataCompat.METADATA_KEY_ALBUM, track.album)
            .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, track.duration)

        currentBitmap?.let { bitmap ->
            metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
        }

        // 添加歌词信息到extras
        val lyricJson = buildLyricJson(track)
        if (lyricJson.isNotEmpty()) {
            // 使用反射或直接设置extras
            try {
                val extras = Bundle()
                extras.putString("lyricInfo", lyricJson)
                val field = MediaMetadataCompat.Builder::class.java.getDeclaredField("mBundle")
                field.isAccessible = true
                val bundle = field.get(metadataBuilder) as Bundle
                bundle.putBundle("android.media.metadata.EXTRAS", extras)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to add lyrics to metadata extras: ${e.message}")
            }
        }

        mediaSession.setMetadata(metadataBuilder.build())
    }

    /**
     * 构建歌词JSON字符串
     */
    private fun buildLyricJson(track: Track): String {
        val lyrics = currentLyrics ?: return ""
        if (lyrics.lines.isEmpty()) return ""

        // 构建LRC格式歌词
        val lrcBuilder = StringBuilder()
        for (line in lyrics.lines) {
            val minutes = line.startAt / 60000
            val seconds = (line.startAt % 60000) / 1000
            val milliseconds = (line.startAt % 1000) / 10
            lrcBuilder.append("[%02d:%02d.%02d]%s\n".format(minutes, seconds, milliseconds, line.text))
        }

        // 构建JSON
        val escapedLyric = lrcBuilder.toString()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")

        return """{"lyric":"$escapedLyric","songName":"${track.title}","artist":"${track.artist}"}"""
    }

    private fun showNotification() {
        if (mediaNotificationDismissed) return
        val track = currentTrack ?: return

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause,
                "暂停",
                getActionIntent(ACTION_PAUSE)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play,
                "播放",
                getActionIntent(ACTION_PLAY)
            )
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(track.title)
            .setContentText(track.artist)
            .setSmallIcon(R.drawable.ic_music_note)
            .setLargeIcon(currentBitmap)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_previous,
                    "上一首",
                    getActionIntent(ACTION_PREVIOUS)
                )
            )
            .addAction(playPauseAction)
            .addAction(
                NotificationCompat.Action(
                    R.drawable.ic_skip_next,
                    "下一首",
                    getActionIntent(ACTION_NEXT)
                )
            )
            .addAction(R.drawable.ic_media_close, "关闭", getActionIntent(ACTION_CLOSE))
            .addAction(if (favoriteTrack == track && favorite) R.drawable.ic_media_favorite_filled else R.drawable.ic_media_favorite,
                if (favoriteTrack == track && favorite) "取消收藏" else "收藏", getActionIntent(ACTION_FAVORITE))
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)
                    .setShowActionsInCompactView(0, 1, 2)
            )
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun getActionIntent(action: String): PendingIntent {
        val intent = Intent(this, MediaPlaybackReceiver::class.java).apply {
            this.action = action
        }
        return PendingIntent.getBroadcast(
            this, action.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun releaseMediaPlayer() {
        releasePlayer()
    }

    override fun onGetRoot(
        clientPackageName: String,
        clientUid: Int,
        rootHints: Bundle?
    ): BrowserRoot? {
        return BrowserRoot("root", null)
    }

    override fun onLoadChildren(
        parentId: String,
        result: Result<List<MediaBrowserCompat.MediaItem>>
    ) {
        result.sendResult(emptyList())
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN) {
            com.hypermusic.miuix.data.cache.ImageCaches.clearMemory()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        mediaSession.release()
        releasePlayer()
        abandonAudioFocus()
        currentBitmap?.recycle()
        currentBitmap = null
        
        // 释放Lyricon
        lyriconManager?.release()
        lyriconManager = null
    }
}
