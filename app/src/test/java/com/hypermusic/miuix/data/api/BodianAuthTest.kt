package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BodianAuthTest {
    @Test fun validatesConsistentIdentityWithoutLosingLargeIds() {
        val data = JsonParser.parseString("""{"uid":"12345678901234567890","userInfo":{"uid":"12345678901234567890","nickname":"测试"}}""").asJsonObject
        assertEquals("12345678901234567890", BodianAuth.parseAccount(data).uid)
        data.addProperty("uid", "123")
        assertThrows(IllegalStateException::class.java) { BodianAuth.parseAccount(data) }
    }
}
