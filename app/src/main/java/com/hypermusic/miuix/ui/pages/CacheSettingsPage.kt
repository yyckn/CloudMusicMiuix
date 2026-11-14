package com.hypermusic.miuix.ui.pages

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.media3.common.util.UnstableApi
import com.hypermusic.miuix.data.cache.AudioCache
import com.hypermusic.miuix.data.cache.ImageCaches
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CacheSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cache_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val sizes = listOf(1, 2, 5, 10, 20, 50, 100)
    var selected by remember { mutableIntStateOf(sizes.indexOf(prefs.getInt("audio_gb", 10)).coerceAtLeast(0)) }
    var size by remember { mutableLongStateOf(0L) }
    val imageSizes = listOf(256, 512, 1024, 2048, 5120, 10240)
    var imageSelected by remember { mutableIntStateOf(imageSizes.indexOf(prefs.getInt("images_mb", 1024).coerceAtLeast(256)).let { if (it < 0) imageSizes.indexOf(1024) else it }) }
    var imageUsage by remember { mutableStateOf<Map<ImageCaches.Kind, Long>>(emptyMap()) }
    suspend fun readImageUsage(): Map<ImageCaches.Kind, Long> = withContext(Dispatchers.IO) {
        ImageCaches.Kind.entries.associateWith { kind -> runCatching { ImageCaches.get(context, kind).diskCache?.size ?: 0L }.getOrDefault(0L) }
    }
    var busy by remember { mutableStateOf(false) }
    var pendingClear by remember { mutableStateOf<Pair<String, ImageCaches.Kind?>?>(null) }
    LaunchedEffect(Unit) { if (prefs.getInt("images_mb", 1024) < 256) prefs.edit().putInt("images_mb", 256).apply() }
    BackHandler(enabled = pendingClear != null) { }
    LaunchedEffect(Unit) {
        size = withContext(Dispatchers.IO) { runCatching { AudioCache.get(context).cacheSpace }.getOrDefault(0) }
        imageUsage = readImageUsage()
    }
    com.hypermusic.miuix.ui.navigation.BackHandler(enabled = pendingClear == null, onBack = onBack)
    Scaffold(topBar = { SmallTopAppBar(title = "缓存设置", navigationIcon = {
        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回", modifier = Modifier.padding(start = 8.dp).clickable(onClick = onBack))
    }) }) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            SmallTitle("图片缓存")
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                WindowDropdownPreference(title = "图片缓存容量", summary = "更改后重启应用生效", items = imageSizes.map { if (it >= 1024) "${it / 1024} GB" else "$it MB" }, selectedIndex = imageSelected,
                    onSelectedIndexChange = { imageSelected = it; prefs.edit().putInt("images_mb", imageSizes[it]).apply() })
                listOf(ImageCaches.Kind.COVER to "封面", ImageCaches.Kind.THUMBNAIL to "封面缩略图", ImageCaches.Kind.ARTIST to "歌手头像").forEach { (kind, title) ->
                    ArrowPreference(title = "清空$title", summary = android.text.format.Formatter.formatShortFileSize(context, imageUsage[kind] ?: 0L), onClick = {
                        if (!busy) pendingClear = title to kind
                    })
                }
            }
            SmallTitle("歌曲缓存")
            Card(Modifier.padding(horizontal = 12.dp)) {
                WindowDropdownPreference(title = "歌曲缓存容量", summary = "更改后重启应用生效", items = sizes.map { "$it GB" }, selectedIndex = selected,
                    onSelectedIndexChange = { selected = it; prefs.edit().putInt("audio_gb", sizes[it]).apply() })
                ArrowPreference(title = "清空歌曲文件缓存", summary = android.text.format.Formatter.formatShortFileSize(context, size), onClick = {
                    if (!busy) pendingClear = "歌曲文件" to null
                })
            }
        }
    }
    OverlayDialog(show = pendingClear != null, title = "确认清空${pendingClear?.first.orEmpty()}缓存吗？",
        onDismissRequest = {}, content = {
            Row(Modifier.fillMaxWidth()) {
                TextButton(text = "取消", modifier = Modifier.weight(1f), enabled = !busy, onClick = { pendingClear = null })
                Spacer(Modifier.width(12.dp))
                TextButton(text = "确认", modifier = Modifier.weight(1f), enabled = !busy, colors = ButtonDefaults.textButtonColorsPrimary(), onClick = {
                    val selectedCache = pendingClear ?: return@TextButton
                    scope.launch {
                        busy = true
                        val result = withContext(Dispatchers.IO) { runCatching {
                            val kind = selectedCache.second
                            if (kind != null) { val loader = ImageCaches.get(context, kind); loader.memoryCache?.clear(); loader.diskCache?.clear() }
                            else { val cache = AudioCache.get(context); cache.keys.toList().forEach(cache::removeResource); context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE).edit().clear().apply() }
                        } }
                        size = withContext(Dispatchers.IO) { runCatching { AudioCache.get(context).cacheSpace }.getOrDefault(0) }
                        imageUsage = readImageUsage()
                        busy = false
                        pendingClear = null
                        result.onFailure { android.widget.Toast.makeText(context, "清空缓存失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        })
}
