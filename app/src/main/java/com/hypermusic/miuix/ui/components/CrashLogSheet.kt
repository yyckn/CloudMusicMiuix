package com.hypermusic.miuix.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.hypermusic.miuix.data.diagnostics.CrashDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@Composable
internal fun CrashLogSheet(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var reports by remember { mutableStateOf<List<Pair<File, String>>>(emptyList()) }
    var revision by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(false) }
    var selected by remember(show) { mutableStateOf<File?>(null) }
    var detail by remember(selected) { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
    LaunchedEffect(show, revision) {
        if (!show) return@LaunchedEffect
        loading = true
        reports = withContext(Dispatchers.IO) {
            runCatching { CrashDiagnostics.store.reports().map { it to CrashDiagnostics.store.read(it).lineSequence().firstOrNull().orEmpty() } }.getOrDefault(emptyList())
        }
        loading = false
    }
    LaunchedEffect(selected) {
        val file = selected ?: return@LaunchedEffect
        detail = withContext(Dispatchers.IO) { runCatching { CrashDiagnostics.store.read(file) }.getOrDefault("日志读取失败") }
    }
    fun share(files: List<File>) {
        scope.launch {
            try {
                val zip = withContext(Dispatchers.IO) {
                    val folder = context.cacheDir.resolve("crash_log_shares").apply { mkdirs() }
                    val output = folder.resolve("HyperMusic-CrashLogs-${System.currentTimeMillis()}.zip")
                    ZipOutputStream(output.outputStream()).use { stream ->
                        files.flatMap { CrashDiagnostics.store.attachments(it) }.distinctBy { it.name }.forEach { file ->
                            stream.putNextEntry(ZipEntry(file.name))
                            file.inputStream().use { it.copyTo(stream) }
                            stream.closeEntry()
                        }
                    }
                    // Keep recent share archives without accumulating large diagnostics indefinitely.
                    folder.listFiles().orEmpty().filter { it != output && it.lastModified() < System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L }.forEach { it.delete() }
                    output
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
                val intent = Intent(Intent.ACTION_SEND).setType("application/zip").putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION).apply { clipData = ClipData.newRawUri(zip.name, uri) }
                context.startActivity(Intent.createChooser(intent, "分享闪退日志"))
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (e: Exception) { toast("分享失败：${e.message}") }
        }
    }
    OverlayBottomSheet(show = show, title = "闪退日志", insideMargin = DpSize(12.dp, 0.dp), onDismissRequest = onDismiss,
        endAction = { IconButton(onClick = onDismiss) { Icon(MiuixIcons.Close, "关闭") } }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
            Text("自动保留最近10条异常退出记录。系统提供的原生崩溃、ANR或低内存退出信息会在下次启动后补充。",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            if (loading) InfiniteProgressIndicator(Modifier.padding(16.dp))
            if (!loading && reports.isEmpty()) Text("暂无闪退日志", Modifier.padding(16.dp))
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                items(reports, key = { it.first.name }) { (file, title) ->
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        ArrowPreference(title = title, summary = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.ROOT)
                            .format(java.util.Date(file.name.substringBefore('-').toLongOrNull() ?: file.lastModified())), onClick = { selected = file })
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 12.dp, bottom = 12.dp + bottom), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("清空", enabled = reports.isNotEmpty(), modifier = Modifier.weight(1f), onClick = { confirmClear = true })
                TextButton("分享全部", enabled = reports.isNotEmpty(), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { share(reports.map { it.first }) })
            }
        }
    }
    OverlayBottomSheet(show = selected != null && show, title = "闪退详情", insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = { selected = null }, endAction = { IconButton(onClick = { selected = null }) { Icon(MiuixIcons.Close, "关闭") } }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(16.dp)) {
                items(detail.lines()) { line -> Text(line, style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp + bottom), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("复制", enabled = detail.isNotBlank(), modifier = Modifier.weight(1f), onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("闪退日志", detail)); toast("已复制")
                })
                TextButton("分享日志", enabled = detail.isNotBlank(), modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { selected?.let { share(listOf(it)) } })
            }
        }
    }
    OverlayDialog(show = confirmClear && show, title = "清空闪退日志", summary = "确认清空所有闪退日志吗？", onDismissRequest = {}) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("取消", modifier = Modifier.weight(1f), onClick = { confirmClear = false })
            TextButton("清空", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                scope.launch { withContext(Dispatchers.IO) { CrashDiagnostics.clearReports() }; confirmClear = false; selected = null; revision++ }
            })
        }
    }
}
