package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.hypermusic.miuix.data.api.*
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.overlay.OverlayBottomSheet

private data class PlaylistTarget(val id: String, val title: String, val append: suspend () -> Unit)

@Composable
internal fun AddTrackToPlaylistAction(track: Track?) {
    if (track?.source !in setOf("bodian", "kugou", "qishui")) return
    var show by remember(track?.source, track?.id) { mutableStateOf(false) }
    Card(modifier = Modifier.padding(horizontal = 12.dp).padding(bottom = 12.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)) {
        ArrowPreference(title = "添加到个人歌单", onClick = { show = true })
    }
    AddTrackToPlaylistSheet(show = show, track = track, onDismiss = { show = false })
}

@Composable
internal fun AddTrackToPlaylistSheet(show: Boolean, track: Track?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var targets by remember(track?.source, track?.id) { mutableStateOf(emptyList<PlaylistTarget>()) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var retry by remember { mutableIntStateOf(0) }
    LaunchedEffect(show, track, retry) {
        if (!show || track == null) return@LaunchedEffect
        loading = true; error = null; targets = emptyList()
        try {
            targets = when (track.source) {
                "bodian" -> {
                    val client = BodianClient(context); val auth = BodianAuth(context, client)
                    val revision = auth.sessionRevision(); val api = BodianLibrary(client, auth, BodianCatalog(client))
                    val lists = api.playlists()
                    (listOfNotNull(lists.liked) + lists.created).distinctBy { it.id }.map { list ->
                        PlaylistTarget(list.id, list.title) { api.updateOwnedPlaylist(list.id, listOf(track.id), false, revision) }
                    }
                }
                "kugou" -> {
                    val client = KugouClient(context); val auth = KugouAuth(context, client)
                    val revision = auth.sessionGeneration(); val api = KugouLibrary(client, auth)
                    val lists = mutableListOf<KugouLibrary.Collection>(); var page: Int? = 1
                    val seen = mutableSetOf<Int>()
                    while (page != null) {
                        check(seen.add(page) && seen.size <= 200) { "酷狗音乐歌单分页异常" }
                        val result = api.collections(page); lists += result.items; page = result.nextPage
                    }
                    lists.filter { it.owned && !it.album }.distinctBy { it.listId }.map { list ->
                        PlaylistTarget(list.listId, list.title) { api.addToOwnedPlaylist(track, list.listId, revision) }
                    }
                }
                "qishui" -> {
                    val client = QishuiClient(context); val auth = QishuiAuth(context, client)
                    val revision = auth.sessionGeneration(); val api = QishuiLibrary(client, auth)
                    val lists = mutableListOf<QishuiLibrary.Playlist>(); var cursor = ""
                    val seen = mutableSetOf<String>()
                    do {
                        check(seen.add(cursor) && seen.size <= 500) { "汽水音乐歌单分页异常" }
                        val result = api.playlists(cursor); lists += result.items
                        cursor = if (result.hasMore) requireNotNull(result.nextCursor) else ""
                    } while (cursor.isNotEmpty())
                    lists.filter { it.owned }.distinctBy { it.id }.map { list ->
                        PlaylistTarget(list.id, QishuiCatalog.playlistEntity(list.data)?.title ?: "个人歌单") {
                            api.editPlaylist(list.id, listOf(track.id.removePrefix("qishui_")), false, revision)
                        }
                    }
                }
                else -> kotlin.error("此平台暂未接入个人歌单编辑")
            }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { error = e.message ?: "歌单加载失败" }
        finally { loading = false }
    }
    OverlayBottomSheet(show = show, title = "添加到歌单", insideMargin = DpSize(12.dp, 0.dp),
        onDismissRequest = { if (!saving) onDismiss() }) {
        LazyColumn(Modifier.navigationBarsPadding().padding(bottom = 24.dp)) {
            when {
                loading -> item { Text("正在加载个人歌单…", Modifier.padding(24.dp)) }
                error != null -> item { ArrowPreference(title = error ?: "加载失败", summary = "点击重试", onClick = { retry++ }) }
                targets.isEmpty() -> item { Text("暂无可添加的个人歌单", Modifier.padding(24.dp)) }
                else -> items(targets, key = { it.id }) { target ->
                    ArrowPreference(title = target.title, enabled = !saving, onClick = {
                        scope.launch {
                            saving = true
                            try {
                                target.append()
                                val platform = when (track?.source) { "bodian" -> MusicPlatform.BODIAN; "kugou" -> MusicPlatform.KUGOU; else -> MusicPlatform.QISHUI }
                                AdditionalLibraryChanges.changed(platform)
                                android.widget.Toast.makeText(context, "已添加到歌单", android.widget.Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } catch (e: CancellationException) { throw e }
                            catch (e: Exception) { android.widget.Toast.makeText(context, e.message ?: "添加失败", android.widget.Toast.LENGTH_LONG).show() }
                            finally { saving = false }
                        }
                    })
                }
            }
        }
    }
}
