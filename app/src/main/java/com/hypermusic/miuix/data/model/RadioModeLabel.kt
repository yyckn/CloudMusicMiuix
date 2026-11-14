package com.hypermusic.miuix.data.model

internal fun radioModeLabel(id: String?, title: String?): String = when {
    id == null || !id.startsWith("extra:qishui:scene:") -> "私人FM"
    title == "专注模式" || id == "extra:qishui:scene:6" -> "专注模式"
    !title.isNullOrBlank() -> "场景推荐 ($title)"
    else -> "场景推荐"
}
