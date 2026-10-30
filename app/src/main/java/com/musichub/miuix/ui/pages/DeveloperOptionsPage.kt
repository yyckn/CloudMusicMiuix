package com.musichub.miuix.ui.pages

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.musichub.miuix.data.api.NeteaseApi
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.ui.FeedBottomPads
import com.musichub.miuix.ui.components.BlurredBar
import com.musichub.miuix.ui.components.pageLayerBackdrop
import com.musichub.miuix.ui.components.rememberPageBlurBackdrop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
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
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scrollBehavior = MiuixScrollBehavior()
    val blurBackdrop = rememberPageBlurBackdrop()

    var showRawLyrics by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var rawText by remember { mutableStateOf("") }
    var errorText by remember { mutableStateOf<String?>(null) }

    fun loadRawLyrics() {
        val track = currentTrack
        if (track == null) {
            Toast.makeText(context, "当前没有播放歌曲", Toast.LENGTH_SHORT).show()
            return
        }
        rawText = ""
        errorText = null
        isLoading = true
        showRawLyrics = true
        scope.launch {
            NeteaseApi.getRawLyric(track.id).fold(
                onSuccess = { raw -> rawText = buildRawLyricsText(raw) },
                onFailure = { e -> errorText = e.message ?: "加载失败" }
            )
            isLoading = false
        }
    }

    fun copyRawLyrics() {
        if (rawText.isBlank()) return
        val clipboard = context.getSystemService(ClipboardManager::class.java)
        clipboard?.setPrimaryClip(ClipData.newPlainText("raw_lyrics", rawText))
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
            item { SmallTitle("歌词调试") }
            item {
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 12.dp)
                ) {
                    ArrowPreference(
                        title = "查看当前播放歌曲的原歌词",
                        summary = if (currentTrack == null) {
                            "当前没有播放歌曲"
                        } else {
                            "${currentTrack.title} - ${currentTrack.artist}"
                        },
                        onClick = { loadRawLyrics() }
                    )
                }
            }
        }

        OverlayBottomSheet(
            show = showRawLyrics,
            title = "原始歌词",
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 460.dp)
                ) {
                    when {
                        isLoading -> {
                            InfiniteProgressIndicator(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            )
                        }

                        errorText != null -> {
                            Text(
                                text = errorText ?: "加载失败",
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
                                    text = rawText,
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

private fun buildRawLyricsText(raw: NeteaseApi.RawLyrics): String {
    val builder = StringBuilder()

    fun appendSection(title: String, content: String) {
        if (content.isBlank()) return
        if (builder.isNotEmpty()) builder.append("\n\n")
        builder.append("【").append(title).append("】\n").append(content)
    }

    appendSection("YRC 逐字", raw.yrc)
    appendSection("LRC", raw.lrc)
    appendSection("翻译 YTLRC", raw.ytlrc)
    appendSection("翻译 TLYRIC", raw.tlyric)
    appendSection("罗马音 YROMALRC", raw.yromalrc)
    appendSection("罗马音 ROMALRC", raw.romalrc)

    return if (builder.isEmpty()) "未获取到歌词" else builder.toString()
}
