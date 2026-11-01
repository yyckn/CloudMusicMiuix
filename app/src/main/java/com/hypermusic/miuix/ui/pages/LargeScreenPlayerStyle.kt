package com.hypermusic.miuix.ui.pages

/** Resolve automatic fallbacks without changing the user's saved layout preference. */
internal fun resolveLargeScreenPlayerStyle(preferred: Int, widthToHeight: Float): Int {
    val fullscreenFallback = if (preferred == 1 && widthToHeight < 4f / 3f) 0 else preferred
    // 接近 2:3 的设备可能因面板像素取整略高于边界。
    return if (fullscreenFallback == 0 && widthToHeight < 2f / 3f + 0.001f) 2 else fullscreenFallback
}
