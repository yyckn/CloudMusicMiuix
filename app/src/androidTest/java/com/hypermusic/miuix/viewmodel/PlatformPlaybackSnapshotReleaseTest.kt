package com.hypermusic.miuix.viewmodel

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlatformPlaybackSnapshotReleaseTest {
    @Test fun oldReleasePlaylistRetainsTrackTypeAfterShrinking() {
        val song = Track("qq-mid", "测试", "歌手", "qqartist_mid", "专辑", "1", "qqmusic", "", "")
        val encoded = Gson().toJson(song)
        val json = """{"a":$encoded,"b":[$encoded],"c":[$encoded],"d":2300}"""
        val snapshotClass = Class.forName("com.hypermusic.miuix.viewmodel.PlatformPlaybackSnapshot")
        val snapshot = Gson().fromJson(json, snapshotClass)
        for (field in listOf("playlist", "original")) {
            val values = snapshotClass.getDeclaredField(field).apply { isAccessible = true }.get(snapshot) as List<*>
            assertTrue("$field must contain Track after Release shrinking", values.single() is Track)
            assertEquals("qq-mid", (values.single() as Track).id)
        }
    }
}
