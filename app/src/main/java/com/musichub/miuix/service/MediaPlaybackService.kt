package com.musichub.miuix.service

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
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.musichub.miuix.MainActivity
import com.musichub.miuix.R
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

class MediaPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTrack: Track? = null
    private var currentUrl: String? = null
    private var playlist: List<Track> = emptyList()
    private var isPlaying = false
    private var playbackPosition = 0L
    private var playbackSpeed = 1.0f
    private var completionHandled = false
    private var errorRetryCount = 0
    private var fadeJob: Job? = null
    private var pendingFadeIn = false
    private val audioEffectController = AudioEffectController()

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

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                }
                Player.STATE_READY -> {
                    // 播放就绪后兜底同步一次音频会话，确保音效挂载
                    player?.let { audioEffectController.onAudioSessionChanged(it.audioSessionId) }
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

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            audioEffectController.onAudioSessionChanged(audioSessionId)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(
                TAG,
                "onPlayerError: ${error.errorCodeName} (${error.errorCode}) at ${player?.currentPosition}",
                error
            )
            isPlaying = false
            onPlaybackStateChanged?.invoke(false)
            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
            lyriconManager?.setPlaybackState(false)
            recoverFromError()
        }
    }
    private var currentBitmap: Bitmap? = null
    private var currentLyrics: com.musichub.miuix.data.model.SongLyrics? = null
    private var wasPlayingBeforeFocusLoss = false
    
    // 回调接口，用于通知ViewModel歌曲切换
    var onTrackChangedListener: OnTrackChangedListener? = null
    
    // 回调接口，用于通知ViewModel播放状态变化
    var onPlaybackStateChanged: ((Boolean) -> Unit)? = null
    
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
        const val MEDIA_SESSION_TAG = "CloudMusicMediaSession"
        const val ACTION_PLAY = "com.musichub.miuix.PLAY"
        const val ACTION_PAUSE = "com.musichub.miuix.PAUSE"
        const val ACTION_NEXT = "com.musichub.miuix.NEXT"
        const val ACTION_PREVIOUS = "com.musichub.miuix.PREVIOUS"
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
        ThemeSettings.init(this)
        createNotificationChannel()
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
            isActive = true
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
            stopPlayback()
        }

        override fun onSeekTo(pos: Long) {
            seekTo(pos)
        }
    }

    fun setPlaylist(tracks: List<Track>) {
        playlist = tracks
    }

    fun playTrack(track: Track) {
        currentTrack = track
        serviceScope.launch {
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

    fun playTrackWithUrl(track: Track, url: String) {
        currentTrack = track
        errorRetryCount = 0
        serviceScope.launch {
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

    fun prepareTrackWithUrl(track: Track, url: String) {
        currentTrack = track
        errorRetryCount = 0
        serviceScope.launch {
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
        currentUrl = url
        playbackPosition = startPosition
        completionHandled = false
        isPlaying = autoplay

        releasePlayer()
        val newPlayer = createPlayer()
        player = newPlayer
        pendingFadeIn = autoplay && ThemeSettings.fadeEnabled
        newPlayer.volume = if (pendingFadeIn) 0f else 1f

        updatePlaybackState(
            if (autoplay) PlaybackStateCompat.STATE_BUFFERING
            else PlaybackStateCompat.STATE_PAUSED
        )

        newPlayer.setMediaItem(MediaItem.fromUri(url))
        if (startPosition > 0L) {
            newPlayer.seekTo(startPosition)
        }
        newPlayer.prepare()
        newPlayer.playWhenReady = autoplay

        if (autoplay) {
            onPlaybackStateChanged?.invoke(true)
        }
    }

    private fun createPlayer(): ExoPlayer {
        val dataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
            .setUserAgent(USER_AGENT)
            .setDefaultRequestProperties(mapOf("Referer" to "https://music.163.com"))
        return ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .apply {
                setAudioAttributes(media3AudioAttributes, false)
                setWakeMode(C.WAKE_MODE_NETWORK)
                setHandleAudioBecomingNoisy(true)
                setPlaybackSpeed(playbackSpeed)
                addListener(playerListener)
                audioEffectController.setPresetById(ThemeSettings.audioEffectPresetId)
                audioEffectController.onAudioSessionChanged(audioSessionId)
            }
    }

    private fun releasePlayer() {
        cancelFade()
        pendingFadeIn = false
        audioEffectController.release()
        player?.removeListener(playerListener)
        player?.release()
        player = null
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
        playbackSpeed = speed.coerceIn(0.1f, 5f)
        player?.setPlaybackSpeed(playbackSpeed)
        updatePlaybackState(
            if (isPlaying) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        )
    }

    /** 切换音效预设。 */
    fun setAudioEffectPreset(presetId: String) {
        audioEffectController.setPresetById(presetId)
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
                requestAudioFocus()
                val fromStart = current?.playbackState == Player.STATE_ENDED
                startPlayback(
                    track,
                    url,
                    autoplay = true,
                    startPosition = if (fromStart) 0L else playbackPosition
                )
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

    fun updateLyrics(lyrics: com.musichub.miuix.data.model.SongLyrics?) {
        val track = currentTrack ?: return
        // 只有当歌曲未切换时才更新歌词
        if (lyriconManager?.isCurrentTrack(track.id) == true) {
            // 更新当前歌词
            currentLyrics = lyrics
            // 关闭「推送歌词到词幕」时不向词幕传歌词
            val payload = if (com.musichub.miuix.service.LyriconProviderManager.pushLyricsEnabled) lyrics else null
            lyriconManager?.setTrack(track, payload)
            Log.d(TAG, "Updated lyrics for track: ${track.title}, pushLyrics=${com.musichub.miuix.service.LyriconProviderManager.pushLyricsEnabled}, lines: ${payload?.lines?.size ?: 0}")
            
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
        val position = player?.currentPosition ?: playbackPosition
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, playbackSpeed)
            .build()
        mediaSession.setPlaybackState(playbackState)
    }

    private fun loadAlbumArtAndShowNotification(track: Track) {
        serviceScope.launch(Dispatchers.IO) {
            try {
                val url = URL(track.imgUrl)
                val connection = url.openConnection() as HttpURLConnection
                connection.doInput = true
                connection.connect()
                val inputStream = connection.inputStream
                currentBitmap = BitmapFactory.decodeStream(inputStream)
                inputStream.close()
                connection.disconnect()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load album art", e)
                currentBitmap = null
            }

            withContext(Dispatchers.Main) {
                updateMetadata(track)
                showNotification()
            }
        }
    }

    private fun updateMetadata(track: Track) {
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
