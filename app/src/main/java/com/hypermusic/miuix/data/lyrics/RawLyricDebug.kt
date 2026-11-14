package com.hypermusic.miuix.data.lyrics

internal fun formatRawLyricSections(vararg sections: Pair<String, String>): String = sections
    .filter { it.second.isNotBlank() }
    .joinToString("\n\n") { (title, content) -> "【$title】\n$content" }
