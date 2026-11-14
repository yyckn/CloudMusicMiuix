package com.hypermusic.miuix.data.api

import java.io.ByteArrayOutputStream

/** MQTT 5 packets used by QQ Music's native QR event channel. */
internal object QqQrMqtt {
    data class Event(val type: String?, val payload: String)
    data class Frame(val header: Int, val body: ByteArray)
    data class Connack(val reason: Int, val serverReference: String?)

    fun connack(frame: Frame): Connack {
        require(frame.header ushr 4 == 2 && frame.body.size >= 3)
        val data = frame.body
        val length = requireNotNull(readVariable(data, 2))
        var pos = 2 + length.second
        val end = pos + length.first
        require(end <= data.size)
        var reference: String? = null
        while (pos < end) {
            val id = data[pos++].toInt() and 255
            when (id) {
                0x1c, 0x1f, 0x03, 0x08, 0x12, 0x15, 0x1a, 0x26 -> {
                    fun readText(): String {
                        require(pos + 2 <= end)
                        val size = ((data[pos].toInt() and 255) shl 8) or (data[pos + 1].toInt() and 255)
                        pos += 2
                        require(size <= end - pos)
                        return data.copyOfRange(pos, pos + size).toString(Charsets.UTF_8).also { pos += size }
                    }
                    val value = readText()
                    if (id == 0x1c) reference = value
                    if (id == 0x26) readText()
                }
                0x13, 0x21, 0x22, 0x23 -> pos += 2
                0x02, 0x11, 0x18, 0x27 -> pos += 4
                0x01, 0x17, 0x19, 0x24, 0x25, 0x28, 0x29, 0x2a -> pos++
                0x09, 0x16 -> { require(pos + 2 <= end); val n = ((data[pos].toInt() and 255) shl 8) or (data[pos + 1].toInt() and 255); pos += 2 + n }
                0x0b -> { val n = requireNotNull(readVariable(data, pos)); pos += n.second }
                else -> error("Unsupported MQTT property $id")
            }
            require(pos <= end)
        }
        return Connack(data[1].toInt() and 255, reference)
    }

    /** Leave incomplete packets buffered until their next WebSocket frame arrives. */
    fun split(input: ByteArray): Pair<List<Frame>, ByteArray> {
        val frames = mutableListOf<Frame>()
        var start = 0
        while (start < input.size) {
            val length = readVariable(input, start + 1) ?: break
            val bodyStart = start + 1 + length.second
            if (length.first > input.size - bodyStart) break
            val end = bodyStart + length.first
            frames += Frame(input[start].toInt() and 255, input.copyOfRange(bodyStart, end))
            start = end
        }
        return frames to input.copyOfRange(start, input.size)
    }

    private fun readVariable(bytes: ByteArray, offset: Int): Pair<Int, Int>? {
        var value = 0
        var factor = 1
        repeat(4) { index ->
            if (offset + index >= bytes.size) return null
            val digit = bytes[offset + index].toInt() and 255
            value += (digit and 127) * factor
            if (digit and 128 == 0) return value to index + 1
            factor *= 128
        }
        throw IllegalArgumentException("Invalid MQTT variable integer")
    }

    fun publish(frame: Frame): Event {
        require(frame.header ushr 4 == 3)
        val data = frame.body
        var pos = 0
        fun readText(): String {
            require(pos + 2 <= data.size)
            val length = ((data[pos].toInt() and 255) shl 8) or (data[pos + 1].toInt() and 255)
            pos += 2
            require(length <= data.size - pos)
            return data.copyOfRange(pos, pos + length).toString(Charsets.UTF_8).also { pos += length }
        }
        readText() // topic
        if (((frame.header shr 1) and 3) > 0) { require(pos + 2 <= data.size); pos += 2 }
        val length = requireNotNull(readVariable(data, pos))
        pos += length.second
        val end = pos + length.first
        require(end <= data.size)
        var type: String? = null
        while (pos < end) {
            when (val property = data[pos++].toInt() and 255) {
                0x26 -> { val key = readText(); val value = readText(); if (key == "type") type = value }
                0x03, 0x08, 0x12, 0x15, 0x1a, 0x1c, 0x1f -> readText()
                0x13, 0x21, 0x22, 0x23 -> pos += 2
                0x02, 0x11, 0x18, 0x27 -> pos += 4
                0x01, 0x17, 0x19, 0x24, 0x25, 0x28, 0x29, 0x2a -> pos++
                0x09, 0x16 -> { require(pos + 2 <= end); val n = ((data[pos].toInt() and 255) shl 8) or (data[pos + 1].toInt() and 255); pos += 2 + n }
                0x0b -> { val n = requireNotNull(readVariable(data, pos)); pos += n.second }
                else -> throw IllegalArgumentException("Unsupported MQTT property $property")
            }
            require(pos <= end)
        }
        return Event(type, data.copyOfRange(end, data.size).toString(Charsets.UTF_8))
    }
    private fun variable(value: Int): ByteArray {
        require(value in 0..268435455)
        var remaining = value
        val result = ByteArrayOutputStream()
        do {
            var digit = remaining % 128
            remaining /= 128
            if (remaining > 0) digit = digit or 0x80
            result.write(digit)
        } while (remaining > 0)
        return result.toByteArray()
    }

    private fun text(value: String): ByteArray {
        val bytes = value.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 65535)
        return byteArrayOf((bytes.size ushr 8).toByte(), bytes.size.toByte()) + bytes
    }

    private fun properties(auth: String? = null, values: List<Pair<String, String>>): ByteArray {
        val data = ByteArrayOutputStream()
        if (auth != null) { data.write(0x15); data.write(text(auth)) }
        values.forEach { (key, value) -> data.write(0x26); data.write(text(key)); data.write(text(value)) }
        return variable(data.size()) + data.toByteArray()
    }

    private fun packet(header: Int, body: ByteArray): ByteArray = byteArrayOf(header.toByte()) + variable(body.size) + body

    fun connect(clientId: String, qrId: String): ByteArray = packet(0x10,
        text("MQTT") + byteArrayOf(5, 2, 0, 45) + properties("pass", listOf(
            "tmeAppID" to "qqmusic", "business" to "management", "hashTag" to qrId,
            "clientTag" to "management.user", "userID" to qrId
        )) + text(clientId))

    fun subscribe(qrId: String): ByteArray = packet(0x82, byteArrayOf(0, 1) + properties(values = listOf(
        "authorization" to "tmelogin", "pubsub" to "unicast"
    )) + text("management.qrcode_login/$qrId") + byteArrayOf(0))
}
