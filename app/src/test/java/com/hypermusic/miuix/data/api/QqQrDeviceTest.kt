package com.hypermusic.miuix.data.api

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QqQrDeviceTest {
    @Test fun liveBootstrapProbeWhenRequested() {
        if (System.getenv("HYPERMUSIC_QQ_QR_PROBE") != "1") return
        val constructor = QqQrDevice::class.java.declaredConstructors.first { it.parameterTypes.size == 10 }
        constructor.isAccessible = true
        val device = constructor.newInstance("d9c0a82e47698123", "c012345678901234567890123456789a", "123456789012345", "xiaomi/iarim/sagit:10/eomam.200122.001/1234567:user/release-keys", "Linux 5.4.0-54-generic-abcd1234 (android-build@google.com)", "", "", 0L, "", "") as QqQrDevice
        val client = OkHttpClient.Builder().callTimeout(30, java.util.concurrent.TimeUnit.SECONDS).build()
        device.requestQimei(client)
        device.refreshSession(client)
        val qr = device.createQr(client)
        kotlinx.coroutines.runBlocking {
            QqQrChannel(client, qr.first).use { it.open() }
        }
        println("QQ QR bootstrap and MQTT subscription succeeded")
    }
    @Test fun deviceBootstrapAndNativeQrUseSameIdentity() {
        val constructor = QqQrDevice::class.java.declaredConstructors.first { it.parameterTypes.size == 10 }
        constructor.isAccessible = true
        val device = constructor.newInstance("androidid", "openudid", "123456789012345", "fingerprint", "kernel", "", "", 0L, "", "") as QqQrDevice
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val response = if (request.url.host == "api.tencentmusic.com") {
                assertEquals("GetQimei", request.header("method"))
                """{"data":{"data":{"q16":"q16-test","q36":"q36-test"}}}"""
            } else {
                val buffer = okio.Buffer(); request.body!!.writeTo(buffer)
                val body = buffer.readUtf8()
                assertTrue(body.contains("q16-test"))
                when {
                    body.contains("GetSession") -> """{"code":0,"req_0":{"code":0,"data":{"session":{"uid":"u1","sid":"s1"}}}}"""
                    else -> """{"code":0,"req_0":{"code":0,"data":{"qrcodeID":"qr1","qrcode":"iVBORw0KGgo="}}}"""
                }
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(response.toResponseBody("application/json".toMediaType())).build()
        }.build()
        device.requestQimei(client)
        device.refreshSession(client)
        assertEquals("qr1", device.createQr(client).first)
        assertEquals("u1", device.uid)
        assertEquals("s1", device.sid)
    }
}
