package com.hypermusic.miuix.ui.components

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import com.hypermusic.miuix.data.download.DownloadEntry
import com.hypermusic.miuix.data.download.exportedSongName
import com.hypermusic.miuix.data.download.prepareAudioShareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

internal data class DownloadFileActions(val share: (DownloadEntry) -> Unit, val export: (DownloadEntry) -> Unit)

@Composable
internal fun rememberDownloadFileActions(): DownloadFileActions {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf<DownloadEntry?>(null) }
    fun toast(message: String) = android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val entry = exporting
        exporting = null
        if (uri != null && entry != null) scope.launch {
            val result = withContext(Dispatchers.IO) { runCatching {
                val file = File(entry.path)
                check(file.isFile) { "歌曲文件不存在" }
                val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(context, uri) ?: error("无法打开目录")
                val name = exportedSongName(entry.track, file.extension)
                val target = tree.createFile("audio/${if (file.extension == "m4a") "mp4" else file.extension}", name) ?: error("无法创建文件")
                try { context.contentResolver.openOutputStream(target.uri)?.use { output -> file.inputStream().use { it.copyTo(output) } } ?: error("无法写入文件") }
                catch (error: Exception) { target.delete(); throw error }
            } }
            toast(result.fold({ "导出成功" }, { it.message ?: "导出失败" }))
        }
    }
    return DownloadFileActions(share = { entry ->
        scope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val source = File(entry.path)
                    prepareAudioShareFile(source, context.cacheDir.resolve("shared_audio"),
                        exportedSongName(entry.track, source.extension))
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file, file.name)
                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("audio/*")
                    .putExtra(Intent.EXTRA_STREAM, uri).putExtra(Intent.EXTRA_TITLE, file.name)
                    .apply { clipData = android.content.ClipData.newRawUri(file.name, uri) }
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "分享文件"))
            } catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (error: Exception) { toast("分享失败：${error.message}") }
        }
    }, export = { entry -> exporting = entry; picker.launch(null) })
}
