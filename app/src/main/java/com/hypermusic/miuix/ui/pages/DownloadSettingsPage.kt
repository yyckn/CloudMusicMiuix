package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageNavigationIconInset

import android.content.Intent
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.core.content.FileProvider
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.download.Downloads
import com.hypermusic.miuix.data.model.AudioQuality
import com.hypermusic.miuix.data.api.MusicPlatform
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.OverlayIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DownloadSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("downloads", Context.MODE_PRIVATE) }
    var wifi by remember { mutableStateOf(prefs.getBoolean("warn_mobile", true)) }
    var ne by remember { mutableIntStateOf(prefs.getInt("quality_netease", -1)) }
    var qq by remember { mutableIntStateOf(prefs.getInt("quality_qq", -1)) }
    var bodian by remember { mutableIntStateOf(prefs.getInt("quality_bodian", -1)) }
    var kugou by remember { mutableIntStateOf(prefs.getInt("quality_kugou", -1)) }
    var qishui by remember { mutableIntStateOf(prefs.getInt("quality_qishui", -1)) }
    var parallel by remember { mutableIntStateOf(prefs.getInt("parallel", 2).coerceIn(1, 5)) }
    com.hypermusic.miuix.ui.navigation.BackHandler(onBack = onBack)
    Scaffold(topBar = { SmallTopAppBar(title = "下载设置", navigationIcon = {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.padding(start = pageNavigationIconInset()).clickable(onClick = onBack))
    }) }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding)) {
            SmallTitle("下载")
            Card(Modifier.padding(horizontal = 12.dp)) {
                ArrowPreference(title = "查看下载任务", onClick = { Downloads.showSheet = true })
                SwitchPreference(title = "非WiFi下载时弹窗提醒", checked = wifi, onCheckedChange = { wifi = it; prefs.edit().putBoolean("warn_mobile", it).remove("wifi_only").apply() })
                WindowDropdownPreference(title = "同时下载数量", items = listOf("1", "2", "3", "4", "5"), selectedIndex = parallel - 1,
                    onSelectedIndexChange = { parallel = it + 1; prefs.edit().putInt("parallel", parallel).apply() })
                WindowDropdownPreference(title = "网易云音乐下载音质", items = listOf("跟随播放设置") + AudioQuality.options(MusicPlatform.NETEASE), selectedIndex = (ne + 1).coerceIn(0, AudioQuality.neteaseOptions.size),
                    onSelectedIndexChange = { ne = it - 1; prefs.edit().putInt("quality_netease", ne).apply() })
                WindowDropdownPreference(title = "QQ音乐下载音质", items = listOf("跟随播放设置") + AudioQuality.options(MusicPlatform.QQ), selectedIndex = (qq + 1).coerceIn(0, AudioQuality.qqOptions.size),
                    onSelectedIndexChange = { qq = it - 1; prefs.edit().putInt("quality_qq", qq).apply() })
                WindowDropdownPreference(title = "波点音乐下载音质", items = listOf("跟随播放设置") + AudioQuality.bodianOptions, selectedIndex = (bodian + 1).coerceIn(0, AudioQuality.bodianOptions.size),
                    onSelectedIndexChange = { bodian = it - 1; prefs.edit().putInt("quality_bodian", bodian).apply() })
                WindowDropdownPreference(title = "酷狗音乐下载音质", items = listOf("跟随播放设置") + AudioQuality.kugouOptions, selectedIndex = (kugou + 1).coerceIn(0, AudioQuality.kugouOptions.size),
                    onSelectedIndexChange = { kugou = it - 1; prefs.edit().putInt("quality_kugou", kugou).apply() })
                WindowDropdownPreference(title = "汽水音乐下载音质", items = listOf("跟随播放设置") + AudioQuality.qishuiOptions, selectedIndex = (qishui + 1).coerceIn(0, AudioQuality.qishuiOptions.size),
                    onSelectedIndexChange = { qishui = it - 1; prefs.edit().putInt("quality_qishui", qishui).apply() })
            }
        }
    }
}

@Composable
fun DownloadSheet() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    BackHandler(enabled = Downloads.networkConfirmation != null) { }
    val fileActions = com.hypermusic.miuix.ui.components.rememberDownloadFileActions()
    OverlayBottomSheet(show = Downloads.showSheet, onDismissRequest = { Downloads.showSheet = false }, title = "下载任务", insideMargin = DpSize(12.dp, 0.dp)) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 40.dp + WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding())) {
            val active = Downloads.entries.filter { it.state != "complete" }
            val done = Downloads.entries.filter { it.state == "complete" }
            if (active.isNotEmpty()) item { SmallTitle("正在下载") }
            items(active, key = { it.id }) { entry ->
                DownloadRow(entry, onExport = {}, onShare = {})
            }
            if (done.isNotEmpty()) item { SmallTitle("历史下载") }
            items(done, key = { it.id }) { entry ->
                DownloadRow(entry, onExport = { fileActions.export(entry) }, onShare = { fileActions.share(entry) })
            }
        }
    }
    com.hypermusic.miuix.ui.components.BatchDownloadSheets()
    OverlayDialog(show = Downloads.networkConfirmation != null, title = "当前正在使用流量，确认要继续下载吗？",
        onDismissRequest = {}, content = {
            Column(Modifier.fillMaxWidth()) {
                TextButton(text = "等待连接WiFi后继续", modifier = Modifier.fillMaxWidth(), onClick = { Downloads.confirmNetwork(true) })
                Spacer(Modifier.height(8.dp))
                TextButton(text = "确认", modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { Downloads.confirmNetwork(false) })
            }
        })
    com.hypermusic.miuix.ui.components.DownloadDeduplicationDialogs()
}

@Composable
private fun DownloadRow(entry: com.hypermusic.miuix.data.download.DownloadEntry, onExport: () -> Unit, onShare: () -> Unit) {
    val done = entry.state == "complete"
    val size = android.text.format.Formatter.formatShortFileSize(LocalContext.current, entry.bytes)
    val total = if (entry.total > 0) "/${android.text.format.Formatter.formatShortFileSize(LocalContext.current, entry.total)}" else ""
    val options = if (done) listOf(DropdownItem("分享文件", onClick = onShare), DropdownItem("导出文件", onClick = onExport), DropdownItem(if (done) "删除文件" else "删除任务", onClick = { Downloads.remove(entry.id) }))
        else listOf(DropdownItem("重新下载", onClick = { Downloads.retry(entry.id) }), DropdownItem(if (done) "删除文件" else "删除任务", onClick = { Downloads.remove(entry.id) }))
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors()) {
        BasicComponent(
            startAction = {
                AsyncImage(model = com.hypermusic.miuix.data.download.OfflineLibrary.cover(entry, thumbnail = true) ?: entry.track.imgUrl, contentDescription = null,
                    imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(LocalContext.current, com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL),
                    modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
            },
            endActions = {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Box(Modifier.size(32.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        OverlayIconDropdownMenu(entry = DropdownEntry(items = options)) {
                            Icon(MiuixIcons.More, "更多", modifier = Modifier.size(20.dp),
                                tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            },
            bottomAction = if (!done && entry.state != "failed") {{
                if (entry.total > 0) LinearProgressIndicator(progress = (entry.bytes.toFloat() / entry.total).coerceIn(0f, 1f), modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }} else null
        ) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(entry.track.title, fontSize = MiuixTheme.textStyles.headline1.fontSize, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text("${entry.track.artist} - ${entry.track.album}", fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            com.hypermusic.miuix.ui.components.TrackFileDetails(entry.quality, if (done) size else size + total)
            if (entry.message.isNotBlank()) Text(entry.message, style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
