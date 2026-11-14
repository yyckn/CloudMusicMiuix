package com.hypermusic.miuix.data.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class QqQrMqttTest {
    private fun ByteArray.hex() = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    @Test fun packetsMatchReferenceProtocol() {
        assertEquals("10830100044d5154540502002d7015000470617373260008746d654170704944000771716d75736963260008627573696e657373000a6d616e6167656d656e7426000768617368546167000571722d6964260009636c69656e74546167000f6d616e6167656d656e742e75736572260006757365724944000571722d69640006636c69656e74", QqQrMqtt.connect("client", "qr-id").hex())
        assertEquals("824f00012c26000d617574686f72697a6174696f6e0008746d656c6f67696e2600067075627375620007756e6963617374001d6d616e6167656d656e742e7172636f64655f6c6f67696e2f71722d696400", QqQrMqtt.subscribe("qr-id").hex())
    }

    @Test fun fragmentedPublishPreservesEventAndRemainder() {
        val frame = "30230003612f620d2600047479706500047363616e7b2261223a317d".chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        // Correct the remaining-length byte to cover the full MQTT body.
        frame[1] = (frame.size - 2).toByte()
        val first = QqQrMqtt.split(frame.copyOfRange(0, 10))
        assertEquals(0, first.first.size)
        assertArrayEquals(frame.copyOfRange(0, 10), first.second)
        val second = QqQrMqtt.split(first.second + frame.copyOfRange(10, frame.size))
        assertEquals(1, second.first.size)
        val event = QqQrMqtt.publish(second.first.single())
        assertEquals("scan", event.type)
        assertEquals("{\"a\":1}", event.payload)
        assertEquals(0, second.second.size)
    }

    @Test fun connackParsesRedirectAndRejectsTruncatedProperties() {
        val reference = "node:443".toByteArray()
        val body = byteArrayOf(0, 0x9c.toByte(), (reference.size + 3).toByte(), 0x1c, 0, reference.size.toByte()) + reference
        val result = QqQrMqtt.connack(QqQrMqtt.Frame(0x20, body))
        assertEquals(0x9c, result.reason)
        assertEquals("node:443", result.serverReference)
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            QqQrMqtt.connack(QqQrMqtt.Frame(0x20, body.copyOf(body.size - 1)))
        }
    }
}
