package com.hypermusic.miuix.data.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import java.io.Closeable
import java.security.SecureRandom

/** One QR session owns one MQTT connection; close it when the QR expires or is replaced. */
internal class QqQrChannel(private val client: OkHttpClient, private val qrId: String) : Closeable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val events = Channel<QqQrMqtt.Event>(Channel.UNLIMITED)
    private var socket: WebSocket? = null

    suspend fun open() {
        var path = "/ws/handshake"
        repeat(4) { attempt ->
            val frames = Channel<QqQrMqtt.Frame>(Channel.UNLIMITED)
            var pending = byteArrayOf()
            val listener = object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    val id = "${System.currentTimeMillis()}${SecureRandom().nextInt(9000) + 1000}"
                    if (!webSocket.send(QqQrMqtt.connect(id, qrId).toByteString())) frames.close(IllegalStateException("MQTT 连接发送失败"))
                }
                override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                    try {
                        pending += bytes.toByteArray()
                        check(pending.size <= 1_048_576) { "MQTT 数据过大" }
                        val result = QqQrMqtt.split(pending)
                        pending = result.second
                        result.first.forEach { frames.trySend(it) }
                    } catch (error: Exception) { frames.close(error); webSocket.cancel() }
                }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { frames.close(t) }
                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { frames.close() }
            }
            val request = Request.Builder().url("wss://mu.y.qq.com$path").header("Sec-WebSocket-Protocol", "mqtt").build()
            val current = client.newWebSocket(request, listener)
            socket = current
            try {
                val ack = QqQrMqtt.connack(withTimeout(20_000) { frames.receive() })
                if (ack.reason != 0) {
                    current.cancel()
                    val reference = ack.serverReference.orEmpty()
                    check(ack.reason in listOf(0x9c, 0x9d) && attempt < 3 && reference.matches(Regex("[A-Za-z0-9._:-]{1,128}"))) { "MQTT 连接被拒绝（${ack.reason}）" }
                    val parts = path.trimEnd('/').split('/').toMutableList()
                    if (parts.last().contains(':')) parts[parts.lastIndex] = reference else parts.add(reference)
                    path = parts.joinToString("/")
                    return@repeat
                }
                check(current.send(QqQrMqtt.subscribe(qrId).toByteString())) { "MQTT 订阅发送失败" }
                withTimeout(20_000) {
                    while (true) {
                        val frame = frames.receive()
                        when (frame.header ushr 4) {
                            9 -> { check(frame.body.isNotEmpty() && (frame.body.last().toInt() and 255) < 0x80) { "MQTT 订阅失败" }; break }
                            3 -> events.send(QqQrMqtt.publish(frame))
                        }
                    }
                }
                scope.launch {
                    try { for (frame in frames) if (frame.header ushr 4 == 3) events.send(QqQrMqtt.publish(frame)) }
                    catch (error: Exception) { events.close(error) }
                    finally { events.close() }
                }
                scope.launch { while (true) { delay(30_000); if (!current.send(byteArrayOf(0xc0.toByte(), 0).toByteString())) break } }
                return
            } catch (error: Exception) { current.cancel(); throw error }
        }
        error("MQTT 重定向次数过多")
    }

    suspend fun nextEvent(): QqQrMqtt.Event = events.receive()

    override fun close() {
        socket?.cancel()
        socket = null
        events.close()
        scope.cancel()
    }
}
