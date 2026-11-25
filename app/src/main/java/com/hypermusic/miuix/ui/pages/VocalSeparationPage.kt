package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.R
import com.hypermusic.miuix.data.audio.separation.VocalModelDownloadState
import com.hypermusic.miuix.data.audio.separation.VocalSeparationEngine
import com.hypermusic.miuix.data.audio.separation.VocalSeparationModel
import com.hypermusic.miuix.data.audio.separation.VocalSeparationModelStore
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.PageBackButton
import com.hypermusic.miuix.ui.components.SettingsItemIcon
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.pageScrollTopInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.navigation.BackHandler
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun VocalSeparationPage(onBack: () -> Unit) {
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()
    val ui by VocalSeparationEngine.uiState.collectAsState()
    val downloadStates by VocalSeparationModelStore.states.collectAsState()
    val readyModels = VocalSeparationModel.entries.filter { downloadStates[it.id] is VocalModelDownloadState.Ready }
    var showModels by remember { mutableStateOf(false) }
    BackHandler { onBack() }
    Scaffold(
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "人声分离",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = { PageBackButton(onClick = onBack) },
                )
            }
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = pageViewportTopInset(paddingValues.calculateTopPadding()))
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(
                top = pageScrollTopInset(paddingValues.calculateTopPadding()),
                bottom = FeedBottomPads.None,
            ),
            overscrollEffect = null,
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            item {
                Text(
                    "*通过本地的音频分离模型分离歌曲的人声与伴奏，不会将任何数据传出此设备。",
                    modifier = Modifier.padding(start = 26.dp, end = 26.dp, bottom = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            item { SmallTitle("分离设置") }
            item {
                Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    SwitchPreference(
                        title = "开启人声分离",
                        startAction = { SettingsItemIcon(R.drawable.ic_md3_equalizer) },
                        checked = ui.enabled,
                        onCheckedChange = { VocalSeparationEngine.setEnabled(it) },
                    )
                    ArrowPreference(
                        title = "人声分离模型",
                        onClick = { showModels = true },
                    )
                    SwitchPreference(
                        title = "启用硬件加速",
                        summary = "已适配部分骁龙芯片的Adreno GPU推理加速，不支持的设备将依次退回NNAPI、CPU进行计算",
                        checked = ui.hardwareAcceleration,
                        onCheckedChange = { VocalSeparationEngine.setHardwareAcceleration(it) },
                    )
                }
            }
        }
    }

    OverlayBottomSheet(
        show = showModels,
        title = "人声分离模型",
        insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = { showModels = false },
        endAction = {
            IconButton(onClick = { showModels = false }) {
                Icon(MiuixIcons.Close, contentDescription = "关闭")
            }
        },
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            contentPadding = PaddingValues(top = 4.dp, bottom = 28.dp),
        ) {
            item {
                Text(
                    "模型来自Github，国内下载可能较慢，下载后将存储在应用内。",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            if (readyModels.isNotEmpty()) {
                item {
                    Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp)) {
                        WindowDropdownPreference(
                            title = "当前使用模型",
                            items = readyModels.map { it.label },
                            selectedIndex = readyModels.indexOf(ui.model),
                            onSelectedIndexChange = { index ->
                                readyModels.getOrNull(index)?.let(VocalSeparationEngine::setModel)
                            },
                        )
                    }
                }
            }
            items(VocalSeparationModel.entries, key = { it.id }) { model ->
                ModelRow(
                    model = model,
                    state = downloadStates[model.id] ?: VocalModelDownloadState.Idle,
                    onDownload = { VocalSeparationModelStore.download(model) },
                    onCancel = { VocalSeparationModelStore.cancelDownload(model) },
                    onDelete = {
                        VocalSeparationModelStore.delete(model)
                        VocalSeparationEngine.refreshModels()
                    },
                )
            }
        }
    }

}

@Composable
private fun ModelRow(
    model: VocalSeparationModel,
    state: VocalModelDownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val (icon, description, action) = when (state) {
        VocalModelDownloadState.Ready -> Triple(MiuixIcons.Delete, "删除模型", onDelete)
        is VocalModelDownloadState.Downloading, VocalModelDownloadState.Verifying ->
            Triple(MiuixIcons.Close, "取消下载", onCancel)
        else -> Triple(MiuixIcons.Download, "下载模型", onDownload)
    }
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp),
    ) {
        BasicComponent(
            title = model.label + if (model == VocalSeparationModel.DEFAULT) " (推荐)" else "",
            summary = model.summary,
            endActions = {
                IconButton(onClick = action) {
                    val colors = MiuixTheme.colorScheme
                    Icon(
                        imageVector = icon,
                        contentDescription = "$description ${model.label}",
                        tint = if (state is VocalModelDownloadState.Ready) colors.error else colors.onSurface,
                    )
                }
            },
            bottomAction = {
                Column(Modifier.fillMaxWidth()) {
                    when (state) {
                        is VocalModelDownloadState.Downloading -> {
                            LinearProgressIndicator(
                                progress = (state.downloaded.toFloat() / state.total.coerceAtLeast(1L)).coerceIn(0f, 1f),
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Text(
                                "${formatBytes(state.downloaded)} / ${formatBytes(state.total)}" +
                                    if (state.bytesPerSecond > 0) " · ${formatBytes(state.bytesPerSecond)}/s" else "",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        VocalModelDownloadState.Verifying -> {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "正在校验模型完整性",
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                        is VocalModelDownloadState.Failed -> {
                            Text(
                                state.message,
                                style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.error,
                            )
                        }
                        VocalModelDownloadState.Ready, VocalModelDownloadState.Idle -> Unit
                    }
                }
            },
        )
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024.0 && unit < units.lastIndex) {
        value /= 1024.0
        unit++
    }
    return if (unit == 0) "${value.toLong()} ${units[unit]}" else String.format(java.util.Locale.ROOT, "%.1f %s", value, units[unit])
}
