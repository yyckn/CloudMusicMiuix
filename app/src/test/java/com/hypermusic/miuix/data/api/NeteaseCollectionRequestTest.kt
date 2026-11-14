package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import okhttp3.FormBody
import org.junit.Assert.*
import org.junit.Test
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class NeteaseCollectionRequestTest {
    @Test fun collectionRoutesAndCsrfUseCapturedSession() {
        for ((kind, favorite, action) in listOf(
            Triple("playlist", true, "subscribe"), Triple("playlist", false, "unsubscribe"),
            Triple("album", true, "sub"), Triple("album", false, "unsub")
        )) {
            var plaintext = ""
            val cookie = "MUSIC_U=test-session; __csrf=session-csrf; other=value"
            val request = NeteaseCollectionRequest.build(kind, 123L, favorite, cookie) {
                plaintext = it
                mapOf("params" to "encrypted", "encSecKey" to "key")
            }
            val payload = JsonParser.parseString(plaintext).asJsonObject
            assertEquals(123L, payload.get("id").asLong)
            assertEquals("session-csrf", payload.get("csrf_token").asString)
            assertEquals("/weapi/$kind/$action", request.url.encodedPath)
            assertEquals("session-csrf", request.url.queryParameter("csrf_token"))
            assertEquals(cookie, request.header("Cookie"))
            assertEquals("https://music.163.com", request.header("Origin"))
            assertFalse(request.url.toString().contains("test-session"))
        }
    }

    @Test fun pcFallbackUsesMatchingSignedPathAndAction() {
        for (favorite in listOf(true, false)) {
            val request = NeteaseCollectionRequest.buildPcPlaylist(123L, favorite, "MUSIC_U=test-session")
            val action = if (favorite) "subscribe" else "unsubscribe"
            val encrypted = (request.body as FormBody).value(0).chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec("e82ckenh8dichen8".toByteArray(), "AES"))
            val plaintext = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
            assertTrue(plaintext.startsWith("/api/playlist/$action-36cd479b6b5-"))
            assertEquals("/eapi/playlist/$action", request.url.encodedPath)
            assertEquals("os=pc; MUSIC_U=test-session", request.header("Cookie"))
        }
    }
}
