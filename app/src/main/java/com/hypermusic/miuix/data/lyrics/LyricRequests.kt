package com.hypermusic.miuix.data.lyrics

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asContextElement
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody
import okio.ForwardingSource
import okio.buffer

/** Scope only lyric/search calls, so choosing a winner never cancels playback or account requests. */
internal object LyricRequests : Interceptor {
    private class Calls {
        private val active = mutableSetOf<Call>()
        private var cancelled = false
        @Synchronized fun add(call: Call) { if (cancelled) call.cancel() else active.add(call) }
        @Synchronized fun remove(call: Call) { active.remove(call) }
        @Synchronized fun cancel() { cancelled = true; active.forEach { it.cancel() }; active.clear() }
    }
    private val current = ThreadLocal<Calls?>()

    suspend fun <T> scoped(block: suspend () -> T): T = coroutineScope {
        val calls = Calls()
        // A separate dispatcher can cancel a synchronous OkHttp call while its IO thread is blocked.
        val watcher = launch(Dispatchers.Default, start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { calls.cancel() }
        }
        try { withContext(current.asContextElement(calls)) { block() } }
        finally { watcher.cancel(); calls.cancel() }
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val calls = current.get() ?: return chain.proceed(chain.request())
        val call = chain.call()
        calls.add(call)
        try {
            val response = chain.proceed(chain.request())
            val body = response.body ?: run { calls.remove(call); return response }
            val source = object : ForwardingSource(body.source()) {
                override fun close() { try { super.close() } finally { calls.remove(call) } }
                override fun read(sink: okio.Buffer, byteCount: Long): Long =
                    super.read(sink, byteCount).also { if (it == -1L) calls.remove(call) }
            }.buffer()
            return response.newBuilder().body(object : ResponseBody() {
                override fun contentType() = body.contentType()
                override fun contentLength() = body.contentLength()
                override fun source() = source
            }).build()
        } catch (error: Throwable) { calls.remove(call); throw error }
    }
}
