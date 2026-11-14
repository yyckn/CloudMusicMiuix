package com.hypermusic.miuix.data.lyrics

import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class LyricRequestsTest {
    @Test fun cancellingLyricScopeAbortsBlockingHttpCall() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val received = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream()
                        var tail = ""
                        while (!tail.endsWith("\r\n\r\n")) {
                            val byte = input.read()
                            if (byte < 0) return@Thread
                            tail = (tail + byte.toChar()).takeLast(4)
                        }
                        received.countDown()
                        if (input.read() == -1) closed.countDown()
                    }
                } catch (_: Exception) { }
            }.apply { isDaemon = true; start() }
            val client = OkHttpClient.Builder().addInterceptor(LyricRequests).build()
            val request = Request.Builder().url("http://127.0.0.1:${server.localPort}/").build()
            val job = launch(Dispatchers.IO) {
                try { LyricRequests.scoped { client.newCall(request).execute().use { it.body?.string() } } }
                catch (_: java.io.IOException) { ensureActive() }
            }
            try {
                assertTrue("request did not start", received.await(5, TimeUnit.SECONDS))
                withTimeout(2000) { job.cancelAndJoin() }
                assertTrue("cancelling the coroutine must close the HTTP socket", closed.await(2, TimeUnit.SECONDS))
            } finally {
                job.cancel()
                client.dispatcher.cancelAll()
                server.close()
                worker.join(1000)
            }
        }
    }
}
