package com.hypermusic.miuix.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class KugouClientTest {
    @Test fun webSignatureSortsParametersLikeReference() {
        assertEquals("5bcf957a6291282f86a420aadacbeb5c", KugouClient.webSignature(mapOf("type" to "1", "appid" to "1001", "plat" to "4")))
    }
}
