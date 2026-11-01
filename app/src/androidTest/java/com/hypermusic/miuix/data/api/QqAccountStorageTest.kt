package com.hypermusic.miuix.data.api

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QqAccountStorageTest {
    @Test fun migratesAndKeepsAccountSelectionsIndependent() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = base.getSharedPreferences("qq_account_test_isolated", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = prefs
        }
        try {
            val first = QqMusicApi.Credential("101", "first-key", "first-uin", 2)
            val second = QqMusicApi.Credential("202", "second-key", "second-uin", 2)
            prefs.edit().putString("credential", Gson().toJson(first)).putString("account_name", "First")
                .putBoolean("multi_account_enabled", true).commit()
            QqMusicApi.init(context)
            assertEquals("101", QqMusicApi.dataAccountId)
            assertEquals("First", QqMusicApi.accountName)
            assertNull(prefs.getString("credential", null))
            val accounts = QqMusicApi.accounts + QqMusicApi.Account(second, "Second")
            prefs.edit().putString("accounts", Gson().toJson(accounts)).commit()
            QqMusicApi.init(context)
            assertTrue(QqMusicApi.switchPlaybackAccount("202"))
            assertEquals("101", QqMusicApi.dataAccountId)
            assertEquals("202", QqMusicApi.playbackAccountId)
            QqMusicApi.init(context)
            assertEquals("202", QqMusicApi.playbackAccountId)
            assertTrue(QqMusicApi.switchDataAccount("202"))
            assertEquals("Second", QqMusicApi.accountName)
            QqMusicApi.logout()
            assertEquals("101", QqMusicApi.dataAccountId)
            assertEquals("101", QqMusicApi.playbackAccountId)
            assertEquals(1, QqMusicApi.accounts.size)
            assertTrue(prefs.getBoolean("multi_account_enabled", false))
            QqMusicApi.init(context)
            assertEquals("First", QqMusicApi.accountName)
            assertFalse(QqMusicApi.switchDataAccount("missing"))
        } finally {
            prefs.edit().clear().commit()
        }
    }
}
