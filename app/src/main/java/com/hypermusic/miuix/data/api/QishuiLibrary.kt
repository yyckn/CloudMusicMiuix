package com.hypermusic.miuix.data.api

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 汽水音乐账号目录和收藏操作。账号发生变化时丢弃旧请求的结果。 */
internal class QishuiLibrary(private val client: QishuiClient, private val auth: QishuiAuth) {
    data class Playlist(val id: String, val ownerId: String, val data: JsonObject, val liked: Boolean, val owned: Boolean)
    data class Page<T>(val items: List<T>, val nextCursor: String?, val hasMore: Boolean)
    private data class Account(val session: QishuiSessionStore.Session, val generation: Long)

    private fun account(): Account {
        val generation = auth.sessionGeneration()
        val session = auth.validSession() ?: error("请先登录汽水音乐")
        check(generation == auth.sessionGeneration()) { "汽水音乐账号已切换，请重试" }
        return Account(session, generation)
    }
    private fun checkAccount(account: Account) {
        check(auth.sessionGeneration() == account.generation) { "汽水音乐账号已切换，请重试" }
    }
    private fun request(account: Account, path: String, body: JsonObject): JsonObject {
        checkAccount(account)
        val session = account.session
        val result = client.authenticated(path, body, session.privateKey,
            QishuiGuard.Ticket(session.ticket, session.certificate, session.timestampSignature), session.userId)
        checkAccount(account)
        return result
    }

    suspend fun playlists(cursor: String = "", limit: Int = 100): Page<Playlist> = withContext(Dispatchers.IO) {
        require(limit in 1..100 && cursor.length <= 512)
        val account = account()
        playlistPage(account, cursor, limit)
    }

    suspend fun collectedPlaylists(cursor: String = "", limit: Int = 100): Page<Playlist> = withContext(Dispatchers.IO) {
        require(limit in 1..100 && cursor.length <= 512)
        val data = request(account(), "/me/collection/mixed/", JsonObject().apply {
            addProperty("count", limit); addProperty("cursor", cursor)
            add("item_types", JsonArray().apply { add("playlist") })
        })
        val rows = data.array("mixed_collections") ?: error("汽水音乐收藏歌单缺失")
        parsePage(data, rows, cursor, owned = false)
    }

    suspend fun collectedAlbums(cursor: String = "", limit: Int = 100): Page<com.hypermusic.miuix.data.model.AlbumInfo> = withContext(Dispatchers.IO) {
        require(limit in 1..100 && cursor.length <= 512)
        val data = request(account(), "/me/collection/mixed/", JsonObject().apply {
            addProperty("count", limit); addProperty("cursor", cursor)
            add("item_types", JsonArray().apply { add("album") })
        })
        val rows = data.array("mixed_collections") ?: error("汽水音乐收藏专辑缺失")
        parseAlbumPage(data, rows, cursor)
    }

    private fun playlistPage(account: Account, cursor: String, limit: Int): Page<Playlist> {
        val data = request(account, "/me/playlist/", JsonObject().apply { addProperty("count", limit); addProperty("cursor", cursor) })
        val rows = data.array("playlists") ?: data.array("playlist_list") ?: error("汽水音乐账号歌单缺失")
        return parsePage(data, rows, cursor, owned = true)
    }

    suspend fun isSongLiked(id: String): Boolean = withContext(Dispatchers.IO) {
        requireId(id)
        val account = account()
        val seen = mutableSetOf<String>()
        var cursor = ""
        var liked: Playlist? = null
        for (pageNumber in 0 until 20) {
            check(seen.add(cursor)) { "汽水音乐账号歌单游标重复" }
            val page = playlistPage(account, cursor, 100)
            liked = page.items.firstOrNull { it.liked }
            if (liked != null || !page.hasMore) break
            cursor = requireNotNull(page.nextCursor)
        }
        val playlist = liked ?: error("汽水音乐未找到喜欢的歌单")
        val trackCursors = mutableSetOf<String>()
        cursor = ""
        repeat(500) {
            check(trackCursors.add(cursor)) { "汽水音乐喜欢的歌曲游标重复" }
            checkAccount(account)
            val data = client.read("/playlist/detail/", JsonObject().apply {
                addProperty("playlist_id", playlist.id); addProperty("owner_id", playlist.ownerId)
                addProperty("count", 100); addProperty("cursor", cursor); addProperty("type", 1)
            })
            checkAccount(account)
            val rows = data.array("media_entities") ?: data.array("media_list") ?: error("汽水音乐喜欢的歌曲列表缺失")
            val found = containsTrack(rows, id)
            if (found) return@withContext true
            if (data.get("has_more")?.asBoolean != true) return@withContext false
            val next = data.text("next_cursor")
            check(next.isNotBlank() && next !in trackCursors && rows.size() > 0) { "汽水音乐喜欢的歌曲游标未前进" }
            cursor = next
        }
        error("汽水音乐喜欢的歌曲分页次数过多")
    }

    suspend fun setSongLiked(id: String, liked: Boolean, expectedUserId: String? = null) = withContext(Dispatchers.IO) {
        val account = account()
        check(expectedUserId == null || account.session.userId == expectedUserId) { "汽水音乐账号已切换，请重试" }
        request(account, if (liked) "/media/collect/" else "/media/cancel/collect/", JsonObject().apply {
            add("media", media(listOf(id)))
            if (liked) { addProperty("scene", ""); addProperty("collect_action", 1) }
        })
        Unit
    }

    suspend fun editPlaylist(id: String, ids: List<String>, remove: Boolean, expectedGeneration: Long? = null) = withContext(Dispatchers.IO) {
        requireId(id)
        val account = account()
        check(expectedGeneration == null || account.generation == expectedGeneration) { "汽水音乐账号已切换，请重新选择歌单" }
        val seen = mutableSetOf<String>()
        var cursor = ""
        var owned = false
        while (!owned) {
            check(seen.add(cursor) && seen.size <= 500) { "汽水音乐个人歌单分页异常" }
            val page = playlistPage(account, cursor, 100)
            owned = page.items.any { it.id == id && it.owned && it.ownerId == account.session.userId }
            if (owned) break
            check(page.hasMore) { "只能修改当前账号的个人歌单" }
            cursor = requireNotNull(page.nextCursor)
        }
        request(account, if (remove) "/playlist/delete/" else "/playlist/append/", JsonObject().apply {
            addProperty("playlist_id", id); add("media", media(ids))
        })
        Unit
    }

    companion object {
        internal fun parseAlbumPage(data: JsonObject, rows: JsonArray, cursor: String): Page<com.hypermusic.miuix.data.model.AlbumInfo> {
            val albumRows = rows.filter { element ->
                val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: error("汽水音乐收藏条目格式暂不支持")
                check(item.obj("album") != null || item.obj("playlist") != null) { "汽水音乐收藏条目格式暂不支持" }
                item.obj("album") != null
            }
            val albums = albumRows.mapNotNull { element ->
                val album = element.takeIf { it.isJsonObject }?.asJsonObject?.obj("album") ?: return@mapNotNull null
                val base = album.obj("base_info") ?: album
                val id = base.text("id").toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                val artists = base.getAsJsonArray("artists")?.mapNotNull { it.takeIf { a -> a.isJsonObject }?.asJsonObject }.orEmpty()
                val image = base.obj("url_cover") ?: album.obj("display_info")?.obj("url_cover")
                com.hypermusic.miuix.data.model.AlbumInfo(id = id, name = base.text("name").ifBlank { base.text("title") },
                    picUrl = QishuiCatalog.image(image), size = base.text("count_tracks").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                    publishTime = (base.text("release_date").toLongOrNull()?.coerceAtLeast(0) ?: 0) * 1000,
                    artist = artists.joinToString(" / ") { it.text("name") }, artistId = artists.firstOrNull()?.text("id")?.toLongOrNull() ?: 0)
            }
            check(albums.size == albumRows.size) { "汽水音乐收藏专辑格式暂不支持" }
            val more = data.get("has_more")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val next = data.text("next_cursor").takeIf { it.isNotBlank() }
            check(!more || (next != null && next != cursor && rows.size() > 0)) { "汽水音乐收藏专辑游标未前进" }
            return Page(albums, next, more)
        }
        internal fun containsTrack(rows: JsonArray, id: String): Boolean {
            var unrecognized = false
            for (row in rows) {
                val item = row.takeIf { it.isJsonObject }?.asJsonObject
                if (item == null) { unrecognized = true; continue }
                val track = QishuiCatalog.unwrapTrack(item)
                val trackId = track.obj("base_info")?.text("id").orEmpty().ifBlank { track.text("id") }
                    .ifBlank { item.text("media_id") }
                if (trackId == id) return true
                if (!trackId.matches(Regex("[0-9]{1,30}"))) unrecognized = true
            }
            check(!unrecognized) { "汽水音乐喜欢的歌曲格式暂不支持，无法确认收藏状态" }
            return false
        }
        internal fun parsePage(data: JsonObject, rows: JsonArray, cursor: String, owned: Boolean): Page<Playlist> {
            val playlistRows = if (owned) rows.toList() else rows.filter { row ->
                val item = row.takeIf { it.isJsonObject }?.asJsonObject ?: error("汽水音乐收藏条目格式暂不支持")
                check(item.obj("playlist") != null || item.obj("album") != null) { "汽水音乐收藏条目格式暂不支持" }
                item.obj("playlist") != null
            }
            val items = playlistRows.mapNotNull { row ->
                val obj = row.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val playlist = obj.obj("playlist") ?: obj
                val base = playlist.obj("base_info") ?: return@mapNotNull null
                val id = base.text("id").takeIf { it.matches(Regex("[0-9]{1,30}")) } ?: return@mapNotNull null
                Playlist(id, base.text("owner_id"), playlist, base.text("type") == "1", owned)
            }
            check(items.size == playlistRows.size) { "汽水音乐账号歌单格式暂不支持" }
            val more = data.get("has_more")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
            val next = data.text("next_cursor").takeIf { it.isNotBlank() }
            check(!more || (next != null && next != cursor && rows.size() > 0)) { "汽水音乐账号歌单游标未前进" }
            return Page(items, next, more)
        }
        private fun requireId(id: String) { require(id.matches(Regex("[0-9]{1,30}"))) { "无效的汽水音乐 ID" } }
        internal fun media(ids: List<String>): JsonArray {
            require(ids.isNotEmpty() && ids.size <= 100)
            return JsonArray().apply { ids.distinct().forEach { id -> requireId(id); add(JsonObject().apply { addProperty("id", id); addProperty("type", "track") }) } }
        }
        private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
