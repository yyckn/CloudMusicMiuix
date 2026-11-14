package com.hypermusic.miuix.data.api

import org.junit.Assert.assertEquals
import org.junit.Test

class AdditionalLibraryChangesTest {
    @Test fun changingOneLibraryLeavesOtherPlatformsUntouched() {
        val before = MusicPlatform.entries.associateWith(AdditionalLibraryChanges::revision)
        AdditionalLibraryChanges.changed(MusicPlatform.KUGOU)
        for (platform in MusicPlatform.entries) {
            val expected = before.getValue(platform) + if (platform == MusicPlatform.KUGOU) 1 else 0
            assertEquals(expected, AdditionalLibraryChanges.revision(platform))
        }
    }
}
