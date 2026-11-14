package com.hypermusic.miuix.data.cache

import android.content.Context
import android.app.ActivityManager
import coil.ImageLoader
import coil.disk.DiskCache

/** 独立磁盘缓存，便于分别统计和清理封面、缩略图及歌手头像。 */
object ImageCaches {
    enum class Kind(val folder: String, val percent: Int) { COVER("cover", 45), THUMBNAIL("thumbnail", 40), ARTIST("artist", 15) }
    private val loaders = mutableMapOf<Kind, ImageLoader>()
    /** 只释放内存中的图片，不创建新的加载器，也不影响磁盘缓存。 */
    @Synchronized fun clearMemory() {
        loaders.values.forEach { it.memoryCache?.clear() }
    }
    @Synchronized fun get(context: Context, kind: Kind): ImageLoader = loaders.getOrPut(kind) {
        val app = context.applicationContext
        val maxMb = app.getSharedPreferences("cache_settings", Context.MODE_PRIVATE).getInt("images_mb", 1024).coerceIn(256, 10240)
        val maxBytes = maxMb.toLong() * 1024 * 1024 * kind.percent / 100
        val lowRam = app.getSystemService(ActivityManager::class.java)?.isLowRamDevice == true
        ImageLoader.Builder(app).memoryCache { coil.memory.MemoryCache.Builder(app).maxSizePercent(if (lowRam) 0.02 else 0.05).build() }.diskCache {
            DiskCache.Builder().directory(app.cacheDir.resolve("image_cache/${kind.folder}")).maxSizeBytes(maxBytes).build()
        }.build()
    }
}
