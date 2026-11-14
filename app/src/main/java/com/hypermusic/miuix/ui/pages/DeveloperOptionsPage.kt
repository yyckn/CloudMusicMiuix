package com.hypermusic.miuix.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import com.hypermusic.miuix.ui.navigation.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.viewmodel.LyricState
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun DeveloperOptionsPage(
    currentTrack: Track?,
    lyricState: LyricState,
    onBackClick: () -> Unit,
    onOpenOobe: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()

    var showRawLyrics by remember { mutableStateOf(false) }
    var showProcessedLyrics by remember { mutableStateOf(false) }
    var showHardwareInfo by remember { mutableStateOf(false) }
    val rawText = buildString {
        if (lyricState.rawDebugText.isNotBlank()) {
            append("【实际歌词来源】\n").append(lyricState.source ?: "未知")
            lyricState.matchedTrack?.let { matched ->
                append("\n【歌词歌曲】\n").append(matched.title).append(" - ").append(matched.artist)
                append("\n歌曲 ID：").append(matched.id)
            }
            append("\n\n").append(lyricState.rawDebugText)
        }
    }

    val processedText = remember(lyricState.lyrics) { com.hypermusic.miuix.data.lyrics.formatProcessedWordLyrics(lyricState.lyrics) }
    val displayedText = if (showProcessedLyrics) processedText else rawText
    fun loadRawLyrics(processed: Boolean = false) {
        if (currentTrack == null) {
            Toast.makeText(context, "当前没有播放歌曲", Toast.LENGTH_SHORT).show()
        } else {
            showProcessedLyrics = processed
            showRawLyrics = true
        }
    }

    fun copyRawLyrics() {
        if (displayedText.isBlank()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText(if (showProcessedLyrics) "processed_word_lyrics" else "raw_lyrics", displayedText))
        Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
    }

    BackHandler { onBackClick() }

    Scaffold(
        modifier = modifier,
        topBar = {
            BlurredBar(backdrop = blurBackdrop) {
                SmallTopAppBar(
                    title = "开发者选项",
                    color = if (blurBackdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    navigationIcon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { onBackClick() }
                        )
                    }
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .overScrollVertical()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .pageLayerBackdrop(blurBackdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.None)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            item { SmallTitle("应用") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    ArrowPreference(title = "OOBE", summary = "重新打开首次设置", onClick = onOpenOobe)
                }
            }
            item { SmallTitle("设备") }
            item {
                Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp)) {
                    ArrowPreference(title = "硬件信息", summary = "屏幕、设备和系统信息", onClick = { showHardwareInfo = true })
                }
            }
            item { SmallTitle("歌词调试") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                         title = "查看当前歌曲的歌词原数据",
                        summary = if (currentTrack == null) {
                            "当前没有播放歌曲"
                        } else {
                            "${currentTrack.title} - ${currentTrack.artist}"
                        },
                        onClick = { loadRawLyrics() }
                    )
                    ArrowPreference(
                        title = "查看当前歌曲的最终处理歌词",
                        summary = if (currentTrack == null) "当前没有播放歌曲" else "${currentTrack.title} - ${currentTrack.artist}",
                        onClick = { loadRawLyrics(processed = true) }
                    )
                }
            }
        }

        HardwareInfoSheet(showHardwareInfo) { showHardwareInfo = false }
        OverlayBottomSheet(
            show = showRawLyrics,
            title = if (showProcessedLyrics) "最终处理歌词" else "歌词原数据",
            startAction = {
                IconButton(onClick = { copyRawLyrics() }) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = "复制",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            endAction = {
                IconButton(onClick = { showRawLyrics = false }) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            onDismissRequest = { showRawLyrics = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 12.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when {
                        lyricState.isLoading -> {
                            InfiniteProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            )
                        }

                        lyricState.error != null -> {
                            Text(
                                text = lyricState.error,
                                modifier = Modifier.padding(16.dp),
                                style = MiuixTheme.textStyles.body2,
                                color = MiuixTheme.colorScheme.error
                            )
                        }

                        else -> {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState())
                                    .padding(16.dp)
                            ) {
                                Text(
                                    text = displayedText.ifBlank { if (showProcessedLyrics) "当前歌曲没有可显示的逐字歌词" else "当前歌词没有可显示的原始数据" },
                                    style = MiuixTheme.textStyles.footnote1,
                                    softWrap = true,
                                    color = MiuixTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HardwareInfoSheet(show: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val windowManager = context.getSystemService(android.view.WindowManager::class.java)
    val metrics = windowManager.currentWindowMetrics
    val bounds = metrics.bounds
    val insets = metrics.windowInsets.getInsetsIgnoringVisibility(
        android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout()
    )
    val usableWidth = (bounds.width() - insets.left - insets.right).coerceAtLeast(0)
    val usableHeight = (bounds.height() - insets.top - insets.bottom).coerceAtLeast(0)
    val mode = context.display?.mode
    val details = listOf(
        "设备名称" to android.os.Build.MODEL,
        "制造商" to android.os.Build.MANUFACTURER,
        "设备代号" to android.os.Build.DEVICE,
        "屏幕当前模式" to (mode?.let { "${it.physicalWidth} × ${it.physicalHeight} px" } ?: "不可用"),
        "应用窗口" to "${bounds.width()} × ${bounds.height()} px",
        "应用窗口 dp" to "${(bounds.width() / density.density).toInt()} × ${(bounds.height() / density.density).toInt()} dp",
        "可用区域" to "${usableWidth} × ${usableHeight} px（扣除系统栏和屏幕缺口）",
        "可用区域 dp" to "${(usableWidth / density.density).toInt()} × ${(usableHeight / density.density).toInt()} dp",
        "配置宽高" to "${configuration.screenWidthDp} × ${configuration.screenHeightDp} dp",
        "屏幕密度" to "${configuration.densityDpi} dpi（${"%.2f".format(java.util.Locale.ROOT, density.density)}×）",
        "刷新率" to (context.display?.refreshRate?.let { "${"%.1f".format(java.util.Locale.ROOT, it)} Hz" } ?: "不可用"),
        "系统版本" to "Android ${android.os.Build.VERSION.RELEASE}（API ${android.os.Build.VERSION.SDK_INT}）",
        "处理器架构" to android.os.Build.SUPPORTED_ABIS.joinToString("、")
    )
    OverlayBottomSheet(show = show, title = "硬件信息", onDismissRequest = onDismiss,
        endAction = { IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, contentDescription = "关闭") } }) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 16.dp)) {
            Card(Modifier.fillMaxWidth()) {
                details.forEach { (label, value) -> BasicComponent(title = label, summary = value) }
            }
        }
    }
}
