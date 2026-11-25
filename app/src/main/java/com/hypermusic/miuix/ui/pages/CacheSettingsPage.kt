package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.PageBackButton

import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.hypermusic.miuix.data.cache.AudioCache
import com.hypermusic.miuix.data.cache.ImageCaches
import com.hypermusic.miuix.data.audio.separation.VocalSeparationCache
import com.hypermusic.miuix.data.audio.separation.VocalSeparationCacheEntry
import com.hypermusic.miuix.data.audio.separation.VocalSeparationEngine
import com.hypermusic.miuix.data.audio.separation.VocalSeparationModel
import com.hypermusic.miuix.data.audio.separation.VocalSeparationStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@OptIn(coil.annotation.ExperimentalCoilApi::class)
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
fun CacheSettingsPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("cache_settings", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    val sizes = listOf(1, 2, 5, 10, 20, 50, 100)
    val capacityLabels = sizes.map { "$it GB" }
    var separationCapacity by remember {
        val capacity = prefs.getInt(VocalSeparationCache.CAPACITY_PREF, VocalSeparationCache.DEFAULT_CAPACITY_GB)
        mutableIntStateOf(sizes.indexOf(capacity).takeIf { it >= 0 } ?: sizes.indexOf(VocalSeparationCache.DEFAULT_CAPACITY_GB))
    }
    var showSeparationCache by remember { mutableStateOf(false) }
    var separationEntries by remember { mutableStateOf(emptyList<VocalSeparationCacheEntry>()) }
    val separationUi by VocalSeparationEngine.uiState.collectAsState()
    val readyEntryId = (separationUi.status as? VocalSeparationStatus.Ready)?.entry?.id
    var separationBusy by remember { mutableStateOf(false) }
    suspend fun refreshSeparationEntries() {
        separationEntries = withContext(Dispatchers.IO) { VocalSeparationEngine.listCacheEntries() }
    }
    LaunchedEffect(showSeparationCache, readyEntryId) { refreshSeparationEntries() }

    fun updateSeparationCache(operation: suspend () -> Boolean) {
        if (separationBusy) return
        separationBusy = true
        scope.launch {
            try {
                if (!operation()) Toast.makeText(context, "部分人声分离缓存无法删除", Toast.LENGTH_SHORT).show()
                refreshSeparationEntries()
            } finally {
                separationBusy = false
            }
        }
    }
    var selected by remember { mutableIntStateOf(sizes.indexOf(prefs.getInt("audio_gb", 10)).coerceAtLeast(0)) }
    var size by remember { mutableLongStateOf(0L) }
    var pageSize by remember { mutableLongStateOf(0L) }
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
        pageSize = com.hypermusic.miuix.data.cache.PageCache.size(context)
    }
    com.hypermusic.miuix.ui.navigation.BackHandler(
        enabled = pendingClear == null && !showSeparationCache,
        onBack = onBack,
    )
    Scaffold(topBar = { SmallTopAppBar(title = "缓存设置", navigationIcon = { PageBackButton(onClick = onBack) }) }) { padding ->
        Column(Modifier.verticalScroll(rememberScrollState()).padding(padding)) {
            SmallTitle("页面缓存")
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                ArrowPreference(title = "清空页面缓存", summary = android.text.format.Formatter.formatShortFileSize(context, pageSize),
                    onClick = { if (!busy) pendingClear = "页面" to null })
            }
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
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                WindowDropdownPreference(title = "歌曲缓存容量", summary = "更改后重启应用生效", items = capacityLabels, selectedIndex = selected,
                    onSelectedIndexChange = { selected = it; prefs.edit().putInt("audio_gb", sizes[it]).apply() })
                ArrowPreference(title = "清空歌曲文件缓存", summary = android.text.format.Formatter.formatShortFileSize(context, size), onClick = {
                    if (!busy) pendingClear = "歌曲文件" to null
                })
            }
            SmallTitle("人声分离缓存")
            Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                WindowDropdownPreference(
                    title = "人声分离缓存容量",
                    items = capacityLabels,
                    selectedIndex = separationCapacity,
                    enabled = !separationBusy,
                    onSelectedIndexChange = { index ->
                        separationCapacity = index
                        prefs.edit().putInt(VocalSeparationCache.CAPACITY_PREF, sizes[index]).apply()
                        updateSeparationCache {
                            VocalSeparationEngine.trimCacheToBudget()
                            true
                        }
                    },
                )
                ArrowPreference(
                    title = "管理人声分离缓存",
                    summary = Formatter.formatShortFileSize(context, separationEntries.sumOf { it.totalBytes }),
                    onClick = { showSeparationCache = true },
                )
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
                            if (selectedCache.first == "页面") com.hypermusic.miuix.data.cache.PageCache.clear(context)
                            else if (kind != null) { val loader = ImageCaches.get(context, kind); loader.memoryCache?.clear(); loader.diskCache?.clear() }
                            else { val cache = AudioCache.get(context); cache.keys.toList().forEach(cache::removeResource); context.getSharedPreferences("audio_cache_index", Context.MODE_PRIVATE).edit().clear().apply() }
                        } }
                        size = withContext(Dispatchers.IO) { runCatching { AudioCache.get(context).cacheSpace }.getOrDefault(0) }
                        imageUsage = readImageUsage()
                        pageSize = com.hypermusic.miuix.data.cache.PageCache.size(context)
                        busy = false
                        pendingClear = null
                        result.onFailure { android.widget.Toast.makeText(context, "清空缓存失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show() }
                    }
                })
            }
        })
    VocalSeparationCacheSheet(
        show = showSeparationCache,
        entries = separationEntries,
        busy = separationBusy,
        onDismiss = { showSeparationCache = false },
        onClear = { updateSeparationCache { VocalSeparationEngine.clearCache() } },
        onDelete = { ids -> updateSeparationCache { VocalSeparationEngine.deleteCacheEntries(ids) } },
    )
}

@Composable
private fun VocalSeparationCacheSheet(
    show: Boolean,
    entries: List<VocalSeparationCacheEntry>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onClear: () -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    val context = LocalContext.current
    val colors = MiuixTheme.colorScheme
    var managing by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(emptySet<String>()) }
    var confirmClear by remember { mutableStateOf(false) }
    val selectedIds = entries.mapNotNull { it.id.takeIf(selection::contains) }.toSet()

    OverlayBottomSheet(
        show = show,
        title = "人声分离缓存",
        insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = onDismiss,
        onDismissFinished = {
            managing = false
            selection = emptySet()
            confirmClear = false
        },
        startAction = {
            IconButton(onClick = { confirmClear = true }, enabled = !busy && entries.isNotEmpty()) {
                Icon(MiuixIcons.Delete, "清空人声分离缓存", tint = colors.error)
            }
        },
        endAction = {
            TextButton(
                text = if (managing) "完成" else "管理",
                enabled = !busy && (managing || entries.isNotEmpty()),
                onClick = {
                    managing = !managing
                    selection = emptySet()
                },
            )
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        ) {
            if (entries.isEmpty()) {
                item {
                    Text(
                        "暂无人声分离缓存",
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 20.dp),
                        color = colors.onSurfaceVariantSummary,
                    )
                }
            }
            if (managing) {
                item {
                    TextButton(
                        text = "删除所选（${selectedIds.size}）",
                        enabled = !busy && selectedIds.isNotEmpty(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        onClick = {
                            onDelete(selectedIds)
                            selection = emptySet()
                        },
                    )
                }
            }
            items(entries, key = { it.id }) { entry ->
                val model = when (VocalSeparationModel.byId(entry.modelId)) {
                    VocalSeparationModel.KUIELAB_B_VOCALS -> "快速模型"
                    VocalSeparationModel.KIM_VOCAL_2 -> "均衡模型"
                    VocalSeparationModel.UVR_MDX_INST_HQ_4 -> "质量模型"
                }
                val artistAndAlbum = listOf(entry.artist, entry.album).filter { it.isNotBlank() }.joinToString(" - ")
                val toggle = {
                    selection = if (entry.id in selection) selection - entry.id else selection + entry.id
                }
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 8.dp)) {
                    BasicComponent(
                        title = entry.title.ifBlank { "未命名歌曲" },
                        summary = if (artistAndAlbum.isBlank()) model else "$artistAndAlbum（$model）",
                        startAction = if (managing) {
                            {
                                Checkbox(
                                    state = if (entry.id in selection) ToggleableState.On else ToggleableState.Off,
                                    enabled = !busy,
                                    onClick = toggle,
                                )
                            }
                        } else null,
                        endActions = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Formatter.formatShortFileSize(context, entry.totalBytes),
                                    style = MiuixTheme.textStyles.footnote1,
                                    color = colors.onSurfaceVariantSummary,
                                )
                                IconButton(onClick = { onDelete(setOf(entry.id)) }, enabled = !busy) {
                                    Icon(MiuixIcons.Delete, "删除${entry.title}的分离缓存", tint = colors.error)
                                }
                            }
                        },
                        onClick = toggle.takeIf { managing && !busy },
                    )
                }
            }
        }
        OverlayDialog(
            show = confirmClear,
            title = "清空人声分离缓存？",
            onDismissRequest = { confirmClear = false },
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", modifier = Modifier.weight(1f), onClick = { confirmClear = false })
                TextButton(
                    text = "清空",
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.textButtonColorsPrimary(),
                    enabled = !busy,
                    onClick = {
                        confirmClear = false
                        selection = emptySet()
                        onClear()
                    },
                )
            }
        }
    }
}
