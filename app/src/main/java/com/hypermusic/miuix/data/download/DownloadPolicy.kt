package com.hypermusic.miuix.data.download

/** 网络不可用时先排队，连接非 Wi-Fi 网络后再请求用户确认。 */
internal fun shouldPromptForDownload(warningEnabled: Boolean, hasInternet: Boolean, isWifi: Boolean): Boolean =
    warningEnabled && hasInternet && !isWifi

/** -1 表示跟随播放设置；已有的独立音质索引保持不变。 */
internal fun resolveDownloadQuality(configured: Int, playback: Int): Int =
    if (configured < 0) playback else configured
