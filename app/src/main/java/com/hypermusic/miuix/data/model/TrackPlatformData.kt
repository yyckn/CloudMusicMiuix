package com.hypermusic.miuix.data.model

/**
 * Platform-specific track metadata. Add new provider-only fields here instead of
 * expanding the common [Track] contract.
 */
data class TrackPlatformData(
    val isCloudDisk: Boolean = false,
    val qqMediaMid: String = "",
    val qqSongId: Long = 0,
    val bodianFreeSign: String = "",
    val kugouHash: String = "",
    val kugouAlbumAudioId: String = ""
)
