package com.hypermusic.miuix.data.lyrics

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AmllCancellationTest {
    @Test fun cancellingRequestClosesTheNetworkCall() = runBlocking {
        ServerSocket(0).use { server ->
            server.soTimeout = 5000
            val received = CountDownLatch(1)
            val closed = CountDownLatch(1)
            val worker = Thread {
                try {
                    server.accept().use { socket ->
                        socket.soTimeout = 5000
                        val input = socket.getInputStream()
                        var previous = ""
                        while (!previous.endsWith("\r\n\r\n")) {
                            val value = input.read()
                            if (value < 0) return@Thread
                            previous = (previous + value.toChar()).takeLast(4)
                        }
                        received.countDown()
                        if (input.read() == -1) closed.countDown()
                    }
                } catch (_: Exception) { }
            }.apply { isDaemon = true; start() }
            val job = launch(Dispatchers.IO) { AmllLyricsApi.request("http://127.0.0.1:${server.localPort}/") }
            try {
                assertTrue("request was not received", received.await(5, TimeUnit.SECONDS))
                job.cancelAndJoin()
                assertTrue("cancelled request did not close its socket", closed.await(5, TimeUnit.SECONDS))
            } finally {
                job.cancel()
                server.close()
                worker.join(1000)
            }
        }
    }
}
