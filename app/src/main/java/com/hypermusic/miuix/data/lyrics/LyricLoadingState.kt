package com.hypermusic.miuix.data.lyrics

internal fun initialLyricWaitRemaining(startedAt: Long, now: Long): Long =
    if (startedAt <= 0) 0 else (LyricsRepository.INITIAL_WAIT_MS - (now - startedAt).coerceAtLeast(0)).coerceAtLeast(0)
