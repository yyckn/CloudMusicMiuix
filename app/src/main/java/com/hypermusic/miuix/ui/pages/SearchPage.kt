package com.hypermusic.miuix.ui.pages

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Comment
import coil.compose.AsyncImage
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.data.model.*
import com.hypermusic.miuix.ui.FeedBottomPads
import com.hypermusic.miuix.ui.components.*
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel
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

@Composable
fun SearchPage(
    viewModel: MusicPlayerViewModel, onBack: () -> Unit,
    onPlaylist: (String, String, String) -> Unit,
    onArtist: (String) -> Unit,
    onAlbum: (Long, Boolean) -> Unit,
    onComment: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var submittedQuery by remember { mutableStateOf("") }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var category by remember { mutableStateOf(SearchCategory.SONG) }
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
    var entities by remember { mutableStateOf<List<SearchEntity>>(emptyList()) }
    var entityLoading by remember { mutableStateOf(false) }
    var entityError by remember { mutableStateOf<String?>(null) }
    var selectedTrack by remember { mutableStateOf<Track?>(null) }
    var showMore by remember { mutableStateOf(false) }
    var liked by remember { mutableStateOf<Boolean?>(null) }
    var likeError by remember { mutableStateOf(false) }
    var showUnlike by remember { mutableStateOf(false) }
    var albumArtist by remember { mutableStateOf<String?>(null) }
    var albumArtistId by remember { mutableStateOf<Long?>(null) }
    val scrollBehavior = MiuixScrollBehavior()
    val backdrop = rememberPageBlurBackdrop()
    BackHandler { onBack() }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        focusRequester.requestFocus()
        keyboard?.show()
    }
    LaunchedEffect(platform, category, submittedQuery, searchGeneration) {
        entities = emptyList(); entityError = null; entityLoading = false
        if (submittedQuery.isBlank()) return@LaunchedEffect
        if (category == SearchCategory.SONG) {
            viewModel.clearSearch()
            viewModel.searchSongs(submittedQuery)
        } else {
            viewModel.clearSearch()
            entityLoading = true
            val result = if (platform == MusicPlatform.QQ) QqMusicApi.searchEntities(submittedQuery, category)
                else NeteaseApi.searchEntities(submittedQuery, category)
            result.fold({ entities = it }, { entityError = it.message ?: "搜索失败" })
            entityLoading = false
        }
    }
    LaunchedEffect(showMore, selectedTrack) {
        liked = null
        likeError = false
        val track = selectedTrack
        if (showMore && track != null) {
            val result = if (track.source == "qqmusic") QqMusicApi.isLiked(track) else NeteaseApi.checkSongLiked(track.id)
            result.fold({ liked = it }, { likeError = true })
        }
    }
    LaunchedEffect(showMore, selectedTrack?.albumId, selectedTrack?.source) {
        albumArtist = null
        albumArtistId = null
        val track = selectedTrack ?: return@LaunchedEffect
        if (!showMore || track.source == "qqmusic") return@LaunchedEffect
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
                        modifier = Modifier.padding(start = 8.dp).clickable { onBack() })
                }, color = if (backdrop != null) Color.Transparent else MiuixTheme.colorScheme.surface)
        }
    }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())
            .overScrollVertical().nestedScroll(scrollBehavior.nestedScrollConnection).pageLayerBackdrop(backdrop),
            contentPadding = PaddingValues(bottom = FeedBottomPads.Standard), overscrollEffect = null) {
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
                        TabRow(tabs = SearchCategory.entries.map { it.title }, selectedTabIndex = category.ordinal,
                            onTabSelected = { category = SearchCategory.entries[it] }, minWidth = 64.dp, maxWidth = 64.dp)
                    }
                }
                if ((category == SearchCategory.SONG && state.isLoading) || (category != SearchCategory.SONG && entityLoading)) {
                    item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { InfiniteProgressIndicator() } }
                } else {
                    val error = if (category == SearchCategory.SONG) state.error else entityError
                    if (error != null) item { Text("搜索失败：$error", modifier = Modifier.padding(16.dp)) }
                    else if (category == SearchCategory.SONG) {
                        if (state.results.isEmpty()) item { Text("没有找到单曲", modifier = Modifier.padding(16.dp)) }
                        items(state.results) { track ->
                            SearchResultRow(track.title, "${track.artist} - ${track.album}", track.imgUrl, vip = track.isVip,
                                onClick = { viewModel.playTrackWithMode(track, state.results) },
                                trailing = { IconButton(onClick = { selectedTrack = track; showMore = true }) { Icon(MiuixIcons.More, "更多") } })
                        }
                    } else {
                        if (entities.isEmpty()) item { Text("没有找到${category.title}", modifier = Modifier.padding(16.dp)) }
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
                                    SearchCategory.ALBUM -> entity.id.toLongOrNull()?.let { onAlbum(it, platform == MusicPlatform.QQ) }
                                    SearchCategory.ARTIST -> onArtist(if (platform == MusicPlatform.QQ) "qqartist_${entity.id}" else "neartist_${entity.id}")
                                    else -> Unit
                                }
                            })
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
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(history) { word -> TextButton(text = word, onClick = { search(word) }) }
                    }
                }
            } else item { Text("输入关键词搜索音乐", modifier = Modifier.padding(16.dp)) }
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
                // 歌曲区域
                item { SmallTitle("歌曲") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        ArrowPreference(
                            title = "分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                selectedTrack?.let { track ->
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}")
                                    }, "分享歌曲"))
                                }
                            }
                        )
                        ArrowPreference(
                            title = when {
                                likeError -> "收藏状态获取失败"
                                liked == null -> "正在获取收藏状态"
                                selectedIsLiked -> "取消收藏"
                                else -> "收藏"
                            },
                            startAction = {
                                Icon(
                                    imageVector = if (selectedIsLiked) MiuixIcons.FavoritesFill else MiuixIcons.Favorites,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp),
                                    tint = if (selectedIsLiked) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                                )
                            },
                            onClick = {
                                if (liked == null) return@ArrowPreference
                                showMore = false
                                if (selectedIsLiked) {
                                    showUnlike = true
                                } else {
                                    selectedTrack?.let { track ->
                                        if (track.source == "qqmusic") viewModel.setQqTrackLiked(track, true) {
                                            android.widget.Toast.makeText(context, "已收藏", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                        else viewModel.setNeteaseTrackLiked(track, true)
                                    }
                                }
                            }
                        )
                        ArrowPreference(
                            title = "评论",
                            startAction = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Comment,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                selectedTrack?.let { track ->
                                    onComment(track)
                                }
                            }
                        )
                    }
                }

                // 歌手与专辑区域
                item { SmallTitle("歌手与专辑") }
                item {
                    Card(
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.defaultColors(
                            color = MiuixTheme.colorScheme.secondaryContainer
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
                                                onArtist(finalArtistId)
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
                                            onArtist(track.artistId)
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
                            color = MiuixTheme.colorScheme.secondaryContainer
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
                            title = "下载此歌曲并分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                selectedTrack?.let { viewModel.downloadAndShareCurrentTrack(it) }
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
                                selectedTrack?.let { track ->
                                    if (track.imgUrl.isNotBlank()) {
                                        runCatching {
                                            val manager = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
                                            val safeTitle = track.title.replace(Regex("[\\\\/:*?\\\"<>|]"), "_")
                                            manager.enqueue(android.app.DownloadManager.Request(android.net.Uri.parse(track.imgUrl))
                                                .setTitle("下载封面图片").setDescription("正在下载封面图片")
                                                .setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                                .setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_PICTURES, "CloudMusic/$safeTitle.jpg"))
                                        }.onSuccess {
                                            android.widget.Toast.makeText(context, "已保存到 /Pictures/CloudMusic/ 目录", android.widget.Toast.LENGTH_SHORT).show()
                                        }.onFailure {
                                            android.widget.Toast.makeText(context, "保存失败：${it.message}", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        )
                        ArrowPreference(
                            title = "下载封面图片并分享",
                            startAction = {
                                Icon(
                                    imageVector = MiuixIcons.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(24.dp)
                                )
                            },
                            onClick = {
                                showMore = false
                                selectedTrack?.let { viewModel.downloadAndShareCover(it) }
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
                            if (track.source == "qqmusic") viewModel.setQqTrackLiked(track, false) {
                                android.widget.Toast.makeText(context, "已取消收藏", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            else viewModel.setNeteaseTrackLiked(track, false)
                        }
                    }, modifier = Modifier.weight(1f)) }
            })
    }
}

@Composable
private fun SearchResultRow(title: String, summary: String, image: String, round: Boolean = false, vip: Boolean = false,
                            onClick: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
        pressFeedbackType = PressFeedbackType.Tilt,
        showIndication = true,
        onClick = onClick
    ) {
        BasicComponent(onClick = onClick, startAction = {
            AsyncImage(model = image, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(48.dp).clip(if (round) CircleShape else RoundedCornerShape(8.dp)))
            Spacer(Modifier.width(12.dp))
        }, endActions = { trailing?.invoke() }) {
            Text(title, style = MiuixTheme.textStyles.headline1, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (vip) {
                    Text("VIP", style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.border(1.dp, MiuixTheme.colorScheme.primary, RoundedCornerShape(4.dp)).padding(horizontal = 3.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(summary, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
