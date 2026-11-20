package com.hypermusic.miuix.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.download.*
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.menu.WindowIconDropdownMenu
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.preference.RadioButtonPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun BatchDownloadMenu(tracks: List<Track>, enabled: Boolean = true, loadAll: (suspend () -> List<Track>)? = null) {
    WindowIconDropdownMenu(entry = DropdownEntry(items = listOf(DropdownItem("批量下载", enabled = enabled && tracks.isNotEmpty(),
        icon = { Icon(MiuixIcons.Download, null, modifier = it.size(24.dp)) },
        onClick = { BatchDownloads.open(tracks, loadAll) })))) {
        Icon(MiuixIcons.More, "更多")
    }
}

@Composable
internal fun BatchDownloadSheets() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val request = BatchDownloads.selection
    var tracks by remember(request?.id) { mutableStateOf(request?.tracks.orEmpty()) }
    var selected by remember(request?.id) { mutableStateOf(request?.tracks.orEmpty().map { it.downloadSongKey() }.toSet()) }
    var loading by remember(request?.id) { mutableStateOf(request?.loadAll != null) }
    var error by remember(request?.id) { mutableStateOf<String?>(null) }
    var submitting by remember(request?.id) { mutableStateOf(false) }
    var proposals by remember(request?.id) { mutableStateOf<Map<DownloadSongKey, DownloadEntry>>(emptyMap()) }
    LaunchedEffect(request?.id) {
        val current = request ?: return@LaunchedEffect
        try {
            tracks = distinctDownloadTracks(current.loadAll?.invoke() ?: current.tracks)
            selected = tracks.map { it.downloadSongKey() }.toSet()
            proposals = tracks.associate { it.downloadSongKey() to Downloads.prepare(context, it) }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "无法加载歌曲，请关闭后重试" }
        finally { loading = false }
    }
    val limiter = remember { Semaphore(3) }
    val previews = remember(request?.id) { mutableStateMapOf<String, DownloadComparison>() }
    // Resolve offscreen rows too: a LazyColumn only composes the visible songs, while the
    // download button must include the entire selection. Keep requests bounded and reuse
    // these previews when the selection changes or a row scrolls into view.
    LaunchedEffect(proposals) {
        val entries = proposals.values.toList()
        coroutineScope {
            repeat(minOf(3, entries.size)) { worker ->
                launch {
                    for (index in worker until entries.size step 3) {
                        val entry = entries[index]
                        if (entry.id !in previews) previews[entry.id] = limiter.withPermit {
                            loadDownloadPreview(context, entry)
                        }
                    }
                }
            }
        }
    }
    val selectedEntries = selected.mapNotNull { proposals[it] }
    val selectedSize = when {
        selected.isEmpty() -> Formatter.formatShortFileSize(context, 0)
        selectedEntries.size != selected.size || selectedEntries.any { it.id !in previews } -> "计算中…"
        selectedEntries.any { previews[it.id]?.size == null } -> "大小未知"
        else -> Formatter.formatShortFileSize(context, selectedEntries.sumOf { previews[it.id]?.size ?: 0L })
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    OverlayBottomSheet(show = request != null, title = "批量下载", insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = { if (!submitting) BatchDownloads.closeSelection() },
        startAction = {
            // Read state inside the root overlay's composition, rather than capturing the
            // header's initial value while the sheet is opening.
            val checkState = when {
                selected.isEmpty() -> ToggleableState.Off
                selected.size == tracks.size -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            Checkbox(checkState, enabled = !loading && !submitting && error == null, onClick = {
                selected = if (selected.isNotEmpty() && selected.size == tracks.size) emptySet()
                    else tracks.map { it.downloadSongKey() }.toSet()
            })
        },
        endAction = { IconButton(onClick = { if (!submitting) BatchDownloads.closeSelection() }) { Icon(MiuixIcons.Close, "关闭") } }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 600.dp)) {
            if (loading) Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() }
            error?.let { Text(it, modifier = Modifier.padding(16.dp), color = MiuixTheme.colorScheme.error) }
            if (!loading && error == null) LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(tracks, key = { "${it.source}:${it.id}" }) { track ->
                    val entry = proposals[track.downloadSongKey()]
                    val preview = entry?.let { previews[it.id] }
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        BatchSongInformation(track, checked = track.downloadSongKey() in selected,
                            onToggle = { selected = if (track.downloadSongKey() in selected) selected - track.downloadSongKey() else selected + track.downloadSongKey() },
                            quality = preview?.quality ?: entry?.qualityLabel(), size = preview?.size)
                    }
                }
            }
            TextButton("开始下载${selected.size}首歌曲（${selectedSize}）", modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
                .padding(top = 12.dp, bottom = 12.dp + bottomInset), colors = ButtonDefaults.textButtonColorsPrimary(),
                enabled = selected.isNotEmpty() && !loading && !submitting && error == null, onClick = {
                    submitting = true
                    scope.launch {
                        try { BatchDownloads.start(context, tracks.filter { it.downloadSongKey() in selected }) }
                        catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = e.message ?: "无法创建下载任务"; submitting = false }
                    }
                })
        }
    }

    val conflict = BatchDownloads.conflicts
    OverlayDialog(show = conflict != null && !conflict.manual, title = "重复下载",
        summary = "当前有${conflict?.proposed?.size ?: 0}首歌曲已经在下载任务中存在，是否需要重新下载",
        onDismissRequest = {}) {
        if (conflict != null) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TextButton("全部重新下载", modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary(),
                onClick = { BatchDownloads.resolve(conflict.id, conflict.proposed.map { it.id }.toSet()) })
            TextButton("全部取消下载", modifier = Modifier.fillMaxWidth(), onClick = { BatchDownloads.resolve(conflict.id, emptySet()) })
            TextButton("手动选择是否下载", modifier = Modifier.fillMaxWidth(), onClick = { BatchDownloads.selectManually() })
        }
    }
    var replaceIds by remember(conflict?.id) { mutableStateOf(conflict?.proposed.orEmpty().map { it.id }.toSet()) }
    val conflictPreviews = remember(conflict?.id) { mutableStateMapOf<String, DownloadComparison>() }
    OverlayBottomSheet(show = conflict?.manual == true, title = "解决冲突歌曲", insideMargin = DpSize(12.dp, 0.dp), allowDismiss = false,
        onDismissRequest = { /* Resolve explicitly to avoid accidentally losing pending choices. */ }) {
        if (conflict != null) Column(Modifier.fillMaxWidth().heightIn(max = 640.dp)) {
            LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false), contentPadding = PaddingValues(vertical = 8.dp)) {
                items(conflict.proposed, key = { it.id }) { proposed ->
                    PreviewDownload(proposed, conflictPreviews, limiter)
                    val preview = conflictPreviews[proposed.id]
                    val existing = Downloads.entries.filter { sameDownloadSong(it.track, proposed.track) }
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                        BatchSongInformation(proposed.track)
                        Column(Modifier.selectableGroup()) {
                            RadioButtonPreference(title = "当前下载版本：", selected = proposed.id in replaceIds,
                                onClick = { replaceIds = replaceIds + proposed.id }, endActions = {
                                    ConflictVersionDetails(preview?.quality ?: proposed.qualityLabel(),
                                        preview?.size?.let { Formatter.formatShortFileSize(context, it) } ?: "未知大小")
                                })
                            // Existing files are retained together; choosing this option skips a replacement.
                            RadioButtonPreference(title = "已有版本：", selected = proposed.id !in replaceIds,
                                onClick = { replaceIds = replaceIds - proposed.id }, endActions = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp), horizontalAlignment = Alignment.End) {
                                        existing.forEach { entry -> ConflictVersionDetails(entry.qualityLabel(),
                                            entry.comparisonSize()?.let { Formatter.formatShortFileSize(context, it) } ?: "未知大小") }
                                        if (existing.isEmpty()) Text("已有任务已移除", style = MiuixTheme.textStyles.footnote1)
                                    }
                                })
                        }
                    }
                }
            }
            Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(top = 8.dp, bottom = 12.dp + bottomInset),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (replaceIds.isNotEmpty() && replaceIds.size < conflict.proposed.size)
                    TextButton("重新下载${replaceIds.size}首歌曲", modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.textButtonColorsPrimary(),
                        onClick = { BatchDownloads.resolve(conflict.id, replaceIds) })
                TextButton("全部重新下载", modifier = Modifier.fillMaxWidth(), onClick = {
                    BatchDownloads.resolve(conflict.id, conflict.proposed.map { it.id }.toSet())
                })
                TextButton("全部取消下载", modifier = Modifier.fillMaxWidth(), onClick = { BatchDownloads.resolve(conflict.id, emptySet()) })
            }
        }
    }
}

@Composable
private fun ConflictVersionDetails(quality: String, size: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(size, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
        TrackQualityTag(quality)
    }
}

@Composable
private fun PreviewDownload(entry: DownloadEntry?, results: MutableMap<String, DownloadComparison>, limiter: Semaphore) {
    val context = LocalContext.current
    LaunchedEffect(entry?.id) {
        if (entry == null || results.containsKey(entry.id)) return@LaunchedEffect
        val result = limiter.withPermit {
            loadDownloadPreview(context, entry)
        }
        results[entry.id] = result
    }
}

private suspend fun loadDownloadPreview(context: android.content.Context, entry: DownloadEntry): DownloadComparison =
    try { inspectDownload(context, entry) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { DownloadComparison(entry.qualityLabel(), null, e.message) }

@Composable
private fun BatchSongInformation(track: Track, checked: Boolean? = null, onToggle: (() -> Unit)? = null,
    quality: String? = null, size: Long? = null) {
    val context = LocalContext.current
    BasicComponent(onClick = onToggle, startAction = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (checked != null) {
                Checkbox(if (checked) ToggleableState.On else ToggleableState.Off, onClick = onToggle)
                Spacer(Modifier.width(12.dp))
            }
            AsyncImage(OfflineLibrary.cover(track, thumbnail = true) ?: track.imgUrl, null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        }
    }) {
        Text(track.title, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${track.artist} - ${track.album}", style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (quality != null) TrackFileDetails(quality, size?.let { Formatter.formatShortFileSize(context, it) } ?: "未知大小")
    }
}
