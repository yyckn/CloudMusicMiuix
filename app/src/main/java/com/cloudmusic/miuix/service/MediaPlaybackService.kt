package com.cloudmusic.miuix.service

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
import android.media.MediaPlayer
import android.os.Binder
import android.os.Bundle
import android.support.v4.media.MediaBrowserCompat
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.MediaBrowserServiceCompat
import com.cloudmusic.miuix.MainActivity
import com.cloudmusic.miuix.R
import com.cloudmusic.miuix.data.model.Track
import kotlinx.coroutines.*
import java.net.HttpURLConnection
import java.net.URL

class MediaPlaybackService : MediaBrowserServiceCompat() {

    private lateinit var mediaSession: MediaSessionCompat
    private lateinit var audioManager: AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var mediaPlayer: MediaPlayer? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var currentTrack: Track? = null
    private var playlist: List<Track> = emptyList()
    private var isPlaying = false
    private var playbackPosition = 0L
    private var currentBitmap: Bitmap? = null
    private var currentLyrics: com.cloudmusic.miuix.data.model.SongLyrics? = null
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
        const val ACTION_PLAY = "com.cloudmusic.miuix.PLAY"
        const val ACTION_PAUSE = "com.cloudmusic.miuix.PAUSE"
        const val ACTION_NEXT = "com.cloudmusic.miuix.NEXT"
        const val ACTION_PREVIOUS = "com.cloudmusic.miuix.PREVIOUS"
        private const val TAG = "MediaPlaybackService"
    }

    override fun onBind(intent: Intent): android.os.IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
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
        serviceScope.launch {
            try {
                if (requestAudioFocus()) {
                    updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                    
                    // 第一阶段：立即用元数据（无歌词）更新 Lyricon
                    lyriconManager?.setTrack(track)
                    lyriconManager?.setPlaybackState(true)
                    
                    mediaPlayer?.release()
                    mediaPlayer = MediaPlayer().apply {
                        setDataSource(url)
                        setAudioAttributes(
                            android.media.AudioAttributes.Builder()
                                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build()
                        )
                        setOnPreparedListener {
                            start()
                            this@MediaPlaybackService.isPlaying = true
                            updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                        }
                        setOnCompletionListener {
                            val duration = this.duration.toLong()
                            val position = this.currentPosition.toLong()
                            // 只有当播放位置接近总时长时才认为是正常完成
                            // 避免因流媒体问题导致的误触发
                            if (duration > 0 && position >= duration * 0.9) {
                                Log.d(TAG, "Track completed normally: position=$position, duration=$duration")
                                playNext()
                            } else {
                                Log.d(TAG, "Track completion ignored: position=$position, duration=$duration")
                            }
                        }
                        setOnErrorListener { _, _, _ ->
                            this@MediaPlaybackService.isPlaying = false
                            updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                            true
                        }
                        prepareAsync()
                    }
                    
                    loadAlbumArtAndShowNotification(track)
                }
            } catch (e: Exception) {
                Log.e(TAG, "playTrackWithUrl error", e)
            }
        }
    }

    fun prepareTrackWithUrl(track: Track, url: String) {
        currentTrack = track
        serviceScope.launch {
            try {
                updatePlaybackState(PlaybackStateCompat.STATE_BUFFERING)
                
                mediaPlayer?.release()
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(url)
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    setOnPreparedListener {
                        // 准备完成，但不自动播放
                        updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                    }
                    setOnCompletionListener {
                        val duration = this.duration.toLong()
                        val position = this.currentPosition.toLong()
                        // 只有当播放位置接近总时长时才认为是正常完成
                        if (duration > 0 && position >= duration * 0.9) {
                            Log.d(TAG, "Track completed normally: position=$position, duration=$duration")
                            playNext()
                        } else {
                            Log.d(TAG, "Track completion ignored: position=$position, duration=$duration")
                        }
                    }
                    setOnErrorListener { _, _, _ ->
                        this@MediaPlaybackService.isPlaying = false
                        updatePlaybackState(PlaybackStateCompat.STATE_ERROR)
                        true
                    }
                    prepareAsync()
                }
                
                loadAlbumArtAndShowNotification(track)
            } catch (e: Exception) {
                Log.e(TAG, "prepareTrackWithUrl error", e)
            }
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
        return mediaPlayer?.currentPosition?.toLong() ?: 0L
    }

    fun getDuration(): Long {
        return mediaPlayer?.duration?.toLong() ?: 0L
    }

    fun isPlaying(): Boolean {
        return mediaPlayer?.isPlaying ?: false
    }

    private fun resumePlayback() {
        Log.d(TAG, "resumePlayback")
        mediaPlayer?.let { player ->
            if (!player.isPlaying) {
                player.start()
                isPlaying = true
                updatePlaybackState(PlaybackStateCompat.STATE_PLAYING)
                showNotification()
                
                // 通知ViewModel播放状态变化
                onPlaybackStateChanged?.invoke(true)
                
                // 同步播放状态到Lyricon
                lyriconManager?.setPlaybackState(true)
            }
        }
    }

    private fun pausePlayback() {
        Log.d(TAG, "pausePlayback")
        mediaPlayer?.let { player ->
            if (player.isPlaying) {
                player.pause()
                playbackPosition = player.currentPosition.toLong()
                isPlaying = false
                updatePlaybackState(PlaybackStateCompat.STATE_PAUSED)
                showNotification()
                
                // 通知ViewModel播放状态变化
                onPlaybackStateChanged?.invoke(false)
                
                // 同步播放状态到Lyricon
                lyriconManager?.setPlaybackState(false)
            }
        }
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
        mediaPlayer?.release()
        mediaPlayer = null
        isPlaying = false
        playbackPosition = 0
        updatePlaybackState(PlaybackStateCompat.STATE_STOPPED)
        abandonAudioFocus()
        stopForeground(true)
        stopSelf()
    }

    fun seekTo(position: Long) {
        Log.d(TAG, "seekTo: position=$position, isPlaying=$isPlaying")
        mediaPlayer?.let { player ->
            try {
                player.seekTo(position.toInt())
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
    }

    fun updateLyrics(lyrics: com.cloudmusic.miuix.data.model.SongLyrics?) {
        val track = currentTrack ?: return
        // 只有当歌曲未切换时才更新歌词
        if (lyriconManager?.isCurrentTrack(track.id) == true) {
            // 更新当前歌词
            currentLyrics = lyrics
            lyriconManager?.setTrack(track, lyrics)
            Log.d(TAG, "Updated lyrics for track: ${track.title}, lines: ${lyrics?.lines?.size ?: 0}")
            
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
        val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).run {
            setAudioAttributes(
                AudioAttributes.Builder().run {
                    setUsage(AudioAttributes.USAGE_MEDIA)
                    setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    build()
                }
            )
            setOnAudioFocusChangeListener(audioFocusChangeListener)
            build()
        }

        val result = audioManager.requestAudioFocus(focusRequest)
        audioFocusRequest = focusRequest
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
                mediaPlayer?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mediaPlayer?.setVolume(1.0f, 1.0f)
                if (wasPlayingBeforeFocusLoss) {
                    resumePlayback()
                    wasPlayingBeforeFocusLoss = false
                }
            }
        }
    }

    private fun updatePlaybackState(state: Int) {
        val position = mediaPlayer?.currentPosition?.toLong() ?: playbackPosition
        val playbackState = PlaybackStateCompat.Builder()
            .setActions(
                PlaybackStateCompat.ACTION_PLAY or
                PlaybackStateCompat.ACTION_PAUSE or
                PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                PlaybackStateCompat.ACTION_SEEK_TO
            )
            .setState(state, position, 1.0f)
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
        mediaPlayer?.release()
        mediaPlayer = null
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
        releaseMediaPlayer()
        abandonAudioFocus()
        currentBitmap?.recycle()
        currentBitmap = null
        
        // 释放Lyricon
        lyriconManager?.release()
        lyriconManager = null
    }
}
