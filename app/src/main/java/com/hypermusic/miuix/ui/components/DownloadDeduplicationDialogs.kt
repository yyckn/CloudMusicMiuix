package com.hypermusic.miuix.ui.components

import android.text.format.Formatter
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.data.download.*
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun DownloadDeduplicationDialogs() {
    val context = LocalContext.current
    val request = Downloads.duplicateConfirmation
    val versionRequest = Downloads.localVersionRequest
    var comparison by remember(request?.proposed?.id) { mutableStateOf<DownloadComparison?>(null) }
    LaunchedEffect(request?.proposed?.id) {
        val current = request ?: return@LaunchedEffect
        comparison = try { inspectDownload(context, current.proposed) }
        catch (error: CancellationException) { throw error }
        catch (error: Exception) { DownloadComparison(current.proposed.qualityLabel(), null, error.message ?: "暂时无法读取音质和大小") }
    }
    val existing = request?.let { pending -> Downloads.entries.filter { sameDownloadSong(it.track, pending.proposed.track) } }.orEmpty()
    val showComparison = comparison != null && !equalDownloadSourceSizes(comparison?.size, existing.map { it.sourceAudioSize() })
    OverlayDialog(show = request != null && versionRequest == null, title = "重复下载",
        summary = "当前下载任务中已有此歌曲，是否需要重新下载？", onDismissRequest = { Downloads.cancelDuplicateDownload(request?.proposed?.id) }) {
        if (request != null) {
            Column(Modifier.fillMaxWidth().heightIn(max = 360.dp)) {
            if (showComparison) LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                item { SmallTitle("当前下载", insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) }
                item { DownloadVersionRow(request.proposed, quality = comparison?.quality ?: request.proposed.qualityLabel(),
                    size = comparison?.size) }
                item { SmallTitle("已有版本", insideMargin = PaddingValues(horizontal = 0.dp, vertical = 8.dp)) }
                items(existing, key = { it.id }) { DownloadVersionRow(it, size = it.sourceAudioSize()) }
            }
            if (comparison == null) Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                InfiniteProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("正在读取实际音质和文件大小", style = MiuixTheme.textStyles.footnote1)
            }
            comparison?.error?.let { Text(it, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton("取消", modifier = Modifier.weight(1f), onClick = { Downloads.cancelDuplicateDownload(request.proposed.id) })
                TextButton("重新下载", modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { Downloads.confirmDuplicateDownload(request.proposed.id) })
            }
            }
        }
    }
    val versions = versionRequest?.let { OfflineLibrary.versions(it.track) }.orEmpty()
    var selected by remember(versionRequest?.id) { mutableStateOf(versions.firstOrNull()?.id) }
    LaunchedEffect(versions.map { it.id }) {
        if (versions.none { it.id == selected }) selected = versions.firstOrNull()?.id
    }
    OverlayDialog(show = versionRequest != null, title = "重复文件",
        summary = "当前歌曲在本地存储了多个版本，请选择需要保留哪个版本？",
        onDismissRequest = { Downloads.dismissLocalVersion() }) {
        Column(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
        LazyColumn(Modifier.fillMaxWidth().weight(1f, fill = false).selectableGroup()) {
            items(versions, key = { it.id }) { entry ->
                DownloadVersionRow(entry, selected = entry.id == selected, onSelect = { selected = entry.id })
            }
        }
        Text("确认后将删除其他版本，保留所选文件。", modifier = Modifier.padding(top = 8.dp),
            style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        TextButton("确定", enabled = versions.any { it.id == selected }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            colors = ButtonDefaults.textButtonColorsPrimary(), onClick = { selected?.let(Downloads::keepLocalVersion) })
        }
    }
}

@Composable
private fun DownloadVersionRow(entry: DownloadEntry, quality: String = entry.qualityLabel(), size: Long? = entry.comparisonSize(),
    showSize: Boolean = true, selected: Boolean? = null, onSelect: (() -> Unit)? = null) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        BasicComponent(onClick = onSelect, startAction = {
            AsyncImage(OfflineLibrary.cover(entry, thumbnail = true) ?: entry.track.imgUrl, null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)))
        }, endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selected != null) {
                    RadioButton(selected, onClick = onSelect)
                }
            }
        }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(entry.track.title, style = MiuixTheme.textStyles.headline1, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
            }
            Text("${entry.track.artist} - ${entry.track.album}", style = MiuixTheme.textStyles.body2,
                maxLines = 1, overflow = TextOverflow.Ellipsis, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f))
            TrackFileDetails(quality, if (showSize) size?.let { Formatter.formatShortFileSize(context, it) } ?: "未知大小" else null)
        }
    }
}
