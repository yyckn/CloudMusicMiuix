package com.hypermusic.miuix.data.cache

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AudioCacheInstrumentedTest {
    @Test fun onlyCompleteEntriesAreAvailableOffline() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val track = Track(id = "cache-test-unique", title = "test", artist = "test", artistId = "", album = "", albumId = "", source = "test", sourceUrl = "", imgUrl = "")
        val format = "guest:128000:mp3"
        val key = AudioCache.key(track.source, track.id, format)
        val cache = AudioCache.get(context)
        try {
            AudioCache.record(context, track, null, 0, format, 128000, "标准品质")
            assertNull(AudioCache.complete(context, track, null, 0))
            val data = ByteArray(128 * 1024) { (it % 251).toByte() }
            val source = CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory { ByteArrayDataSource(data) }.createDataSource()
            val spec = DataSpec.Builder().setUri(Uri.parse("https://cache.invalid/test")).setKey(key).build()
            CacheWriter(source, spec, null, null).cache()
            assertNotNull(AudioCache.complete(context, track, null, 0))
            val offline = CacheDataSource.Factory().setCache(cache)
                .setUpstreamDataSourceFactory { ByteArrayDataSource(byteArrayOf(1)) }.createDataSource()
            val offlineSpec = DataSpec.Builder().setUri(Uri.parse("https://127.0.0.1/cached")).setKey(key).build()
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            offline.open(offlineSpec)
            try {
                while (true) {
                    val count = offline.read(buffer, 0, buffer.size)
                    if (count < 0) break
                    output.write(buffer, 0, count)
                }
            } finally { offline.close() }
            assertArrayEquals(data, output.toByteArray())
            assertNull(AudioCache.complete(context, track, "other-account", 0))
            assertNull(AudioCache.complete(context, track, null, 1))
        } finally {
            cache.removeResource(key)
            AudioCache.forget(context, track, null, 0)
        }
    }
}

