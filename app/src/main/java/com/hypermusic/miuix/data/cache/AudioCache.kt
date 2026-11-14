package com.hypermusic.miuix.data.cache

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.cache.ContentMetadata
import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track

/** 整个进程共享同一个 Media3 缓存实例，避免目录锁冲突。 */
@androidx.annotation.OptIn(UnstableApi::class)
object AudioCache {
    @Volatile private var instance: SimpleCache? = null

    fun get(context: Context): SimpleCache = instance ?: synchronized(this) {
        instance ?: SimpleCache(
            context.applicationContext.cacheDir.resolve("audio_media3"),
            LeastRecentlyUsedCacheEvictor(context.applicationContext.getSharedPreferences("cache_settings", Context.MODE_PRIVATE).getInt("audio_gb", 10).coerceIn(1, 100).toLong() * 1024 * 1024 * 1024),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { instance = it }
    }

    data class CachedAudio(val format: String, val bitrate: Int, val quality: String)
    private fun indexKey(track: Track, account: String?, requestedQuality: Int): String =
        "${track.source}:${track.id}:${account ?: "guest"}:$requestedQuality"

    fun record(context: Context, track: Track, account: String?, requestedQuality: Int, format: String, bitrate: Int, quality: String) {
        context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE).edit()
            .putString(indexKey(track, account, requestedQuality), Gson().toJson(CachedAudio(format, bitrate, quality))).apply()
    }

    fun forget(context: Context, track: Track, account: String?, requestedQuality: Int) {
        context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE).edit().remove(indexKey(track, account, requestedQuality)).apply()
    }

    /** Update metadata without changing the resource key of the audio already being cached. */
    fun updateActual(context: Context, track: Track, format: String, bitrate: Int, quality: String) {
        val prefs = context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE)
        val prefix = "${track.source}:${track.id}:"
        val gson = Gson()
        val editor = prefs.edit()
        prefs.all.forEach { (key, raw) ->
            if (key.startsWith(prefix) && raw is String) {
                val old = runCatching { gson.fromJson(raw, CachedAudio::class.java) }.getOrNull()
                if (old?.format == format) editor.putString(key, gson.toJson(old.copy(bitrate = bitrate, quality = quality)))
            }
        }
        editor.apply()
    }

    fun complete(context: Context, track: Track, account: String?, requestedQuality: Int): CachedAudio? = runCatching {
        val raw = context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE)
            .getString(indexKey(track, account, requestedQuality), null) ?: return null
        val info = Gson().fromJson(raw, CachedAudio::class.java) ?: return null
        val cache = get(context)
        val cacheKey = key(track.source, track.id, info.format)
        val length = ContentMetadata.getContentLength(cache.getContentMetadata(cacheKey))
        info.takeIf { length > 0 && cache.isCached(cacheKey, 0, length) }
    }.getOrNull()

    fun key(source: String, id: String, format: String): String =
        "audio:$source:$id:$format"
}
