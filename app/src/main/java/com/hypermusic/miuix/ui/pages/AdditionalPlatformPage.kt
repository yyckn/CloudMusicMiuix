package com.hypermusic.miuix.ui.pages
import com.hypermusic.miuix.ui.components.pageViewportTopInset
import com.hypermusic.miuix.ui.components.pageScrollTopInset

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.ui.components.AsyncImage
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.ui.components.BlurredBar
import com.hypermusic.miuix.ui.components.SearchPlatformActions
import com.hypermusic.miuix.ui.components.pageLayerBackdrop
import com.hypermusic.miuix.ui.components.rememberPageBlurBackdrop
import com.hypermusic.miuix.ui.components.sharedCover
import com.hypermusic.miuix.ui.components.playlistCoverKey
import com.hypermusic.miuix.ui.components.albumCoverKey
import com.hypermusic.miuix.ui.FeedBottomPads
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.utils.overScrollVertical
import top.yukonga.miuix.kmp.utils.PressFeedbackType

internal data class ExtraCollection(val id: String, val title: String, val cover: String, val count: Int,
    val album: Boolean = false, val personal: Boolean = false)

internal class AdditionalLibraryCache {
    var loaded = false
    var accountRevision = -1L
    var libraryRevision = -1
    var loading by mutableStateOf(false)
    var loggedIn by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var collections by mutableStateOf<List<ExtraCollection>>(emptyList())
    val listState = LazyListState()
    val recommendListState = LazyListState()
}

@Composable
internal fun AdditionalPlatformPage(platform: MusicPlatform, tab: Int, onLogin: () -> Unit,
    onSearch: () -> Unit, showSearchAction: Boolean, onPlaylist: (String, String, String) -> Unit, onAlbum: (Long, String) -> Unit,
    cache: AdditionalLibraryCache, recommendations: AdditionalRecommendations, onRadio: (String) -> Unit,
    onLocalMusicClick: () -> Unit = {}) {
    if (tab == 0) {
        AdditionalRecommendPage(platform, recommendations, cache.recommendListState, onSearch, showSearchAction, onPlaylist, onRadio)
        return
    }
    val context = LocalContext.current
    val backdrop = rememberPageBlurBackdrop()
    val behavior = MiuixScrollBehavior()
    val refresh = rememberPullToRefreshState()
    val recommendListState = rememberLazyListState()
    var revision by remember(platform) { mutableIntStateOf(0) }
    var handledRevision by remember(platform) { mutableIntStateOf(0) }
    var loading by cache::loading
    var loggedIn by cache::loggedIn
    var error by cache::error
    var collections by cache::collections
    val libraryRevision = AdditionalLibraryChanges.revision(platform)
    LaunchedEffect(platform, tab, revision, libraryRevision) {
        if (tab != 1) return@LaunchedEffect
        val accountRevision = when (platform) {
            MusicPlatform.BODIAN -> BodianAuth(context, BodianClient(context)).sessionRevision()
            MusicPlatform.KUGOU -> KugouAuth(context, KugouClient(context)).sessionGeneration()
            MusicPlatform.QISHUI -> QishuiAuth(context, QishuiClient(context)).sessionGeneration()
            else -> 0L
        }
        if (revision == handledRevision && cache.loaded && cache.accountRevision == accountRevision && cache.libraryRevision == libraryRevision) return@LaunchedEffect
        handledRevision = revision
        cache.loaded = false
        loading = true; error = null; collections = emptyList()
        try {
            collections = when (platform) {
                MusicPlatform.BODIAN -> {
                    val api = BodianClient(context); val auth = BodianAuth(context, api)
                    loggedIn = auth.currentAccount() != null
                    if (!loggedIn) emptyList() else {
                        val accountRevision = auth.sessionRevision()
                        val library = BodianLibrary(api, auth, BodianCatalog(api))
                        val data = library.playlists()
                        val personalIds = (listOfNotNull(data.liked) + data.created).map { it.id }.toSet()
                        val all = (listOfNotNull(data.liked) + data.created + data.collected.items).toMutableList()
                        var collected = data.collected
                        var pages = 1
                        while (collected.hasMore && pages++ < 200) {
                            val previous = collected.nextOffset
                            collected = library.collectedPlaylists(previous)
                            check(!collected.hasMore || collected.nextOffset > previous) { "波点音乐歌单分页异常" }
                            all += collected.items
                        }
                        check(!collected.hasMore) { "波点音乐收藏歌单过多，未能完整加载" }
                        var albums = library.albums()
                        all += albums.items
                        pages = 1
                        while (albums.hasMore && pages++ < 200) {
                            val previous = albums.nextOffset
                            albums = library.albums(previous)
                            check(!albums.hasMore || albums.nextOffset > previous) { "波点音乐专辑分页异常" }
                            all += albums.items
                        }
                        check(!albums.hasMore) { "波点音乐收藏专辑过多，未能完整加载" }
                        check(auth.sessionRevision() == accountRevision) { "波点音乐账号已切换，请刷新" }
                        all.distinctBy { it.id to it.source }
                            .map { ExtraCollection("bodian:${it.source}:${it.id}", it.title, it.cover, it.count, it.isAlbum, !it.isAlbum && it.id in personalIds) }
                    }
                }
                MusicPlatform.KUGOU -> {
                    val api = KugouClient(context); val auth = KugouAuth(context, api)
                    loggedIn = auth.account() != null
                    if (!loggedIn) emptyList() else {
                        val accountGeneration = auth.sessionGeneration()
                        val list = mutableListOf<KugouLibrary.Collection>(); var page: Int? = 1
                        while (page != null && page <= 200) {
                            val result = KugouLibrary(api, auth).collections(page)
                            list += result.items; page = result.nextPage
                        }
                        check(page == null) { "酷狗音乐收藏列表过长" }
                        check(auth.sessionGeneration() == accountGeneration) { "酷狗音乐账号已切换，请刷新" }
                        list.map { ExtraCollection(it.globalId, it.title, it.cover, it.count, it.album, !it.album && (it.owned || it.liked)) }
                    }
                }
                MusicPlatform.QISHUI -> {
                    val api = QishuiClient(context); val auth = QishuiAuth(context, api)
                    loggedIn = auth.account() != null
                    if (!loggedIn) emptyList() else {
                        val accountGeneration = auth.sessionGeneration()
                        val library = QishuiLibrary(api, auth); val rows = mutableListOf<QishuiLibrary.Playlist>()
                        for (collected in listOf(false, true)) {
                            var cursor = ""; val seen = mutableSetOf<String>()
                            do {
                                check(seen.add(cursor) && seen.size <= 500) { "汽水音乐收藏分页异常" }
                                val page = if (collected) library.collectedPlaylists(cursor) else library.playlists(cursor)
                                rows += page.items
                                cursor = if (page.hasMore) requireNotNull(page.nextCursor) else ""
                            } while (cursor.isNotEmpty())
                        }
                        check(auth.sessionGeneration() == accountGeneration) { "汽水音乐账号已切换，请刷新" }
                        val playlists = rows.distinctBy { it.id }.mapNotNull {
                            QishuiCatalog.playlistEntity(it.data)?.let { entity ->
                                ExtraCollection(entity.id, entity.title, entity.imageUrl, entity.songCount?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0, personal = it.owned)
                            }
                        }
                        val albums = mutableListOf<ExtraCollection>(); var cursor = ""; val seen = mutableSetOf<String>()
                        try {
                            do {
                                check(seen.add(cursor) && seen.size <= 500) { "汽水音乐收藏专辑分页异常" }
                                val page = library.collectedAlbums(cursor)
                                albums += page.items.map { ExtraCollection(it.id.toString(), it.name, it.picUrl, it.size, album = true) }
                                cursor = if (page.hasMore) requireNotNull(page.nextCursor) else ""
                            } while (cursor.isNotEmpty())
                        } catch (e: CancellationException) { throw e }
                        catch (e: Exception) { error = "专辑收藏加载失败：${e.message ?: "未知错误"}" }
                        check(auth.sessionGeneration() == accountGeneration) { "汽水音乐账号已切换，请刷新" }
                        playlists + albums.distinctBy { it.id }
                    }
                }
                else -> emptyList()
            }
            // A partial result can still be displayed, but it must not suppress
            // a retry when the user returns to the library after an error.
            cache.loaded = error == null
            cache.accountRevision = accountRevision
            cache.libraryRevision = libraryRevision
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { cache.loaded = false; error = e.message ?: "收藏加载失败" }
        finally { loading = false }
    }
    Scaffold(topBar = { BlurredBar(backdrop = backdrop) {
        TopAppBar(title = if (tab == 0) "推荐" else "我的收藏", largeTitle = if (tab == 0) "推荐" else "我的收藏",
            actions = { SearchPlatformActions(onSearch, showSearchAction) }, scrollBehavior = behavior, defaultWindowInsetsPadding = false)
    } }) { padding ->
        PullToRefresh(isRefreshing = loading, onRefresh = { revision++ }, pullToRefreshState = refresh,
            contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding())),
            topAppBarScrollBehavior = behavior, refreshTexts = listOf("继续下拉以刷新", "松手后刷新", "正在刷新...", "刷新成功"),
            modifier = Modifier.padding(top = pageViewportTopInset(padding.calculateTopPadding())).pageLayerBackdrop(backdrop)) {
            LazyColumn(state = if (tab == 1) cache.listState else recommendListState, modifier = Modifier.fillMaxSize().overScrollVertical().nestedScroll(behavior.nestedScrollConnection),
                contentPadding = PaddingValues(top = pageScrollTopInset(padding.calculateTopPadding()) + if (tab == 1) 12.dp else 0.dp, bottom = FeedBottomPads.More), overscrollEffect = null) {
                if (tab == 1) {
                    item { com.hypermusic.miuix.ui.components.LocalMusicCard(onLocalMusicClick) }
                    if (!loggedIn && !loading && error == null) item { Card(Modifier.padding(12.dp)) { BasicComponent(title = "点击登录", summary = "登录后查看收藏", onClick = onLogin) } }
                    error?.let { message -> item { Text(message, modifier = Modifier.padding(16.dp)) } }
                    val sections = listOf("个人收藏" to collections.filter { it.personal },
                        "歌单" to collections.filter { !it.personal && !it.album }, "专辑" to collections.filter { it.album })
                    sections.forEach { (title, rows) ->
                    if (rows.isNotEmpty()) item { SmallTitle(title) }
                    items(rows) { item ->
                        Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 12.dp), pressFeedbackType = PressFeedbackType.Sink, onClick = {
                            if (item.album) item.id.substringAfterLast(':').toLongOrNull()?.let { onAlbum(it, item.cover) }
                            else if (item.id.isNotBlank() && !item.album) onPlaylist(item.id, item.cover, item.title)
                            else android.widget.Toast.makeText(context, "该收藏的详情正在接入", android.widget.Toast.LENGTH_SHORT).show()
                        }) {
                            BasicComponent(title = item.title, summary = if (item.album) "专辑" else "共${item.count}首",
                                startAction = {
                                    AsyncImage(imageLoader = com.hypermusic.miuix.data.cache.ImageCaches.get(context, com.hypermusic.miuix.data.cache.ImageCaches.Kind.THUMBNAIL),
                                        model = item.cover, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(56.dp)
                                            .sharedCover(if (item.album) item.id.substringAfterLast(':').toLongOrNull()?.let { albumCoverKey(it, false) } else playlistCoverKey(item.id))
                                            .clip(RoundedCornerShape(8.dp)))
                                    Spacer(Modifier.width(12.dp))
                                })
                        }
                    }
                    }
                }
            }
        }
    }
}
