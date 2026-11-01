package com.hypermusic.miuix.data.api

import org.junit.Assert.*
import org.junit.Test

class UserCredentialLinkTest {
    @Test fun roundTripBothPlatforms() {
        for ((platform, credential) in listOf("wy" to """{"MUSIC_U":"secret"}""", "qq" to """{"musicid":"123","musickey":"key","encryptUin":"uin","loginType":2}""")) {
            val payload = UserCredentialLink.decode(UserCredentialLink.encode(platform, credential))
            assertEquals(platform, payload.platform)
            assertEquals(credential, payload.credential)
        }
    }
    @Test fun rejectsUnexpectedUriAndCredential() {
        for (value in listOf("https://user/wy?data=e30", "hypermusic://user/other?data=e30", "hypermusic://user/wy?data=e30&x=y", "hypermusic://user/wy?data=e30#x")) {
            assertTrue(runCatching { UserCredentialLink.decode(value) }.isFailure)
        }
        assertTrue(runCatching { UserCredentialLink.encode("wy", """{"MUSIC_U":"bad;other=x"}""") }.isFailure)
        assertTrue(runCatching { UserCredentialLink.encode("wy", """{"MUSIC_U":"secret","unexpected":"x"}""") }.isFailure)
        assertTrue(runCatching { UserCredentialLink.encode("qq", """{"musicid":"123","musickey":"key","encryptUin":"uin","loginType":9}""") }.isFailure)
        assertTrue(runCatching { UserCredentialLink.encode("qq", """{"musicid":"123","musickey":"key","encryptUin":"uin","extra":"x"}""") }.isFailure)
        assertTrue(runCatching { UserCredentialLink.decode("hypermusic://user/wy?data=" + "a".repeat(5000)) }.isFailure)
    }
}
