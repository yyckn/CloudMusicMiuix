package com.hypermusic.miuix.data.api

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class PlatformDeviceIdConcurrencyTest {
    @Test fun concurrentClientsUseTheSamePersistedDeviceId() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "device_id_test_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        val executor = Executors.newFixedThreadPool(12)
        try {
            val start = CountDownLatch(1)
            val tasks = (0 until 12).map {
                executor.submit<Pair<String, String>> {
                    check(start.await(10, TimeUnit.SECONDS))
                    BodianClient(isolated).deviceId to QishuiClient(isolated).deviceId
                }
            }
            start.countDown()
            val ids = tasks.map { it.get(20, TimeUnit.SECONDS) }
            assertEquals(1, ids.map { it.first }.toSet().size)
            assertEquals(1, ids.map { it.second }.toSet().size)
            assertEquals(ids.first().first, BodianClient(isolated).deviceId)
            assertEquals(ids.first().second, QishuiClient(isolated).deviceId)
        } finally {
            executor.shutdownNow()
            target.deleteSharedPreferences(prefix + "bodian_prefs")
            target.deleteSharedPreferences(prefix + "qishui_prefs")
        }
    }
}
