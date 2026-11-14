package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

class QishuiGuardTest {
    private data class Node(val start: Int, val body: Int, val end: Int)
    private fun node(bytes: ByteArray, start: Int): Node {
        var p = start + 1
        val lengthByte = bytes[p++].toInt() and 255
        var length = lengthByte
        if (lengthByte and 0x80 != 0) {
            val count = lengthByte and 0x7f
            length = 0
            repeat(count) { length = length * 256 + (bytes[p++].toInt() and 255) }
        }
        return Node(start, p, p + length)
    }

    @Test fun generatedCsrAndGuardSignatureUseSameKey() {
        val request = QishuiGuard.create()
        val pem = String(Base64.getDecoder().decode(request.csr))
        val csr = Base64.getMimeDecoder().decode(pem.lines().filterNot { it.startsWith("-----") }.joinToString(""))
        val root = node(csr, 0)
        val info = node(csr, root.body)
        val version = node(csr, info.body)
        val subject = node(csr, version.end)
        val spki = node(csr, subject.end)
        val publicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(csr.copyOfRange(spki.start, spki.end)))
        val algorithm = node(csr, info.end)
        val signed = node(csr, algorithm.end)
        val csrVerifier = Signature.getInstance("SHA256withECDSA").apply { initVerify(publicKey); update(csr.copyOfRange(info.start, info.end)) }
        assertTrue(csrVerifier.verify(csr.copyOfRange(signed.body + 1, signed.end)))
        val headers = QishuiGuard.headers(request.privateKey, QishuiGuard.Ticket("ticket-value", "cert", "ts"), "/me/info/", 1234)
        val data = JsonParser.parseString(String(Base64.getDecoder().decode(headers.getValue("bd-ticket-guard-client-data")))).asJsonObject
        assertEquals(1234L, data.get("timestamp").asLong)
        val verifier = Signature.getInstance("SHA256withECDSA").apply {
            initVerify(publicKey); update("ticket=ticket-value&path=/api/luna/v1/platform/me/info/&timestamp=1234".toByteArray())
        }
        assertTrue(verifier.verify(Base64.getDecoder().decode(data.get("req_sign").asString)))
    }
}
