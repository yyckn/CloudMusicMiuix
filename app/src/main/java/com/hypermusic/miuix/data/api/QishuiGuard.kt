package com.hypermusic.miuix.data.api

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

/** 汽水车机 ticket guard 所用 P-256 私钥及 PKCS#10 证书请求。 */
internal object QishuiGuard {
    data class Request(val privateKey: String, val csr: String)
    data class Ticket(val ticket: String, val certificate: String, val timestampSignature: String)
    private val base64 = Base64.getEncoder()
    private val decoder = Base64.getDecoder()
    private const val PREFIX = "/api/luna/v1/platform"

    private fun der(tag: Int, vararg parts: ByteArray): ByteArray {
        val bytes = parts.fold(byteArrayOf()) { a, b -> a + b }
        val len = when {
            bytes.size < 128 -> byteArrayOf(bytes.size.toByte())
            bytes.size < 256 -> byteArrayOf(0x81.toByte(), bytes.size.toByte())
            bytes.size < 65536 -> byteArrayOf(0x82.toByte(), (bytes.size ushr 8).toByte(), bytes.size.toByte())
            else -> error("DER 数据过大")
        }
        return byteArrayOf(tag.toByte()) + len + bytes
    }

    fun create(): Request {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        fun attribute(id: Int, value: String) = der(0x31, der(0x30, der(6, byteArrayOf(0x55, 4, id.toByte())), der(12, value.toByteArray())))
        val subject = der(0x30, attribute(3, "bd-ticket-guard"), attribute(11, ""), attribute(10, ""))
        val info = der(0x30, der(2, byteArrayOf(0)), subject, pair.public.encoded, der(0xa0))
        val signature = Signature.getInstance("SHA256withECDSA").apply { initSign(pair.private); update(info) }.sign()
        val algorithm = der(0x30, der(6, byteArrayOf(0x2a, 0x86.toByte(), 0x48, 0xce.toByte(), 0x3d, 0x04, 0x03, 0x02)))
        val csrBytes = der(0x30, info, algorithm, der(3, byteArrayOf(0) + signature))
        val pem = "-----BEGIN CERTIFICATE REQUEST-----\n" + base64.encodeToString(csrBytes).chunked(64).joinToString("\n") + "\n-----END CERTIFICATE REQUEST-----\n"
        return Request(base64.encodeToString(pair.private.encoded), base64.encodeToString(pem.toByteArray()))
    }

    fun parseTicket(encoded: String): Ticket {
        require(encoded.isNotBlank() && encoded.length <= 65536)
        val json = JsonParser.parseString(String(decoder.decode(encoded), Charsets.UTF_8)).asJsonObject
        fun value(name: String) = json.get(name)?.asString?.takeIf { it.isNotBlank() && it.length <= 16384 } ?: error("汽水音乐登录响应缺少 $name")
        val certificate = value("client_cert").let { if (it.contains("-----BEGIN")) base64.encodeToString(it.toByteArray()) else it }
        return Ticket(value("ticket"), certificate, value("ts_sign"))
    }

    fun headers(privateKey: String, ticket: Ticket, path: String, timestamp: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        require(path.startsWith('/') && !path.contains(".."))
        require(privateKey.length <= 16384 && ticket.ticket.isNotBlank() && ticket.certificate.isNotBlank() && ticket.timestampSignature.isNotBlank())
        val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(decoder.decode(privateKey)))
        val message = "ticket=${ticket.ticket}&path=$PREFIX$path&timestamp=$timestamp"
        val sign = Signature.getInstance("SHA256withECDSA").apply { initSign(key); update(message.toByteArray()) }.sign()
        val data = Gson().toJson(mapOf("ts_sign" to ticket.timestampSignature, "req_content" to "ticket,path,timestamp",
            "req_sign" to base64.encodeToString(sign), "timestamp" to timestamp))
        return mapOf("access-token" to ticket.ticket, "bd-ticket-guard-version" to "1", "bd-ticket-guard-client-cert" to ticket.certificate,
            "bd-ticket-guard-client-data" to base64.encodeToString(data.toByteArray()))
    }
}
