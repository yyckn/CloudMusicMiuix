package com.hypermusic.miuix.data.download

import org.junit.Assert.*
import org.junit.Test

class DownloadPolicyTest {
    @Test fun promptOnlyForConnectedNonWifiWhenEnabled() {
        assertTrue(shouldPromptForDownload(true, true, false))
        assertFalse(shouldPromptForDownload(true, true, true))
        assertFalse(shouldPromptForDownload(true, false, false))
        assertFalse(shouldPromptForDownload(false, true, false))
    }
    @Test fun followPlaybackOrKeepExplicitQuality() {
        assertEquals(3, resolveDownloadQuality(-1, 3))
        assertEquals(1, resolveDownloadQuality(1, 3))
        assertEquals(0, resolveDownloadQuality(0, 4))
    }
}
