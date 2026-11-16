package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.cache.ImageCaches
import com.hypermusic.miuix.data.lyrics.LyricMatches
import com.hypermusic.miuix.data.lyrics.LyricsRepository
import com.hypermusic.miuix.data.lyrics.LyricFeatures
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Ok
import top.yukonga.miuix.kmp.icon.extended.Reset

private val lyricPlatforms = listOf("netease", "qq", "kugou", "apple")
private val lyricPlatformNames = listOf("网易云音乐", "QQ音乐", "酷狗音乐", "Apple Music")

@Composable
internal fun LyricTrackRow(track: Track, selected: Boolean = false, onClick: () -> Unit = {}, onDelete: (() -> Unit)? = null, supportingText: String? = null, features: LyricFeatures? = null) {
    Card(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer), onClick = onClick) {
        BasicComponent(startAction = {
            AsyncImage(imageLoader = ImageCaches.get(LocalContext.current, ImageCaches.Kind.THUMBNAIL), model = track.imgUrl,
                contentDescription = null, modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop, error = com.hypermusic.miuix.ui.components.rememberMusicImageFallbackPainter())
        }, endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val sec = track.duration / 1000
                Text(if (sec > 0) "%d:%02d".format(sec / 60, sec % 60) else "--:--", fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                if (onDelete != null) {
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Delete, "删除匹配", tint = MiuixTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                    }
                } else if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Icon(MiuixIcons.Ok, "已选择", tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                }
            }
        }) {
            Text(track.title, fontSize = MiuixTheme.textStyles.headline1.fontSize, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.artist} - ${track.album}", fontSize = MiuixTheme.textStyles.body2.fontSize,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!features?.labels.isNullOrEmpty()) {
                FlowRow(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    features!!.labels.forEach { label ->
                        Text(label, fontSize = 10.sp, color = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.border(1.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 1.dp))
                    }
                }
            }
            if (supportingText != null) {
                Text(supportingText, fontSize = MiuixTheme.textStyles.body2.fontSize,
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun MatchCandidateRow(show: Boolean, platform: String, track: Track, selected: Boolean,
    features: Map<String, LyricFeatures?>, limiter: Semaphore,
    onFeatures: (String, LyricFeatures?) -> Unit, onClick: () -> Unit) {
    val key = "$platform:${track.id}"
    LaunchedEffect(show, key) {
        if (!show || features.containsKey(key)) return@LaunchedEffect
        val result = limiter.withPermit {
            try { LyricsRepository.matchFeatures(platform, track) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { null }
        }
        onFeatures(key, result)
    }
    LyricTrackRow(track, selected = selected, onClick = onClick, features = features[key])
}

@Composable
internal fun LyricMatchSheet(show: Boolean, original: Track?, currentLyricSource: String?, lyricsLoading: Boolean, onDismiss: () -> Unit, onChanged: () -> Unit, currentLyrics: SongLyrics? = null) {
    val context = LocalContext.current
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val tabTextStyle = TextStyle(fontSize = MiuixTheme.textStyles.body1.fontSize, fontWeight = FontWeight.Bold)
    val tabWidth = with(density) {
        lyricPlatformNames.maxOf { textMeasurer.measure(it, style = tabTextStyle, maxLines = 1).size.width }.toDp() + 24.dp
    }
    var platform by remember(original?.source, original?.id, show) {
        mutableIntStateOf(LyricMatches.find(original ?: return@remember mutableIntStateOf(0))?.let { lyricPlatforms.indexOf(it.platform).coerceAtLeast(0) } ?: 0)
    }
    var selected by remember(original?.source, original?.id, show) { mutableStateOf(original?.let { LyricMatches.find(it)?.match }) }
    var selectedPlatform by remember(original?.source, original?.id, show) { mutableStateOf(original?.let { LyricMatches.find(it)?.platform }) }
    var results by remember(original?.source, original?.id, show) { mutableStateOf<Map<Int, List<Track>>>(emptyMap()) }
    var features by remember(original?.source, original?.id, show) { mutableStateOf<Map<String, LyricFeatures?>>(emptyMap()) }
    val featureLimiter = remember { Semaphore(3) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(show, original?.source, original?.id, platform, retry) {
        if (!show || original == null || results.containsKey(platform)) return@LaunchedEffect
        loading = true; error = null
        try {
            val found = LyricsRepository.searchMatches(lyricPlatforms[platform], original)
            results = results + (platform to found)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "请稍后重试" }
        finally { loading = false }
    }
    OverlayBottomSheet(show = show, title = "匹配歌词", insideMargin = DpSize(12.dp, 0.dp),
        startAction = { IconButton(onClick = {
            original?.let { LyricMatches.remove(it); onChanged() }
            selected = null; selectedPlatform = null
        }) { Icon(MiuixIcons.Reset, "重置匹配") } },
        endAction = { IconButton(onClick = {
            if (original != null && selected != null && selectedPlatform != null) {
                if (!com.hypermusic.miuix.data.lyrics.LyricsMatch.durationMatches(original.duration, selected!!.duration)) {
                    android.widget.Toast.makeText(context, "歌曲与歌词的时长相差超过6秒，无法匹配", android.widget.Toast.LENGTH_SHORT).show()
                    return@IconButton
                }
                LyricMatches.save(original, selectedPlatform!!, selected!!); onChanged()
            }
            onDismiss()
        }) { Icon(MiuixIcons.Ok, "保存") } }, onDismissRequest = onDismiss) {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp), contentPadding = PaddingValues(bottom = 40.dp)) {
            if (original != null) item {
                val sourceText = when {
                    lyricsLoading -> "正在获取歌词"
                    !currentLyricSource.isNullOrBlank() -> "当前歌词由${currentLyricSource}提供"
                    else -> "暂无可用歌词"
                }
                LyricTrackRow(original, supportingText = sourceText, features = currentLyrics?.takeUnless { lyricsLoading }?.let {
                    LyricFeatures.from(it, if (currentLyricSource == "Apple Music") "apple" else "")
                })
            }
            item {
                androidx.compose.runtime.CompositionLocalProvider(
                    top.yukonga.miuix.kmp.material.LocalSoftGlassTabsEnabled provides false
                ) {
                    TabRow(tabs = lyricPlatformNames, selectedTabIndex = platform, onTabSelected = { platform = it },
                        minWidth = tabWidth, maxWidth = tabWidth, modifier = Modifier.padding(start = 12.dp, top = 8.dp, bottom = 4.dp))
                }
            }
            val saved = selected?.takeIf { selectedPlatform == lyricPlatforms[platform] }
            val found = results[platform].orEmpty()
            if (saved != null && found.none { it.id == saved.id }) {
                item(key = "saved:${lyricPlatforms[platform]}:${saved.id}") {
                    MatchCandidateRow(show, lyricPlatforms[platform], saved, true, features, featureLimiter,
                        onFeatures = { key, value -> features = features + (key to value) },
                        onClick = { selected = saved; selectedPlatform = lyricPlatforms[platform] })
                }
            }
            if (loading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() } }
            else if (error != null) item {
                Text("搜索失败：$error，点击重试", Modifier.clickable { retry++ }.padding(16.dp))
            }
            else {
                if (found.isEmpty() && saved == null) item { Text("没有找到歌曲", Modifier.padding(16.dp)) }
                items(found, key = { "${lyricPlatforms[platform]}:${it.id}" }) { candidate ->
                    MatchCandidateRow(show, lyricPlatforms[platform], candidate,
                        selectedPlatform == lyricPlatforms[platform] && selected?.id == candidate.id,
                        features, featureLimiter, onFeatures = { key, value -> features = features + (key to value) },
                        onClick = { selected = candidate; selectedPlatform = lyricPlatforms[platform] })
                }
            }
        }
    }
}
