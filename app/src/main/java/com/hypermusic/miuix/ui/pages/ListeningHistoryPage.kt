package com.hypermusic.miuix.ui.pages

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.history.*
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.shareText
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.time.*
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.async

private val ListeningBarColumnMaxWidth = 48.dp
private val ListeningBarSpacing = 6.dp
private val ListeningStatsCardPadding = 20.dp
private val ListeningCalendarCellSize = 50.dp
private val ListeningCalendarCircleMaxSize = 44.dp
private val ListeningCalendarSpacing = 6.dp

private fun listeningBarCardMaxWidth(barCount: Int) =
    ListeningStatsCardPadding * 2 + ListeningBarColumnMaxWidth * barCount + ListeningBarSpacing * (barCount - 1).coerceAtLeast(0)

@Composable
fun ListeningHistoryPage(viewModel: MusicPlayerViewModel, onBack: () -> Unit, onComment: (Track) -> Unit,
    modifier: Modifier = Modifier, onArtist: (String) -> Unit = {}, onAlbum: (Long) -> Unit = {}) {
    val context = LocalContext.current
    val accountId = NeteaseApi.getAccounts().getOrNull(viewModel.dataAccountIndex)?.userId
        ?: NeteaseApi.getCurrentUserId()?.toString().orEmpty()
    val session = remember(accountId) { viewModel.listeningHistorySession.account(accountId) }
    var tab by session::tab
    val offset = session.offsets[tab]
    val records = session.records
    val cloudRecords = session.cloudRecords
    val revision by ListeningHistory.changes.collectAsState()
    var selected by remember { mutableStateOf<Track?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var likeCheckFailed by remember { mutableStateOf(false) }
    var unlikeTrack by remember { mutableStateOf<Track?>(null) }
    var albumArtist by remember { mutableStateOf<AlbumInfo?>(null) }
    var refresh by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val start = when (tab) {
        0 -> today
        1 -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY)).plusWeeks(offset.toLong())
        2 -> today.withDayOfMonth(1).plusMonths(offset.toLong())
        else -> today.withDayOfYear(1).plusYears(offset.toLong())
    }
    val end = when (tab) { 0 -> today; 1 -> start.plusDays(6); 2 -> start.with(TemporalAdjusters.lastDayOfMonth()); else -> start.with(TemporalAdjusters.lastDayOfYear()) }
    val periodType = listOf("day", "week", "month", "year")[tab]
    val period = session.period(periodType, start, end)
    var remote by period::snapshot
    var remoteError by period::error
    var loading by period::loading
    LaunchedEffect(accountId, revision, refresh) {
        if (session.revision == revision && refresh == 0) return@LaunchedEffect
        session.reading = true
        try {
            val loaded = kotlinx.coroutines.coroutineScope {
                val localJob = async { ListeningHistory.records(accountId) }
                val cloudJob = async { ListeningHistory.cloudRecords(accountId) }
                localJob.await() to cloudJob.await()
            }
            val merged = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                ListeningHistoryMerger.timeline(loaded.first, loaded.second, accountId)
            }
            session.records = loaded.first
            session.cloudRecords = loaded.second
            session.timeline = com.hypermusic.miuix.data.cache.reconcilePageItems(session.timeline, merged) { it.key }
            session.revision = revision
        } finally { session.reading = false }
    }
    LaunchedEffect(showMore, selected) {
        if (!showMore) return@LaunchedEffect
        liked = null
        likeCheckFailed = false
        selected?.let { viewModel.trackLiked(it).fold(onSuccess = { value -> liked = value }, onFailure = { likeCheckFailed = true }) }
    }
    LaunchedEffect(showMore, selected?.albumId) {
        if (!showMore) return@LaunchedEffect
        albumArtist = null
        selected?.albumId?.removePrefix("nealbum_")?.toLongOrNull()?.takeIf { it > 0 }?.let { id ->
            NeteaseApi.getAlbumDetail(id.toString()).onSuccess { (info, _) -> albumArtist = info }
        }
    }
    LaunchedEffect(accountId, period, refresh) {
        if (period.attempted && !refreshing) return@LaunchedEffect
        if (accountId.isBlank()) {
            loading = false; refreshing = false; remoteError = "登录网易云后可自动整合云端听歌记录"
            period.attempted = true
            return@LaunchedEffect
        }
        loading = true
        try {
            if (remote == null) remote = NeteaseListeningRepository.cached(context, accountId, periodType, start, end)
            val fresh = NeteaseListeningRepository.refresh(context, accountId, periodType, start, end, offset == 0)
            if (fresh.connected) remote = fresh
            remoteError = fresh.errors.firstOrNull()
            period.attempted = true
        } catch (error: kotlinx.coroutines.CancellationException) { throw error }
        catch (error: Exception) { remoteError = error.message ?: "获取网易云听歌记录失败"; period.attempted = true }
        finally { loading = false; refreshing = false }
    }
    val timeline = session.timeline
    val stats = remember(timeline, remote, start, end) {
        ListeningHistoryMerger.stats(timeline, remote?.summaries().orEmpty(), start, end)
    }
    val days = stats.days
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    val pullToRefreshState = rememberPullToRefreshState()
    com.hypermusic.miuix.ui.navigation.BackHandler(onBack = onBack)
    Scaffold(modifier = modifier, topBar = {
        BlurredBar(backdrop = backdrop) {
            TopAppBar(title = "听歌记录", largeTitle = "听歌记录", scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                color = if (backdrop != null) androidx.compose.ui.graphics.Color.Transparent else MiuixTheme.colorScheme.surface,
                navigationIcon = { PageBackButton(onClick = onBack) })
        }
    }) { padding ->
        val top = pageScrollTopInset(padding.calculateTopPadding())
        val viewport = pageViewportTopInset(padding.calculateTopPadding())
        PullToRefresh(isRefreshing = refreshing, onRefresh = { if (!loading) { refreshing = true; refresh++ } },
            pullToRefreshState = pullToRefreshState, topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            contentPadding = PaddingValues(top = top), modifier = Modifier.padding(top = viewport)) {
        LazyColumn(state = session.listStates[tab], modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = top, bottom = FeedBottomPads.Standard)) {
            item { TabRow(listOf("日", "周", "月", "年"), tab, { tab = it }, Modifier.fillMaxWidth().padding(vertical = 8.dp)) }
            if (tab == 0) {
                if (timeline.isEmpty() && !session.reading && !loading) item { HistoryNote("听歌超过10秒后，将在这里记录歌曲和播放时间") }
                if ((loading || session.reading) && timeline.isEmpty()) item { Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() } }
                timeline.groupBy { Instant.ofEpochMilli(it.playedAt).atZone(ZoneId.systemDefault()).toLocalDate() }.forEach { (date, entries) ->
                    item(key = date.toString()) {
                        // The list already contributes 12dp: keep the standard 28dp title inset.
                        SmallTitle(ListeningStats.dateLabel(date), insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(entries, key = { it.key }) { record ->
                        val time = ListeningStats.playbackTime(record.playedAt)
                        val source = if (record.local != null) "本地" else "网易云"
                        val listening = record.listenedMs?.let { ms ->
                            " 已听${ListeningStats.duration(ms, fractionalMinutes = true)}"
                        }.orEmpty()
                        SharedTrackRow(record.track, isPlaying = viewModel.playerState.currentTrack?.id == record.track.id,
                            onClick = { viewModel.playTrackWithMode(record.track, timeline.map { it.track }.distinctBy { it.id }) },
                            onMoreClick = { selected = record.track; showMore = true },
                            modifier = Modifier.animateItem(),
                            supportingText = "$time 来自$source$listening",
                            durationLabel = record.track.duration.takeIf { it >= 3_600_000L }?.let { ListeningStats.duration(it) })
                    }
                }
                item { HistoryNote(if (cloudRecords.isNotEmpty()) "已自动合并本机与网易云记录，相同播放不会重复显示" else remoteError ?: "云端最近播放最多返回300条，已获取的记录会保存在本机") }
            } else {
                item {
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        TextButton("上一${listOf("", "周", "月", "年")[tab]}", onClick = { session.offsets[tab]-- }, enabled = start.isAfter(today.minusYears(3)))
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            Text(if (tab == 3) "${start.year}年" else if (tab == 2) "${start.year}年${start.monthValue}月" else "${start.monthValue}.${start.dayOfMonth} - ${end.monthValue}.${end.dayOfMonth}")
                        }
                        TextButton("下一${listOf("", "周", "月", "年")[tab]}", onClick = { session.offsets[tab]++ }, enabled = offset < 0)
                    }
                }
                if (loading && remote == null) item { Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() } }
                item {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val cardMaxWidth = when (tab) {
                        1 -> listeningBarCardMaxWidth(7)
                        2 -> ListeningStatsCardPadding * 2 + ListeningCalendarCellSize * 7 + ListeningCalendarSpacing * 6
                        3 -> listeningBarCardMaxWidth(12)
                        else -> 680.dp
                    }
                    Card(Modifier.widthIn(max = cardMaxWidth).fillMaxWidth()) {
                        Column(Modifier.padding(ListeningStatsCardPadding)) {
                            Text(listOf("", "本周收听时长", "本月收听时长", "全年收听时长")[tab], style = MiuixTheme.textStyles.title3)
                            Spacer(Modifier.height(12.dp))
                            Text(if (stats.totalMs == 0L && stats.activeDates.isNotEmpty()) "未提供时长" else ListeningStats.duration(stats.totalMs), style = MiuixTheme.textStyles.title1)
                            if (stats.totalEstimated) Text("含云端估算时长", style = MiuixTheme.textStyles.footnote1,
                                color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                            val periodDays = java.time.temporal.ChronoUnit.DAYS.between(start, end) + 1
                            Text("已听 ${stats.activeDates.size}/$periodDays 天 · ${stats.songCount}首歌曲", color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f))
                            Spacer(Modifier.height(24.dp))
                            when (tab) {
                                1 -> ListeningBars((0..6).map { start.plusDays(it.toLong()) }.map { "日一二三四五六"[it.dayOfWeek.value % 7].toString() to (days[it] ?: 0L) })
                                2 -> ListeningCalendar(YearMonth.from(start), days, stats.activeDates)
                                else -> ListeningBars((1..12).map { month -> "$month" to days.filterKeys { it.monthValue == month }.values.sum() })
                            }
                        }
                    }
                    }
                }
                item { HistoryNote(when {
                    remote?.stale == true -> "云端部分数据暂不可用，保留上次报告并合并本机记录" + if (stats.estimatedDays) "；部分日期含估算时长" else ""
                    remote?.connected == true -> "已合并本机与网易云听歌记录" + if (stats.estimatedDays) "；部分日期时长按云端歌曲长度估算，官方总时长与图表可能不同" else "；官方数据按网易云更新周期刷新"
                    cloudRecords.isNotEmpty() -> "当前显示已保存的本机与网易云记录；${remoteError ?: "云端更新中"}" + if (stats.estimatedDays) "；云端缺失的收听时长按歌曲长度估算" else ""
                    else -> "${remoteError ?: "正在获取网易云听歌报告"}。当前统计仅包含本机记录。"
                }) }
                remote?.cumulativeMs()?.let { total -> item { HistoryNote("网易云累计收听：${ListeningStats.duration(total)}") } }
                if (records.any { it.eligible && !it.uploaded }) item { HistoryNote("有听歌记录等待同步。网络异常的记录将在下次打开应用时重试。") }
            }
        }
        }
    }
    OverlayBottomSheet(show = showMore, title = "更多", insideMargin = androidx.compose.ui.unit.DpSize(12.dp, 0.dp),
        startAction = null, onDismissRequest = { showMore = false },
        endAction = {
            IconButton(onClick = { showMore = false }) {
                Icon(MiuixIcons.Close, contentDescription = "关闭", tint = MiuixTheme.colorScheme.onSurface)
            }
        }) {
        selected?.let { track ->
            LazyColumn(Modifier.padding(bottom = 32.dp)) {
                item {
                    TrackMoreActions(liked = liked == true, likeEnabled = liked != null,
                        likeLabel = when { likeCheckFailed -> "收藏状态获取失败"; liked == null -> "正在获取收藏状态"; liked == true -> "取消收藏"; else -> "收藏" },
                        onShare = {
                            showMore = false
                            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"; putExtra(Intent.EXTRA_TEXT, track.shareText())
                            }, "分享歌曲"))
                        },
                        onLike = {
                            if (liked == true) unlikeTrack = track else viewModel.setTrackLiked(track, true)
                            showMore = false
                        },
                        onComment = { showMore = false; onComment(track) })
                }
                item { AddTrackToPlaylistAction(track) }
                item { SmallTitle("歌手") }
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                        val names = track.artistNames.ifEmpty { listOf(track.artist) }
                        names.forEachIndexed { index, name ->
                            val id = track.artistIds.getOrNull(index)?.takeIf { it.isNotBlank() } ?: track.artistId
                            ArrowPreference(title = "歌手：$name", onClick = {
                                showMore = false
                                if (id.isNotBlank()) onArtist(id)
                            })
                        }
                        albumArtist?.takeIf { it.artist.isNotBlank() && it.artistId > 0 }?.let { info ->
                            ArrowPreference(title = "专辑歌手：${info.artist}", onClick = { showMore = false; onArtist("neartist_${info.artistId}") })
                        }
                        ArrowPreference(title = "专辑：${track.album}", onClick = {
                            showMore = false
                            track.albumId.removePrefix("nealbum_").toLongOrNull()?.takeIf { it > 0 }?.let(onAlbum)
                        })
                    }
                }
                item { SmallTitle("下载") }
                item {
                    Card(Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                        ArrowPreference(title = "下载此歌曲", startAction = { Icon(MiuixIcons.Download, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            onClick = { showMore = false; viewModel.downloadCurrentTrack(track) })
                        ArrowPreference(title = "下载封面图片", startAction = { Icon(MiuixIcons.Download, contentDescription = null, modifier = Modifier.size(24.dp)) },
                            onClick = { showMore = false; viewModel.downloadCover(track) })
                    }
                }
            }
        }
    }
    OverlayDialog(show = unlikeTrack != null, title = "取消收藏", summary = "确定要取消收藏这首歌曲吗？",
        onDismissRequest = { unlikeTrack = null }, content = {
            Row {
                TextButton("取消", onClick = { unlikeTrack = null }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(20.dp))
                TextButton("确定", onClick = {
                    unlikeTrack?.let { viewModel.setTrackLiked(it, false) }
                    unlikeTrack = null
                }, modifier = Modifier.weight(1f), colors = ButtonDefaults.textButtonColorsPrimary())
            }
        })
}

@Composable private fun HistoryNote(text: String) {
    Text(text, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurface.copy(alpha = .6f), modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp))
}

@Composable private fun ListeningBars(values: List<Pair<String, Long>>) {
    val max = (values.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(60_000)
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MiuixTheme.textStyles.footnote1
    Row(Modifier.fillMaxWidth().height(210.dp), horizontalArrangement = Arrangement.spacedBy(ListeningBarSpacing), verticalAlignment = Alignment.Bottom) {
        values.forEach { (label, value) ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val availableWidth = constraints.maxWidth
                    val labelText = remember(value, availableWidth, textMeasurer, labelStyle) {
                        ListeningStats.chartDuration(value) { candidate ->
                            textMeasurer.measure(candidate, style = labelStyle, softWrap = false, maxLines = 1).size.width <= availableWidth
                        }
                    }
                    Text(labelText, style = labelStyle, maxLines = 1, softWrap = false,
                        autoSize = TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = labelStyle.fontSize, stepSize = .5.sp))
                }
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth(.7f).height(if (value > 0) (155f * value / max).coerceAtLeast(3f).dp else 2.dp)
                    .clip(RoundedCornerShape(30.dp)).background(if (value > 0) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = .08f)))
                Spacer(Modifier.height(10.dp)); Text(label, style = MiuixTheme.textStyles.footnote1)
            }
        }
    }
}

@Composable private fun ListeningCalendar(month: YearMonth, days: Map<LocalDate, Long>, activeDates: Set<LocalDate>) {
    val padding = month.atDay(1).dayOfWeek.value % 7
    var selected by remember(month) { mutableStateOf<LocalDate?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ListeningCalendarSpacing)) {
        "日一二三四五六".forEach { Text(it.toString(), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
    }
    Spacer(Modifier.height(12.dp))
    val count = ((padding + month.lengthOfMonth() + 6) / 7) * 7
    (0 until count step 7).forEach { row ->
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ListeningCalendarSpacing)) {
            (0..6).forEach { col ->
                val day = row + col - padding + 1
                Box(Modifier.weight(1f).height(ListeningCalendarCellSize).padding(3.dp), contentAlignment = Alignment.Center) {
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        val active = date in activeDates
                        Box(Modifier.sizeIn(maxWidth = ListeningCalendarCircleMaxSize, maxHeight = ListeningCalendarCircleMaxSize).aspectRatio(1f).fillMaxWidth()
                            .clip(CircleShape).background(if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = .05f))
                            .clickable { selected = date }, contentAlignment = Alignment.Center) {
                            Text(day.toString(), color = if (active) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface.copy(alpha = .5f))
                        }
                    }
                }
            }
        }
    }
    selected?.let { Text("${it} · " + if ((days[it] ?: 0L) > 0) ListeningStats.duration(days.getValue(it)) else if (it in activeDates) "有听歌记录，未提供时长" else "暂无听歌记录", modifier = Modifier.padding(top = 12.dp), style = MiuixTheme.textStyles.footnote1) }
}
