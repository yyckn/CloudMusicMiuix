package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QishuiSessionStoreTest {
    @Test fun corruptedSessionDoesNotCrashDownloadEntry() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        org.junit.Assume.assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".oobecheck"))
        val prefs = context.getSharedPreferences("qishui_secure_session", 0)
        val old = prefs.getString("session", null)
        val store = androidx.lifecycle.ViewModelStore()
        try {
            prefs.edit().putString("session", "invalid-test-session").commit()
            instrumentation.runOnMainSync {
                NeteaseApi.init(context)
                val owner = object : androidx.lifecycle.ViewModelStoreOwner { override val viewModelStore = store }
                val provider = androidx.lifecycle.ViewModelProvider(owner, androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.getInstance(context.applicationContext as android.app.Application))
                val model = provider[com.hypermusic.miuix.viewmodel.MusicPlayerViewModel::class.java]
                val track = com.hypermusic.miuix.data.model.Track("qishui_123", "测试", "", "", "", "", "qishui", "", "")
                model.downloadCurrentTrack(track)
                assertEquals("无法读取汽水音乐登录信息", model.toastMessage.tryReceive().getOrNull())
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            prefs.edit().apply { if (old == null) remove("session") else putString("session", old) }.commit()
        }
    }

    @Test fun encryptedRoundTripAndLogout() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".oobecheck"))
        val store = QishuiSessionStore(context)
        val value = QishuiSessionStore.Session("device", "user123", "nickname", "", "private-secret", "ticket-secret", "certificate", "ts", "access", "open", 123456L)
        try {
            store.save(value)
            assertEquals(value, QishuiSessionStore(context).read())
            val raw = context.getSharedPreferences("qishui_secure_session", 0).getString("session", "").orEmpty()
            assertFalse(raw.contains("private-secret"))
            assertFalse(raw.contains("ticket-secret"))
            store.clear()
            assertNull(store.read())
        } finally { store.clear() }
    }
}
