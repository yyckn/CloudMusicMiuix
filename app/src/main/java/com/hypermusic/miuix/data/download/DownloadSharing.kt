package com.hypermusic.miuix.data.download

import java.io.File
import java.nio.file.Files

/** Receivers may use the URI's last segment instead of querying DISPLAY_NAME. */
@Synchronized
internal fun prepareAudioShareFile(source: File, shareDirectory: File, displayName: String): File {
    check(source.isFile) { "歌曲文件不存在" }
    require(displayName.isNotBlank() && File(displayName).name == displayName)
    val directory = shareDirectory.resolve(source.name)
        .resolve("${source.length()}-${source.lastModified()}")
    check(directory.isDirectory || directory.mkdirs()) { "无法创建分享目录" }
    val shared = directory.resolve(displayName)
    if (shared.isFile && shared.length() == source.length()) return shared
    try {
        // Downloads are immutable. A cache link can be removed without removing the original.
        try { Files.createLink(shared.toPath(), source.toPath()) }
        catch (_: Exception) { source.copyTo(shared, overwrite = true) }
        return shared
    } catch (error: Exception) {
        shared.delete()
        throw error
    }
}
