package com.hypermusic.miuix.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class BodianClientTest {
    @Test fun signaturesMatchDesktopProtocol() {
        assertEquals("9766798a24ebea6856f55e49bd601374", BodianClient.sign("/api/test", linkedMapOf("keyword" to "音乐 test", "uid" to "-1", "token" to "", "timestamp" to "123"), ""))
        assertEquals("8b1d4505ba0d7f7a673abe1550fbaff6", BodianClient.sign("/api/test", linkedMapOf("uid" to "-1", "token" to "", "timestamp" to "123"), "{\"id\":1}"))
    }
}
