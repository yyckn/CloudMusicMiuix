package com.hypermusic.miuix.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class KugouLibrary(private val client: KugouClient, private val auth: KugouAuth) {
    data class Collection(val listId: String, val globalId: String, val title: String, val cover: String,
        val count: Int, val owned: Boolean, val liked: Boolean, val album: Boolean, val raw: JsonObject)
    data class Page(val items: List<Collection>, val nextPage: Int?, val total: Int?)

    suspend fun collections(page: Int = 1, size: Int = 100): Page = withContext(Dispatchers.IO) {
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        check(version == auth.sessionGeneration()) { "酷狗音乐账号已切换，请重试" }
        val response = client.userPlaylistRequest(account, page, size)
        check(version == auth.sessionGeneration()) { "酷狗音乐账号已切换，请重试" }
        parsePage(response, page, size)
    }

    suspend fun isAlbumCollected(albumId: Long): Boolean = withContext(Dispatchers.IO) {
        require(albumId > 0)
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        var page = 1
        while (page <= 200) {
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            val result = parsePage(client.userPlaylistRequest(account, page, 100), page, 100)
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            if (result.items.any { it.album && it.globalId == albumId.toString() }) return@withContext true
            page = result.nextPage ?: return@withContext false
        }
        error("酷狗音乐收藏列表过长")
    }

    suspend fun collectAlbum(album: com.hypermusic.miuix.data.model.AlbumInfo, expectedUserId: String? = null, expectedGeneration: Long? = null, favorite: Boolean = true) = withContext(Dispatchers.IO) {
        require(album.id > 0 && (!favorite || (album.artistId > 0 && album.name.isNotBlank()))) { "酷狗音乐专辑缺少收藏所需的作者信息" }
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        check((expectedUserId == null || account.userId == expectedUserId) && (expectedGeneration == null || version == expectedGeneration)) { "酷狗音乐账号已切换，请重试" }
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
        if (favorite) client.addCollection(account, album.name, album.artistId.toString(), album.id.toString(), "", album = true)
        else removeCollection(account, version, album.id.toString(), album = true)
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请刷新收藏状态" }
    }

    suspend fun isPlaylistCollected(id: String): Boolean = withContext(Dispatchers.IO) {
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        var page = 1
        while (page <= 200) {
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            val result = parsePage(client.userPlaylistRequest(account, page, 100), page, 100)
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            if (result.items.any { !it.album && it.globalId == id }) return@withContext true
            page = result.nextPage ?: return@withContext false
        }
        error("酷狗音乐收藏列表过长")
    }

    suspend fun collectPlaylist(playlist: com.hypermusic.miuix.data.model.Playlist, expectedUserId: String? = null, expectedGeneration: Long? = null, favorite: Boolean = true) = withContext(Dispatchers.IO) {
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        check((expectedUserId == null || account.userId == expectedUserId) && (expectedGeneration == null || version == expectedGeneration)) { "酷狗音乐账号已切换，请重试" }
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
        if (favorite) client.addCollection(account, playlist.title, playlist.kugouCreatorId, playlist.kugouOriginalListId,
            playlist.kugouCreatorGid, album = false)
        else removeCollection(account, version, playlist.id, album = false)
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请刷新收藏状态" }
    }

    /** 删除自己的收藏引用（listId），不删除来源歌单，也不允许删除自建/我喜欢歌单。 */
    private fun removeCollection(account: KugouAuth.Account, version: Long, id: String, album: Boolean) {
        var page = 1
        while (page <= 200) {
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            val result = parsePage(client.userPlaylistRequest(account, page, 100), page, 100)
            check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
            val item = result.items.firstOrNull { it.album == album && it.globalId == id }
            if (item != null) {
                check(!item.owned && !item.liked) { "不能取消收藏自己创建的歌单" }
                client.removeCollection(account, item.listId)
                return
            }
            page = result.nextPage ?: return
        }
        error("酷狗音乐收藏列表过长")
    }

    private data class LikedRow(val playlist: Collection, val fileId: String?)
    suspend fun addToOwnedPlaylist(track: Track, listId: String, expectedGeneration: Long) = withContext(Dispatchers.IO) {
        check(auth.sessionGeneration() == expectedGeneration) { "酷狗音乐账号已切换，请重新选择歌单" }
        val account = auth.account() ?: error("请先登录酷狗音乐")
        var page = 1
        var owned = false
        while (!owned && page <= 200) {
            val result = collections(page)
            if (result.items.any { it.listId == listId && it.owned && !it.album }) owned = true
            if (!owned) page = result.nextPage ?: return@withContext error("只能添加到自己的歌单")
        }
        check(owned) { "未找到个人歌单" }
        val resolved = track.copy(kugouHash = client.trackHash(track))
        check(auth.sessionGeneration() == expectedGeneration) { "酷狗音乐账号已切换，请重新选择歌单" }
        client.songMutation(account, listId, resolved, null, true)
        check(auth.sessionGeneration() == expectedGeneration) { "酷狗音乐账号已切换，请刷新收藏状态" }
    }
    private fun findLiked(track: Track, account: KugouAuth.Account, version: Long): LikedRow {
        fun checkAccount() = check(auth.sessionGeneration() == version && auth.account()?.userId == account.userId) { "酷狗音乐账号已切换，请重试" }
        require(track.source == "kugou" && track.kugouHash.matches(Regex("[A-Fa-f0-9]{32}"))) { "酷狗音乐歌曲标识缺失" }
        var page = 1
        var playlist: Collection? = null
        while (page <= 200) {
            checkAccount()
            val data = parsePage(client.userPlaylistRequest(account, page, 100), page, 100)
            checkAccount()
            playlist = data.items.firstOrNull { it.liked }
            if (playlist != null || data.nextPage == null) break
            page = data.nextPage
        }
        val liked = playlist ?: error("酷狗音乐未找到我喜欢歌单")
        check(liked.globalId.isNotBlank() && liked.listId.isNotBlank()) { "酷狗音乐我喜欢歌单标识缺失" }
        var offset = 0
        while (offset < 20_000) {
            checkAccount()
            val data = client.playlistRequest(liked.globalId, offset, 100, account)
            checkAccount()
            val rows = data.getAsJsonArray("songs") ?: error("酷狗音乐喜欢的歌曲列表缺失")
            val total = data.text("count").toIntOrNull()?.takeIf { it >= 0 } ?: error("酷狗音乐喜欢的歌曲数量缺失")
            val matching = rows.firstOrNull { it.isJsonObject && it.asJsonObject.text("hash").equals(track.kugouHash, ignoreCase = true) }
            if (matching != null) return LikedRow(liked, matching.asJsonObject.text("fileid").takeIf { it.isNotBlank() } ?: error("酷狗音乐收藏条目标识缺失"))
            offset += rows.size()
            if (offset >= total) return LikedRow(liked, null)
            check(rows.size() > 0) { "酷狗音乐喜欢的歌曲分页未前进" }
        }
        error("酷狗音乐喜欢的歌曲过多，未能完整读取")
    }

    suspend fun isLiked(track: Track): Boolean = withContext(Dispatchers.IO) {
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        val resolved = track.copy(kugouHash = client.trackHash(track))
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
        findLiked(resolved, account, version).fileId != null
    }

    suspend fun setLiked(track: Track, liked: Boolean, expectedUserId: String? = null) = withContext(Dispatchers.IO) {
        val version = auth.sessionGeneration()
        val account = auth.account() ?: error("请先登录酷狗音乐")
        check(expectedUserId == null || account.userId == expectedUserId) { "酷狗音乐账号已切换，请重试" }
        val resolved = track.copy(kugouHash = client.trackHash(track))
        check(auth.sessionGeneration() == version) { "酷狗音乐账号已切换，请重试" }
        val current = findLiked(resolved, account, version)
        if ((current.fileId != null) != liked) {
            check(auth.sessionGeneration() == version && auth.account()?.userId == account.userId) { "酷狗音乐账号已切换，请重试" }
            client.songMutation(account, current.playlist.listId, resolved, current.fileId, liked)
            check(auth.sessionGeneration() == version && auth.account()?.userId == account.userId) { "酷狗音乐账号已切换，请刷新收藏状态" }
        }
    }

    companion object {
        internal fun parsePage(response: JsonObject, page: Int, size: Int): Page {
            require(page > 0 && size in 1..100)
            val data = response.get("data") ?: error("酷狗音乐收藏响应缺少数据")
            val rows = findList(data) ?: error("酷狗音乐收藏列表缺失")
            val items = rows.mapNotNull { it.takeIf(JsonElement::isJsonObject)?.asJsonObject?.let(::parseCollection) }
            val total = data.takeIf(JsonElement::isJsonObject)?.asJsonObject?.text("total")?.toIntOrNull()?.takeIf { it >= 0 }
            val more = rows.size() == size && (total == null || page.toLong() * size < total)
            return Page(items, if (more) page + 1 else null, total)
        }

        internal fun parseCollection(row: JsonObject): Collection? {
            fun first(vararg names: String) = names.firstNotNullOfOrNull { row.text(it).takeIf(String::isNotBlank) }.orEmpty()
            val albumId = first("musiclib_id", "musicLibId")
            val album = first("source") == "2" || albumId.toLongOrNull()?.let { it > 0 } == true
            val listId = first("listid", "list_id")
            val globalId = if (album) first("album_id", "musiclib_id", "musicLibId", "list_create_listid")
                else first("global_collection_id", "globalCollectionId", "gid", "specialid", "id")
            if (listId.isBlank() && globalId.isBlank()) return null
            val title = first("name", "listname", "specialname", "album_name")
            val cover = first("pic", "img", "image", "cover").replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            val count = first("count", "song_count", "songcount").toIntOrNull()?.coerceAtLeast(0) ?: 0
            val owned = first("type").let { it.isEmpty() || it == "0" }
            val liked = !album && (listId == "2" || title == "我喜欢" || title == "我喜欢的音乐")
            return Collection(listId, globalId, title, cover, count, owned, liked, album, row)
        }

        private fun findList(value: JsonElement): JsonArray? {
            if (value.isJsonArray) return value.asJsonArray
            if (!value.isJsonObject) return null
            val objectValue = value.asJsonObject
            val keys = listOf("lists", "list", "info", "items", "playlists", "data", "result", "response")
            for (key in keys) objectValue.get(key)?.takeIf { it.isJsonArray }?.let { return it.asJsonArray }
            for (key in keys) objectValue.get(key)?.takeIf { it.isJsonObject }?.let { findList(it)?.let { found -> return found } }
            return null
        }
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
