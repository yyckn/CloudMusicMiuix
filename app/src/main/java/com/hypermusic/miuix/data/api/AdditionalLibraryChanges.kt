package com.hypermusic.miuix.data.api

import androidx.compose.runtime.mutableStateMapOf

/** Main-thread revision used to invalidate cached collections after successful writes. */
internal object AdditionalLibraryChanges {
    private val revisions = mutableStateMapOf<MusicPlatform, Int>()
    fun revision(platform: MusicPlatform): Int = revisions[platform] ?: 0
    fun changed(platform: MusicPlatform) { revisions[platform] = revision(platform) + 1 }
}
