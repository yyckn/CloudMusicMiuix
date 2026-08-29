package com.cloudmusic.miuix.service

import android.content.Context
import android.util.Log
import com.cloudmusic.miuix.data.model.SongLyrics
import com.cloudmusic.miuix.data.model.Track
import io.github.proify.lyricon.lyric.model.LyricWord
import io.github.proify.lyricon.lyric.model.RichLyricLine
import io.github.proify.lyricon.lyric.model.Song
import io.github.proify.lyricon.provider.ConnectionListener
import io.github.proify.lyricon.provider.LyriconFactory
import io.github.proify.lyricon.provider.LyriconProvider

class LyriconProviderManager(private val context: Context) {
    
    private var provider: LyriconProvider? = null
    private val TAG = "LyriconProviderManager"
    private var enabled = true
    private var currentTrackId: String? = null
    private var currentLyrics: List<RichLyricLine>? = null
    private var currentSongTitle: String = ""
    
    private val connectionListener = object : ConnectionListener {
        override fun onConnected(provider: LyriconProvider) {
            Log.d(TAG, "Lyricon connected")
        }
        
        override fun onReconnected(provider: LyriconProvider) {
            Log.d(TAG, "Lyricon reconnected")
        }
        
        override fun onDisconnected(provider: LyriconProvider) {
            Log.d(TAG, "Lyricon disconnected")
        }
        
        override fun onConnectTimeout(provider: LyriconProvider) {
            Log.e(TAG, "Lyricon connection timeout")
        }
    }
    
    fun initialize() {
        try {
            provider = LyriconFactory.createProvider(context)
            provider?.service?.addConnectionListener(connectionListener)
            provider?.register()
            Log.d(TAG, "Lyricon provider registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LyriconProvider", e)
        }
    }
    
    fun setTrack(track: Track, lyrics: SongLyrics? = null) {
        if (!enabled) return
        val player = provider?.player ?: return
        
        Log.d(TAG, "setTrack: id=${track.id}, title=${track.title}, artist=${track.artist}, duration=${track.duration}, lyrics=${lyrics?.lines?.size ?: 0} lines, isPureMusic=${lyrics?.isPureMusic}")
        
        // 更新当前歌曲ID
        currentTrackId = track.id
        
        // 清除旧歌词
        currentLyrics = null
        currentSongTitle = ""
        
        val parsedLyrics = if (lyrics?.isPureMusic == true) {
            // 纯音乐：整首歌从头到尾输出为一句"歌名 - 歌手"
            listOf(
                RichLyricLine(
                    begin = 0L,
                    end = track.duration,
                    text = "${track.title} - ${track.artist}",
                    words = null,
                    translation = null
                )
            )
        } else {
            parseLyrics(lyrics, track.title, track.artist, track.duration)
        }
        
        currentLyrics = parsedLyrics
        currentSongTitle = "${track.title} - ${track.artist}"
        
        val song = Song(
            id = track.id.toString(),
            name = track.title,
            artist = track.artist,
            duration = track.duration,
            lyrics = parsedLyrics
        )
        
        // 打印详细日志
        Log.d(TAG, "Song: id=${song.id}, name=${song.name}, artist=${song.artist}, duration=${song.duration}")
        Log.d(TAG, "Lyrics count: ${parsedLyrics?.size ?: 0}")
        parsedLyrics?.take(3)?.forEachIndexed { index, line ->
            Log.d(TAG, "  Line $index: begin=${line.begin}, end=${line.end}, text=${line.text}, translation=${line.translation}, words=${line.words?.size ?: 0}")
        }
        
        player.setSong(song)
        player.setDisplayTranslation(true)
        Log.d(TAG, "setSong called successfully")
    }
    
    // 检查歌曲是否已切换
    fun isCurrentTrack(trackId: String): Boolean {
        return currentTrackId == trackId
    }
    
    fun setPlaybackState(isPlaying: Boolean) {
        if (!enabled) return
        try {
            provider?.player?.setPlaybackState(isPlaying)
            Log.d(TAG, "Set playback state: $isPlaying")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set playback state", e)
        }
    }
    
    fun setPosition(positionMs: Long) {
        if (!enabled) return
        try {
            provider?.player?.setPosition(positionMs)
            
            // 查找当前应该显示的歌词
            val lyrics = currentLyrics
            if (lyrics != null && lyrics.isNotEmpty()) {
                val currentIndex = lyrics.indexOfLast { it.begin <= positionMs }
                val currentLine = if (currentIndex >= 0) lyrics[currentIndex] else null
                val nextLine = lyrics.getOrNull(currentIndex + 1)
                
                Log.d(TAG, "setPosition: $positionMs | current: [${currentLine?.begin}-${currentLine?.end}] \"${currentLine?.text}\" | next: [${nextLine?.begin}] \"${nextLine?.text}\"")
            } else {
                Log.d(TAG, "setPosition: $positionMs | no lyrics")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set position", e)
        }
    }
    
    fun seekTo(positionMs: Long) {
        if (!enabled) return
        try {
            provider?.player?.seekTo(positionMs)
            Log.d(TAG, "Seek to: $positionMs")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to seek", e)
        }
    }
    
    fun sendText(text: String) {
        if (!enabled) return
        try {
            provider?.player?.sendText(text)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send text", e)
        }
    }
    
    private fun parseLyrics(lyrics: SongLyrics?, songTitle: String, songArtist: String, songDuration: Long): List<RichLyricLine>? {
        val result = mutableListOf<RichLyricLine>()
        
        // 在第一句歌词开始之前，传递"歌名 - 歌手"作为歌词
        val firstLineStart = lyrics?.lines?.firstOrNull()?.startAt ?: 0L
        if (firstLineStart > 0) {
            val titleText = "$songTitle - $songArtist"
            result.add(
                RichLyricLine(
                    begin = 0L,
                    end = firstLineStart,
                    text = titleText,
                    words = null,
                    translation = null
                )
            )
            Log.d(TAG, "Added title line: $titleText, start=0, end=$firstLineStart")
        }
        
        if (lyrics == null || lyrics.lines.isEmpty()) {
            return result.takeIf { it.isNotEmpty() }
        }
        
        Log.d(TAG, "Parsing ${lyrics.lines.size} lyrics, first line start: ${lyrics.lines.firstOrNull()?.startAt}")
        
        lyrics.lines.forEachIndexed { index, line ->
            // 转换逐字歌词
            val words = line.words?.map { word ->
                LyricWord(
                    text = word.text,
                    begin = word.startAt,
                    end = word.endAt
                )
            }
            
            // 计算结束时间
            val endAt = if (index < lyrics.lines.size - 1) {
                lyrics.lines[index + 1].startAt
            } else {
                songDuration
            }
            
            // 对歌手行添加括号
            val lineText = if (line.isArtistLine) {
                addBracketsToArtistText(line.text)
            } else {
                line.text
            }
            
            if (index < 5) {
                Log.d(TAG, "Line $index: text=$lineText, start=${line.startAt}, end=$endAt, isArtist=${line.isArtistLine}")
            }
            
            result.add(
                RichLyricLine(
                    begin = line.startAt,
                    end = endAt,
                    text = lineText,
                    words = words,
                    translation = line.translation.takeIf { it.isNotEmpty() }
                )
            )
        }
        
        return result
    }
    
    fun release() {
        try {
            provider?.service?.removeConnectionListener(connectionListener)
            provider?.unregister()
            provider?.destroy()
            provider = null
            Log.d(TAG, "Lyricon provider released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release LyriconProvider", e)
        }
    }
    
    /**
     * 为歌手行文本添加括号（用于传递给词幕）
     */
    private fun addBracketsToArtistText(text: String): String {
        val trimmed = text.trim()
        
        // 检查是否已经有括号
        val hasBrackets = (trimmed.startsWith("（") && trimmed.endsWith("）")) ||
                         (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
                         (trimmed.startsWith("【") && trimmed.endsWith("】"))
        
        // 如果没有括号，加括号
        return if (!hasBrackets) {
            "（$trimmed）"
        } else {
            trimmed
        }
    }
}
