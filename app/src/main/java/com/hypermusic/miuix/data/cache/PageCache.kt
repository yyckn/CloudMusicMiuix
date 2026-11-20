package com.hypermusic.miuix.data.cache

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

/** Page JSON only: independently disposable, never contains downloaded audio or offline assets. */
internal class PageCacheStore(private val directory: File, private val maxBytes: Long = 32L * 1024 * 1024,
    private val maxFiles: Int = 120) {
    private var lastAccess = 0L
    private fun touch(file: File) { lastAccess = maxOf(System.currentTimeMillis(), lastAccess + 1); file.setLastModified(lastAccess) }
    private fun file(key: String) = directory.resolve(MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) } + ".json")
    @Synchronized fun read(key: String): String? = runCatching {
        val file = file(key)
        if (!file.isFile || file.length() > 8 * 1024 * 1024) return null
        file.readText().also { touch(file) }
    }.getOrNull()
    @Synchronized fun write(key: String, json: String) {
        if (json.toByteArray().size > minOf(8L * 1024 * 1024, maxBytes)) return
        directory.mkdirs()
        val destination = file(key)
        val temporary = File(directory, "${destination.name}.tmp")
        temporary.writeText(json)
        try { Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
        catch (_: Exception) { Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING) }
        touch(destination)
        var bytes = 0L
        directory.listFiles().orEmpty().filter { it.extension == "json" }.sortedByDescending { it.lastModified() }
            .forEachIndexed { index, file -> bytes += file.length(); if (index >= maxFiles || bytes > maxBytes) file.delete() }
    }
    @Synchronized fun size(): Long = directory.listFiles().orEmpty().sumOf { if (it.isFile) it.length() else 0L }
    @Synchronized fun clear() { directory.listFiles().orEmpty().filter { it.isFile }.forEach { check(it.delete()) { "无法清空页面缓存" } } }
}

object PageCache {
    internal val gson = Gson()
    private val stores = java.util.concurrent.ConcurrentHashMap<String, PageCacheStore>()
    private fun store(context: Context) = stores.getOrPut(context.cacheDir.absolutePath) { PageCacheStore(context.cacheDir.resolve("page_cache")) }
    fun key(context: Context, platform: MusicPlatform, kind: String, id: String = ""): String {
        val account = when (platform) {
            MusicPlatform.NETEASE -> NeteaseApi.getCurrentUserId()?.toString()
            MusicPlatform.QQ -> QqMusicApi.dataAccountId
            else -> { AdditionalAccounts.revision.intValue; AdditionalAccounts(context, platform).dataId }
        } ?: "<guest>"
        return "v1|${platform.storageKey}|$account|$kind|$id"
    }
    internal suspend inline fun <reified T> read(context: Context, key: String): T? = withContext(Dispatchers.IO) {
        val json = readJson(context, key) ?: return@withContext null
        runCatching { gson.fromJson<T>(json, object : TypeToken<T>() {}.type) }.getOrNull()
    }
    internal suspend fun readJson(context: Context, key: String) = withContext(Dispatchers.IO) { store(context).read(key) }
    suspend fun write(context: Context, key: String, value: Any) = withContext(Dispatchers.IO) {
        runCatching { store(context).write(key, gson.toJson(value)) }; Unit
    }
    suspend fun size(context: Context): Long = withContext(Dispatchers.IO) { store(context).size() }
    suspend fun clear(context: Context) = withContext(Dispatchers.IO) { store(context).clear() }
}

@Keep internal data class CachedAlbum(val info: AlbumInfo, val tracks: List<Track>)
@Keep internal data class CachedArtist(val info: ArtistInfo?, val songs: List<Track>, val albums: List<AlbumInfo>,
    val songsOffset: Int, val albumsOffset: Int, val moreSongs: Boolean, val moreAlbums: Boolean,
    val songsCursor: String = "", val albumsCursor: String = "")
@Keep internal data class CachedQqHome(val playlists: List<RecommendPlaylist>, val charts: List<QqMusicApi.TopChart>,
    val features: Map<String, PlaylistDetail>)

/** Preserve equal instances while applying the server's authoritative membership and order. */
internal fun <T, K> reconcilePageItems(old: List<T>, fresh: List<T>, key: (T) -> K): List<T> {
    val previous = old.groupBy(key).mapValues { it.value.toMutableList() }
    return fresh.map { item -> previous[key(item)]?.let { candidates ->
        val index = candidates.indexOfFirst { it == item }
        if (index >= 0) candidates.removeAt(index) else item
    } ?: item }
}
/** Repeated songs remain separate rows, without changing keys for ordinary moves/inserts. */
internal fun <T> stablePageItemKeys(items: List<T>, key: (T) -> String): List<String> {
    val counts = mutableMapOf<String, Int>()
    return items.map { val id = key(it); val occurrence = counts[id] ?: 0; counts[id] = occurrence + 1; "$id#$occurrence" }
}
