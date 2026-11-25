package com.hypermusic.miuix.data.audio.separation

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.json.JSONObject

data class VocalSeparationCacheEntry(
    val id: String,
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val modelId: String,
    val sampleRate: Int,
    val channels: Int,
    val totalFrames: Long,
    val durationMs: Long,
    val vocalsBytes: Long,
    val instrumentalBytes: Long,
    val createdAtMs: Long,
    val lastUsedAtMs: Long,
    val backend: String = "",
) {
    val totalBytes: Long get() = vocalsBytes + instrumentalBytes
}

/**
 * 分离音轨缓存：filesDir/vocal_separation_cache/<id>/{meta.json, vocals.pcm, instrumental.pcm}。
 * 音轨固定为 44.1 kHz、16 bit、双声道交错 PCM。
 */
class VocalSeparationCache(
    private val rootDir: File,
    private val capacityGb: () -> Int = { DEFAULT_CAPACITY_GB },
) {
    constructor(context: Context) : this(
        File(context.filesDir, "vocal_separation_cache"),
        { context.getSharedPreferences("cache_settings", Context.MODE_PRIVATE)
            .getInt(CAPACITY_PREF, DEFAULT_CAPACITY_GB) },
    )

    companion object {
        const val SAMPLE_RATE = 44_100
        const val CHANNELS = 2
        const val CAPACITY_PREF = "vocal_separation_gb"
        const val DEFAULT_CAPACITY_GB = 2
        private const val VOCALS_FILE = "vocals.pcm"
        private const val INST_FILE = "instrumental.pcm"
        private const val META_FILE = "meta.json"

        fun entryId(songKey: String, modelId: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest("$songKey|$modelId".toByteArray(Charsets.UTF_8))
            return digest.joinToString("") { "%02x".format(it) }.take(32)
        }
    }

    fun entryDirectory(id: String): File = File(rootDir, id)
    fun vocalsFile(id: String): File = File(entryDirectory(id), VOCALS_FILE)
    fun instrumentalFile(id: String): File = File(entryDirectory(id), INST_FILE)
    private fun metaFile(id: String): File = File(entryDirectory(id), META_FILE)

    fun hasValidEntry(id: String): Boolean {
        val vocals = vocalsFile(id)
        val inst = instrumentalFile(id)
        return metaFile(id).isFile && vocals.isFile && inst.isFile &&
            vocals.length() > 0L && inst.length() == vocals.length()
    }

    @Synchronized
    fun findEntry(songKey: String, modelId: String): VocalSeparationCacheEntry? {
        val id = entryId(songKey, modelId)
        if (!hasValidEntry(id)) return null
        val meta = readMeta(id) ?: return null
        touch(id)
        return meta
    }

    @Synchronized
    fun listEntries(): List<VocalSeparationCacheEntry> {
        if (!rootDir.isDirectory) return emptyList()
        return rootDir.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { dir -> dir.name.takeIf(::hasValidEntry)?.let(::readMeta) }
            ?.sortedByDescending { it.lastUsedAtMs }
            ?: emptyList()
    }

    fun totalCacheBytes(): Long {
        if (!rootDir.isDirectory) return 0L
        return rootDir.walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    @Synchronized
    fun commitEntry(
        songKey: String,
        title: String,
        artist: String,
        album: String,
        modelId: String,
        totalFrames: Int,
        durationMs: Long,
        vocalsTemp: File,
        instTemp: File,
        backend: String = "",
    ): VocalSeparationCacheEntry {
        rootDir.mkdirs()
        val id = entryId(songKey, modelId)
        val dir = entryDirectory(id)
        dir.deleteRecursively()
        dir.mkdirs()
        val vocalsDst = vocalsFile(id)
        val instDst = instrumentalFile(id)
        check(vocalsTemp.renameTo(vocalsDst)) { "无法写入人声音轨缓存" }
        check(instTemp.renameTo(instDst)) { "无法写入伴奏音轨缓存" }
        val now = System.currentTimeMillis()
        val entry = VocalSeparationCacheEntry(
            id = id,
            songKey = songKey,
            title = title,
            artist = artist,
            album = album,
            modelId = modelId,
            sampleRate = SAMPLE_RATE,
            channels = CHANNELS,
            totalFrames = totalFrames.toLong(),
            durationMs = durationMs.coerceAtLeast(totalFrames.toLong() * 1000L / SAMPLE_RATE),
            vocalsBytes = vocalsDst.length(),
            instrumentalBytes = instDst.length(),
            createdAtMs = now,
            lastUsedAtMs = now,
            backend = backend,
        )
        writeMeta(entry)
        trimToBudget(keepId = id)
        return entry
    }

    @Synchronized
    fun deleteEntry(id: String): Boolean = entryDirectory(id).deleteRecursively()

    @Synchronized
    fun clearAll(): Boolean = rootDir.deleteRecursively()

    private fun touch(id: String) {
        val current = readMeta(id) ?: return
        val now = System.currentTimeMillis()
        if (now - current.lastUsedAtMs > 10_000L) writeMeta(current.copy(lastUsedAtMs = now))
    }

    @Synchronized
    fun trimToBudget(keepId: String? = null) {
        val maxBytes = capacityGb().coerceIn(1, 100) * 1024L * 1024L * 1024L
        var total = totalCacheBytes()
        if (total <= maxBytes) return
        for (entry in listEntries().sortedBy { it.lastUsedAtMs }) {
            if (total <= maxBytes) break
            if (entry.id == keepId) continue
            if (deleteEntry(entry.id)) total = (total - entry.totalBytes).coerceAtLeast(0L)
        }
    }

    private fun readMeta(id: String): VocalSeparationCacheEntry? {
        val file = metaFile(id)
        if (!file.isFile) return null
        val obj = runCatching { JSONObject(file.readText()) }.getOrNull() ?: return null
        val vLen = vocalsFile(id).length()
        val iLen = instrumentalFile(id).length()
        return VocalSeparationCacheEntry(
            id = id,
            songKey = obj.optString("songKey"),
            title = obj.optString("title"),
            artist = obj.optString("artist"),
            album = obj.optString("album"),
            modelId = obj.optString("modelId", VocalSeparationModel.DEFAULT.id),
            sampleRate = obj.optInt("sampleRate", SAMPLE_RATE),
            channels = obj.optInt("channels", CHANNELS),
            totalFrames = obj.optLong("totalFrames", vLen / 4L),
            durationMs = obj.optLong("durationMs", 0L),
            vocalsBytes = vLen,
            instrumentalBytes = iLen,
            createdAtMs = obj.optLong("createdAtMs", 0L),
            lastUsedAtMs = obj.optLong("lastUsedAtMs", 0L),
            backend = obj.optString("backend"),
        )
    }

    private fun writeMeta(entry: VocalSeparationCacheEntry) {
        metaFile(entry.id).writeText(JSONObject().apply {
            put("songKey", entry.songKey)
            put("title", entry.title)
            put("artist", entry.artist)
            put("album", entry.album)
            put("modelId", entry.modelId)
            put("sampleRate", entry.sampleRate)
            put("channels", entry.channels)
            put("totalFrames", entry.totalFrames)
            put("durationMs", entry.durationMs)
            put("createdAtMs", entry.createdAtMs)
            put("lastUsedAtMs", entry.lastUsedAtMs)
            put("backend", entry.backend)
        }.toString())
    }
}
