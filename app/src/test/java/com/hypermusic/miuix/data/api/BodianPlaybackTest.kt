package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BodianPlaybackTest {
    @Test fun preservesAndValidatesPreviewBoundaries() {
        val range = BodianPlayback.previewRange(JsonParser.parseString("""{"start":0,"end":29}""").asJsonObject)
        assertEquals(0L, range.startSeconds)
        assertEquals(29L, range.endSeconds)
        assertThrows(IllegalStateException::class.java) {
            BodianPlayback.previewRange(JsonParser.parseString("""{"start":1000,"end":500}""").asJsonObject)
        }
    }
}
