package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageNavigationIconInset
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import android.content.Intent
import com.hypermusic.miuix.ui.navigation.BackHandler
import com.hypermusic.miuix.ui.components.TrackMoreActions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import com.hypermusic.miuix.ui.components.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.data.model.*
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType

@Composable
fun SearchEntry(onClick: () -> Unit) {
    Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
        SearchBar(inputField = {
            InputField(query = "", onQueryChange = {}, onSearch = {}, expanded = false,
                onExpandedChange = { if (it) onClick() }, label = "搜索音乐")
        }, expanded = false, onExpandedChange = {}) {}
        Box(Modifier.matchParentSize().clickable(onClick = onClick))
    }
}

/** 搜索页面临时离开时保留查询、分类和完整结果。 */
class SearchSession {
    var query by mutableStateOf("")
    var submittedQuery by mutableStateOf("")
    var searchGeneration by mutableIntStateOf(0)
    var category by mutableStateOf(SearchCategory.SONG)
    var entities by mutableStateOf<List<SearchEntity>>(emptyList())
    var entityLoading by mutableStateOf(false)
    var entityError by mutableStateOf<String?>(null)
    var lastRequestKey: String? = null
    val entityResults = mutableStateMapOf<String, List<SearchEntity>>()
    val entityErrors = mutableStateMapOf<String, String>()
    val entityOffsets = mutableStateMapOf<String, Int>()
    val entityHasMore = mutableStateMapOf<String, Boolean>()
    val entityPageLoading = mutableStateMapOf<String, Boolean>()
    val entityPageErrors = mutableStateMapOf<String, String>()
    val entityVersions = mutableMapOf<String, Int>()
    var songRequestKey: String? = null
    var refreshGeneration by mutableIntStateOf(0)
    var hasFocused = false
}

@Composable
fun SearchPage(
    viewModel: MusicPlayerViewModel, session: SearchSession, listState: LazyListState, onBack: () -> Unit,
    onPlaylist: (String, String, String) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (Long, Boolean) -> Unit,
    onComment: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember(session) { session::query }
    var submittedQuery by remember(session) { session::submittedQuery }
    var searchGeneration by remember(session) { session::searchGeneration }
    var category by remember(session) { session::category }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val prefs = remember { context.getSharedPreferences("search_history", android.content.Context.MODE_PRIVATE) }
    val gson = remember { Gson() }
    var history by remember {
        mutableStateOf(runCatching {
            val json = prefs.getString("ordered_history", null)
            if (json == null) prefs.getStringSet("history", emptySet()).orEmpty().toList()
            else gson.fromJson<List<String>>(json, object : TypeToken<List<String>>() {}.type).orEmpty()
        }.getOrDefault(emptyList()))
    }
    fun search(text: String) {
        val keyword = text.trim()
        if (keyword.isEmpty()) return
        query = keyword
        submittedQuery = keyword
        searchGeneration++
        history = (listOf(keyword) + history.filterNot { it == keyword }).take(30)
        prefs.edit().putString("ordered_history", gson.toJson(history)).remove("history").apply()
        focusManager.clearFocus()
    }
    val platform = PlatformManager.current
    val state = viewModel.searchState
    var entities by remember(session) { session::entities }
    var entityLoading by remember(session) { session::entityLoading }
    var entityError by remember(session) { session::entityError }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var likeError by remember { mutableStateOf(false) }
    var showUnlike by remember { mutableStateOf(false) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    val refreshState = rememberPullToRefreshState()
    var refreshing by remember { mutableStateOf(false) }
    val requestKey = "$platform:$category:$submittedQuery:$searchGeneration"
    val currentRequestKey by rememberUpdatedState(requestKey)
    val scope = rememberCoroutineScope()
    suspend fun additionalEntities(offset: Int): Triple<List<SearchEntity>, Int, Boolean> = when (platform) {
        MusicPlatform.BODIAN -> BodianCatalog(BodianClient(context)).searchEntities(submittedQuery, category, offset)
            .let { Triple(it.items, it.nextOffset, it.hasMore) }
        MusicPlatform.KUGOU -> {
            val type = when (category) { SearchCategory.PLAYLIST -> "special"; SearchCategory.ALBUM -> "album"; SearchCategory.ARTIST -> "author"; else -> error("不支持的搜索类型") }
            KugouCatalog(KugouClient(context)).searchEntities(type, submittedQuery, offset).let { page ->
                Triple(page.items.mapNotNull { KugouCatalog.parseSearchEntity(it, category) }, page.nextOffset, page.hasMore)
            }
        }
        MusicPlatform.QISHUI -> {
            val catalog = QishuiCatalog(QishuiClient(context))
            when (category) {
                SearchCategory.PLAYLIST -> catalog.searchPlaylists(submittedQuery, offset).let { Triple(it.items.mapNotNull(QishuiCatalog::playlistEntity), it.nextOffset, it.hasMore) }
                SearchCategory.ARTIST -> catalog.searchArtists(submittedQuery, offset).let { Triple(it.items.mapNotNull(QishuiCatalog::artistEntity), it.nextOffset, it.hasMore) }
                SearchCategory.ALBUM -> QishuiShareAlbum.search(submittedQuery, offset).let { Triple(it.items, it.nextOffset, it.hasMore) }
                else -> error("汽水音乐暂未提供该分类搜索接口")
            }
        }
        else -> error("该平台暂不支持分页搜索")
    }
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        if (!session.hasFocused) {
            session.hasFocused = true
            kotlinx.coroutines.delay(350)
            focusRequester.requestFocus()
            keyboard?.show()
        }
    }
    LaunchedEffect(platform, category, submittedQuery, searchGeneration, session.refreshGeneration) {
        if (submittedQuery.isBlank()) { refreshing = false; return@LaunchedEffect }
        if (category == SearchCategory.SONG) {
            if (session.songRequestKey != requestKey) {
                session.songRequestKey = requestKey
                viewModel.clearSearch()
                viewModel.searchSongs(submittedQuery)
            }
        } else {
            if (session.entityResults.containsKey(requestKey) || session.entityErrors.containsKey(requestKey)) {
                entities = session.entityResults[requestKey].orEmpty()
                entityError = session.entityErrors[requestKey]
                entityLoading = false; refreshing = false
                return@LaunchedEffect
            }
            entities = emptyList(); entityError = null; entityLoading = true
            try {
                val result = when (platform) {
                    MusicPlatform.QQ -> QqMusicApi.searchEntities(submittedQuery, category)
                    MusicPlatform.NETEASE -> NeteaseApi.searchEntities(submittedQuery, category)
                    MusicPlatform.KUGOU, MusicPlatform.QISHUI, MusicPlatform.BODIAN -> try {
                        val (items, next, more) = additionalEntities(0)
                        session.entityOffsets[requestKey] = next
                        session.entityHasMore[requestKey] = more && next > 0
                        Result.success(items)
                    } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                    catch (e: Exception) { Result.failure(e) }
                }
                result.fold({ session.entityResults[requestKey] = it; entities = it },
                    { val message = it.message ?: "搜索失败"; session.entityErrors[requestKey] = message; entityError = message })
            } finally { entityLoading = false; refreshing = false }
        }
    }
    LaunchedEffect(state.isLoading, category, refreshing) {
        if (refreshing && category == SearchCategory.SONG && !state.isLoading && session.songRequestKey == requestKey) refreshing = false
    }
    LaunchedEffect(showMore, selectedTrack) {
        liked = null
        likeError = false
        val track = selectedTrack
        if (showMore && track != null) {
            val result = viewModel.trackLiked(track)
            result.fold({ liked = it }, { likeError = true })
        }
    }
    LaunchedEffect(showMore, selectedTrack?.albumId, selectedTrack?.source) {
        albumArtist = null
        albumArtistId = null
        val track = selectedTrack ?: return@LaunchedEffect
        if (!showMore || track.source != "netease") return@LaunchedEffect
        val albumId = track.albumId.removePrefix("nealbum_").toLongOrNull() ?: return@LaunchedEffect
        NeteaseApi.getAlbumDetail(albumId.toString()).onSuccess { (info, _) ->
            albumArtist = info.artist.ifEmpty { null }
            albumArtistId = info.artistId.takeIf { it > 0 }
        }
    }
    Scaffold(modifier = modifier, topBar = {
        BlurredBar(backdrop = backdrop) {
            TopAppBar(title = "搜索", largeTitle = "搜索", scrollBehavior = scrollBehavior,
                actions = { PlatformSwitch() }, defaultWindowInsetsPadding = false,
                navigationIcon = {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回",
                        modifier = Modifier.padding(start = pageNavigationIconInset()).clickable { onBack() })
                }, color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
        }
    }) { padding ->
        PullToRefresh(isRefreshing = refreshing, onRefresh = {
            if (submittedQuery.isNotBlank() && !refreshing) {
                refreshing = true
                if (category == SearchCategory.SONG) { session.songRequestKey = null; viewModel.clearSearch() }
                else { session.entityVersions[requestKey] = (session.entityVersions[requestKey] ?: 0) + 1; session.entityResults.remove(requestKey); session.entityErrors.remove(requestKey); session.entityOffsets.remove(requestKey); session.entityHasMore.remove(requestKey); session.entityPageLoading.remove(requestKey); session.entityPageErrors.remove(requestKey); entities = emptyList(); entityError = null }
                session.refreshGeneration++
            }
        }, pullToRefreshState = refreshState, topAppBarScrollBehavior = scrollBehavior,
            refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = pageViewportTopInset(padding.calculateTopPadding())).pageLayerBackdrop(backdrop)) {
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()), bottom = FeedBottomPads.Standard), overscrollEffect = null) {
            item {
                SearchBar(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), inputField = {
                    InputField(modifier = Modifier.focusRequester(focusRequester), query = query, onQueryChange = {
                        query = it
                        if (it.isEmpty()) { submittedQuery = ""; viewModel.clearSearch() }
                    }, onSearch = { search(it) }, expanded = false, onExpandedChange = {}, label = "搜索音乐")
                }, expanded = false, onExpandedChange = {}) {}
            }
            if (submittedQuery.isNotBlank()) {
                item {
                    Box(Modifier.padding(start = 12.dp, bottom = 4.dp).widthIn(max = 283.dp)) {
                        androidx.compose.runtime.CompositionLocalProvider(
                            top.yukonga.miuix.kmp.material.LocalSoftGlassTabsEnabled provides false
                        ) {
                            TabRow(tabs = SearchCategory.entries.map { it.title }, selectedTabIndex = category.ordinal,
                                onTabSelected = { category = SearchCategory.entries[it] }, minWidth = 64.dp, maxWidth = 64.dp)
                        }
                    }
                }
                if ((category == SearchCategory.SONG && state.isLoading) || (category != SearchCategory.SONG && entityLoading)) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() } }
                } else {
                    val error = if (category == SearchCategory.SONG) state.error else entityError
                    if (error != null) item { Text("搜索失败：$error", modifier = Modifier.padding(16.dp)) }
                    else if (category == SearchCategory.SONG) {
                        if (state.results.isEmpty() && !state.hasMore) item { Text("没有找到单曲", modifier = Modifier.padding(16.dp)) }
                        items(state.results) { track ->
                            SharedTrackRow(
                                track = track,
                                isPlaying = track.id == viewModel.playerState.currentTrack?.id && track.source == viewModel.playerState.currentTrack?.source,
                                onClick = { viewModel.playTrackWithMode(track, state.results) },
                                onMoreClick = { selectedTrack = track; showMore = true },
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        }
                        if (state.hasMore) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (state.isLoadingMore) InfiniteProgressIndicator()
                                else TextButton(text = state.loadMoreError?.let { "加载失败，点击重试" } ?: "加载更多",
                                    onClick = { viewModel.loadMoreSearchSongs() })
                            }
                        }
                    } else {
                        if (entities.isEmpty() && session.entityHasMore[requestKey] != true) item { Text("没有找到${category.title}", modifier = Modifier.padding(16.dp)) }
                        items(entities) { entity ->
                            val detail = when (category) {
                                SearchCategory.PLAYLIST -> listOf(entity.creator.ifBlank { "作者未知" }, "共${entity.songCount?.toString() ?: "未知"}首", "${entity.playCount?.toString() ?: "未知"}次播放").joinToString("，")
                                SearchCategory.ALBUM -> "${entity.creator.ifBlank { "作者未知" }}，发布于${entity.date.ifBlank { "未知" }}"
                                SearchCategory.ARTIST -> "共${entity.songCount?.toString() ?: "未知"}首歌曲，${entity.albumCount?.toString() ?: "未知"}张专辑"
                                else -> ""
                            }
                            SearchResultRow(entity.title, detail, entity.imageUrl, round = category == SearchCategory.ARTIST, onClick = {
                                when (category) {
                                    SearchCategory.PLAYLIST -> onPlaylist(entity.id, entity.imageUrl, entity.title)
                                    SearchCategory.ALBUM -> if (platform in setOf(MusicPlatform.NETEASE, MusicPlatform.QQ, MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI)) entity.id.toLongOrNull()?.let { onAlbum(it, platform == MusicPlatform.QQ) }
                                        else android.widget.Toast.makeText(context, "该平台的专辑页正在接入", android.widget.Toast.LENGTH_SHORT).show()
                                    SearchCategory.ARTIST -> if (platform in setOf(MusicPlatform.NETEASE, MusicPlatform.QQ, MusicPlatform.BODIAN, MusicPlatform.KUGOU, MusicPlatform.QISHUI)) onArtist(when (platform) { MusicPlatform.QQ -> "qqartist_${entity.id}"; MusicPlatform.BODIAN -> "bodianartist_${entity.id}"; MusicPlatform.KUGOU -> "kugouartist_${entity.id}"; MusicPlatform.QISHUI -> "qishuiartist_${entity.id}"; else -> "neartist_${entity.id}" })
                                        else android.widget.Toast.makeText(context, "该平台的歌手页正在接入", android.widget.Toast.LENGTH_SHORT).show()
                                    else -> Unit
                                }
                            })
                        }
                        if (session.entityHasMore[requestKey] == true) item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                if (session.entityPageLoading[requestKey] == true) InfiniteProgressIndicator() else TextButton(
                                    text = if (session.entityPageErrors[requestKey] != null) "加载失败，点击重试" else "加载更多",
                                    onClick = {
                                        val key = requestKey
                                        val generation = session.entityVersions[key] ?: 0
                                        val offset = session.entityOffsets[key] ?: 0
                                        session.entityPageLoading[key] = true
                                        session.entityPageErrors.remove(key)
                                        scope.launch {
                                            try {
                                                val (rows, next, more) = additionalEntities(offset)
                                                if ((session.entityVersions[key] ?: 0) == generation && session.entityOffsets[key] == offset) {
                                                    val combined = (session.entityResults[key].orEmpty() + rows).distinctBy { it.id }
                                                    session.entityResults[key] = combined
                                                    session.entityOffsets[key] = next
                                                    session.entityHasMore[key] = more && next > offset
                                                    if (currentRequestKey == key) entities = combined
                                                }
                                            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
                                            catch (e: Exception) {
                                                if ((session.entityVersions[key] ?: 0) == generation && session.entityOffsets[key] == offset)
                                                    session.entityPageErrors[key] = e.message ?: "加载失败"
                                            }
                                            finally {
                                                if ((session.entityVersions[key] ?: 0) == generation) session.entityPageLoading.remove(key)
                                            }
                                        }
                                    })
                            }
                        }
                    }
                }
            } else if (history.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("搜索历史")
                        Text("清除", modifier = Modifier.clickable { history = emptyList(); prefs.edit().remove("history").remove("ordered_history").apply() }, color = MiuixTheme.colorScheme.primary)
                    }
                }
                item {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        history.forEach { word -> TextButton(text = word, onClick = { search(word) }) }
                    }
                }
            } else item { Text("输入关键词搜索音乐", modifier = Modifier.padding(16.dp)) }
        }
        }
        val selectedIsLiked = liked == true
        OverlayBottomSheet(
            show = showMore,
            title = "更多",
            insideMargin = DpSize(12.dp, 0.dp),
            startAction = null,
            endAction = {
                IconButton(
                    onClick = { showMore = false }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Close,
                        contentDescription = "关闭",
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            },
            onDismissRequest = { showMore = false }
        ) {
            LazyColumn(
                modifier = Modifier.padding(bottom = 32.dp)
            ) {
                item {
                    TrackMoreActions(liked = selectedIsLiked,
                        likeLabel = when {
                            likeError -> "收藏状态获取失败"
                            liked == null -> "正在获取收藏状态"
                            selectedIsLiked -> "取消收藏"
                            else -> "收藏"
                        }, likeEnabled = liked != null,
                        onShare = {
                            showMore = false
                            selectedTrack?.let { track ->
                                context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}")
                                }, "分享歌曲"))
                            }
                        },
                        onLike = {
                            showMore = false
                            if (selectedIsLiked) showUnlike = true else selectedTrack?.let { track ->
                                viewModel.setTrackLiked(track, true)
                            }
                        },
                        onComment = { showMore = false; selectedTrack?.let {
                            if (it.source in setOf("netease", "qqmusic")) onComment(it)
                            else android.widget.Toast.makeText(context, "该平台的评论正在接入", android.widget.Toast.LENGTH_SHORT).show()
                        } })
                }

                // 歌手与专辑区域
                item { AddTrackToPlaylistAction(selectedTrack) }
                item { SmallTitle("歌手与专辑") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        selectedTrack?.let { track ->
                            // 显示所有歌手
                            val artistNames = track.artistNames
                            val artistIds = track.artistIds
                            if (artistNames.isNotEmpty()) {
                                artistNames.forEachIndexed { artistIndex, artistName ->
                                    val artistId = if (artistIndex < artistIds.size) artistIds[artistIndex] else ""
                                    ArrowPreference(
                                        title = "歌手：$artistName",
                                        onClick = {
                                            showMore = false
                                            val finalArtistId = if (artistId.isNotEmpty()) artistId else track.artistId
                                            if (finalArtistId.isNotEmpty()) {
                                                val prefix = when (track.source) { "bodian" -> "bodianartist_"; "kugou" -> "kugouartist_"; "qishui" -> "qishuiartist_"; else -> "" }
                                                onArtist(if (prefix.isNotEmpty() && !finalArtistId.startsWith(prefix)) prefix + finalArtistId else finalArtistId)
                                            }
                                        }
                                    )
                                }
                            } else {
                                ArrowPreference(
                                    title = "歌手：${track.artist}",
                                    onClick = {
                                        showMore = false
                                        if (track.artistId.isNotEmpty()) {
                                            val prefix = when (track.source) { "bodian" -> "bodianartist_"; "kugou" -> "kugouartist_"; "qishui" -> "qishuiartist_"; else -> "" }
                                            onArtist(if (prefix.isNotEmpty() && !track.artistId.startsWith(prefix)) prefix + track.artistId else track.artistId)
                                        }
                                    }
                                )
                            }
                            // 显示专辑
                            ArrowPreference(
                                title = "专辑：${track.album}",
                                onClick = {
                                    showMore = false
                                    if (track.albumId.isNotEmpty()) {
                                        val albumIdStr = track.albumId.removePrefix("nealbum_")
                                        val albumId = albumIdStr.toLongOrNull()
                                        if (albumId != null) {
                                            onAlbum(albumId, selectedTrack?.source == "qqmusic")
                                        }
                                    }
                                }
                            )
                            // 显示专辑歌手
                            if (albumArtist != null) {
                                ArrowPreference(
                                    title = "专辑歌手：$albumArtist",
                                    onClick = {
                                        showMore = false
                                        if (albumArtistId != null) {
                                            onArtist("neartist_$albumArtistId")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                // 下载区域
                item { SmallTitle("下载") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.surfaceContainer
                        )
                    ) {
                        ArrowPreference(
                            title = "下载此歌曲",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                selectedTrack?.let { viewModel.downloadCurrentTrack(it) }
                            }
                        )
                        ArrowPreference(
                            title = "下载封面图片",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Download,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                viewModel.downloadCover(selectedTrack)
                            }
                        )
                    }
                }
            }
        }
        top.yukonga.miuix.kmp.overlay.OverlayDialog(show = showUnlike, title = "取消收藏", summary = "确定要取消收藏这首歌曲吗？",
            onDismissRequest = { showUnlike = false }, content = {
                Row { TextButton(text = "取消", onClick = { showUnlike = false }, modifier = Modifier.weight(1f)); Spacer(Modifier.width(12.dp));
                    TextButton(text = "确定", onClick = {
                        showUnlike = false
                        selectedTrack?.let { track ->
                            viewModel.setTrackLiked(track, false)
                        }
                    }, modifier = Modifier.weight(1f)) }
            })
    }
}

@Composable
private fun SearchResultRow(title: String, summary: String, image: String, round: Boolean = false, vip: Boolean = false,
                            isPlaying: Boolean = false, onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = com.hypermusic.miuix.ui.components.songRowBackground(isPlaying)),
        pressFeedbackType = PressFeedbackType.Sink,
        showIndication = true,
        onClick = onClick
    ) {
        BasicComponent(startAction = {
            AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(LocalContext.current, if (round) com.hypermusic.miuix.data.cache.ImageCaches.Kind.ARTIST else com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL), model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
        }, endActions = { trailing?.invoke() }) {
            Text(title, fontSize = MiuixTheme.textStyles.headline1.fontSize, fontWeight = FontWeight.Medium,
                color = if (isPlaying) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vip) {
                    Text("VIP", fontSize = 10.sp, color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.border(1.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(summary, style = MiuixTheme.textStyles.body2, color = if (isPlaying) MiuixTheme.colorScheme.primary.copy(alpha = 0.7f) else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
