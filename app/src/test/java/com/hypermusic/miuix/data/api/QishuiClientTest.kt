package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class QishuiClientTest {
    @Test fun mutationsRequireAnExplicitSuccessStatus() {
        fun check(json: String) = QishuiClient.checkMutationResponse(JsonParser.parseString(json).asJsonObject)
        check("""{"err_no":0}""")
        check("""{"data":{"error_code":0}}""")
        assertTrue(runCatching { check("""{}""") }.isFailure)
        assertTrue(runCatching { check("""{"data":{}}""") }.isFailure)
        assertTrue(runCatching { check("""{"err_no":1}""") }.isFailure)
        assertTrue(runCatching { check("""{"err_no":0,"data":{"error_code":12}}""") }.isFailure)
        assertEquals(28001008L, QishuiClient.responseCode(JsonParser.parseString("""{"err_no":0,"data":{"error_code":28001008}}""").asJsonObject))
    }
    @Test fun accountContextDoesNotMutateInputOrLeakIntoGuestReads() {
        val body = JsonParser.parseString("""{"common_params":{"user_id":"old","scene":"search"},"q":"test"}""").asJsonObject
        val signedIn = QishuiClient.readPayload(body, "new")
        assertEquals("new", signedIn.getAsJsonObject("common_params")["user_id"].asString)
        assertEquals("search", signedIn.getAsJsonObject("common_params")["scene"].asString)
        assertEquals("old", body.getAsJsonObject("common_params")["user_id"].asString)
        assertFalse(QishuiClient.readPayload(body, null).getAsJsonObject("common_params").has("user_id"))
    }
}
