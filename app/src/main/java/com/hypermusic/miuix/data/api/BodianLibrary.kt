package com.hypermusic.miuix.data.api

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.Gson
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 波点收藏目录；歌单来源标识必须跟随条目保留，供详情接口使用。 */
internal class BodianLibrary(private val client: BodianClient, private val auth: BodianAuth, private val catalog: BodianCatalog) {
    data class Collection(val id: String, val title: String, val cover: String, val count: Int, val source: Int, val isAlbum: Boolean)
    data class CollectionsPage(val items: List<Collection>, val nextOffset: Int, val hasMore: Boolean)
    data class Playlists(val liked: Collection?, val created: List<Collection>, val collected: CollectionsPage)

    private data class Account(val uid: String, val revision: Long)
    private fun account(): Account {
        val revision = auth.sessionRevision()
        val uid = auth.currentAccount()?.uid ?: error("请先登录波点音乐")
        check(auth.sessionRevision() == revision) { "波点音乐账号已切换，请重试" }
        return Account(uid, revision)
    }
    private fun userParams(uid: String) = mapOf("userId" to uid, "fromUid" to uid)
    private fun sameAccount(account: Account) = check(auth.sessionRevision() == account.revision && auth.currentAccount()?.uid == account.uid) { "波点音乐账号已切换，请重试" }

    suspend fun playlists(offset: Int = 0, limit: Int = 50): Playlists = withContext(Dispatchers.IO) {
        val account = account()
        val uid = account.uid
        val liked = client.call("/api/service/playlist/fond", userParams(uid)).get("data")?.asObjectOrNull()?.let(::parseCollection)
        val createdData = client.call("/api/service/playlist/userCreate", userParams(uid)).get("data")?.asObjectOrNull()
        val created = createdData?.getAsJsonArray("playLists")?.mapNotNull { it.asObjectOrNull()?.let(::parseCollection) }.orEmpty()
        val collected = collectedPage(uid, offset, limit, albums = false)
        sameAccount(account)
        Playlists(liked?.copy(source = 5), created.map { it.copy(source = 5) }, collected)
    }

    suspend fun albums(offset: Int = 0, limit: Int = 50): CollectionsPage = withContext(Dispatchers.IO) {
        val account = account()
        collectedPage(account.uid, offset, limit, albums = true).also { sameAccount(account) }
    }

    suspend fun collectedPlaylists(offset: Int = 0, limit: Int = 50): CollectionsPage = withContext(Dispatchers.IO) {
        val account = account()
        collectedPage(account.uid, offset, limit, albums = false).also { sameAccount(account) }
    }

    suspend fun isCollected(id: String, album: Boolean): Boolean = withContext(Dispatchers.IO) {
        val expectedId = numericId(id).toString()
        val account = account()
        if (!album) {
            val own = playlists()
            sameAccount(account)
            if ((listOfNotNull(own.liked) + own.created).any { it.id == expectedId }) return@withContext true
        }
        var offset = 0
        repeat(200) {
            val page = collectedPage(account.uid, offset, 100, albums = album)
            sameAccount(account)
            if (page.items.any { it.id == expectedId }) return@withContext true
            if (!page.hasMore) return@withContext false
            check(page.nextOffset > offset) { "波点音乐收藏分页异常" }
            offset = page.nextOffset
        }
        error("波点音乐收藏列表过长")
    }

    suspend fun collect(id: String, source: Int, expectedRevision: Long? = null, favorite: Boolean = true) = withContext(Dispatchers.IO) {
        require(source in 1..6) { "波点音乐收藏来源无效" }
        val account = account()
        check(expectedRevision == null || account.revision == expectedRevision) { "波点音乐账号已切换，请重试" }
        val uid = account.uid.toLongOrNull()?.takeIf { it > 0 } ?: error("波点音乐账号标识无效")
        if (!favorite && source != 6) {
            val own = playlists()
            sameAccount(account)
            check((listOfNotNull(own.liked) + own.created).none { it.id == numericId(id).toString() }) { "不能取消收藏自己创建的歌单" }
        }
        val body = Gson().toJson(mapOf("source" to source, "sourceId" to listOf(numericId(id)), "op" to if (favorite) 1 else 2, "uid" to uid))
        sameAccount(account)
        client.call("/api/service/collect", signed = true, method = "POST", body = body)
        sameAccount(account)
        if (!favorite) {
            check(!isCollected(id, album = source == 6)) { "波点音乐未确认取消收藏，请刷新后重试" }
            sameAccount(account)
        }
    }

    suspend fun likedSongs(offset: Int = 0, limit: Int = 50): BodianCatalog.Page<Track> = withContext(Dispatchers.IO) {
        val account = account()
        val uid = account.uid
        val liked = client.call("/api/service/playlist/fond", userParams(uid)).get("data")?.asObjectOrNull()?.let(::parseCollection)
        sameAccount(account)
        if (liked == null) return@withContext BodianCatalog.Page(emptyList(), offset, false, 0)
        catalog.playlistSongs(liked.id, 5, offset, limit).also { sameAccount(account) }
    }

    suspend fun setSongLiked(songId: String, liked: Boolean, expectedUserId: String? = null) = withContext(Dispatchers.IO) {
        val account = account()
        check(expectedUserId == null || account.uid == expectedUserId) { "波点音乐账号已切换，请重试" }
        val uid = account.uid
        val likedList = client.call("/api/service/playlist/fond", mapOf("userId" to uid)).get("data")?.asObjectOrNull()?.let(::parseCollection)
            ?: error("波点音乐未返回喜欢的歌单")
        updateTracks(account, likedList.id, listOf(songId), remove = !liked)
    }

    suspend fun updateOwnedPlaylist(playlistId: String, songIds: List<String>, remove: Boolean, expectedRevision: Long? = null) = withContext(Dispatchers.IO) {
        val account = account()
        check(expectedRevision == null || account.revision == expectedRevision) { "波点音乐账号已切换，请重新选择歌单" }
        val uid = account.uid
        val id = numericId(playlistId)
        val data = client.call("/api/service/playlist/userCreate", mapOf("userId" to uid)).get("data")?.asObjectOrNull()
        val owns = data?.getAsJsonArray("playLists")?.any { it.asObjectOrNull()?.value("id") == id.toString() } == true
        if (!owns) {
            val liked = client.call("/api/service/playlist/fond", mapOf("userId" to uid)).get("data")?.asObjectOrNull()?.value("id")
            check(liked == id.toString()) { "只能修改自己的歌单" }
        }
        updateTracks(account, id.toString(), songIds, remove)
    }

    private fun updateTracks(account: Account, playlistId: String, songIds: List<String>, remove: Boolean) {
        val songs = songIds.map(::numericId).distinct()
        require(songs.size in 1..100)
        sameAccount(account)
        val body = Gson().toJson(mapOf("playListId" to numericId(playlistId), "musicIdList" to songs))
        client.call("/api/service/playlist/music" + if (remove) "/delete" else "", signed = true, method = "POST", body = body)
        sameAccount(account)
    }

    private fun collectedPage(uid: String, offset: Int, limit: Int, albums: Boolean): CollectionsPage {
        require(offset >= 0 && limit in 1..100)
        val parameters = userParams(uid) + mapOf("pn" to (offset / limit + 1).toString(), "rn" to limit.toString())
        val value = client.call("/api/service/collect/4/list", parameters).get("data")
        return parseCollectionsPage(value, offset, limit, albums)
    }

    companion object {
        internal fun parseCollectionsPage(value: JsonElement?, offset: Int, limit: Int, albums: Boolean): CollectionsPage {
            require(offset >= 0 && limit in 1..100)
            if (value == null || value.isJsonNull || (value.isJsonArray && value.asJsonArray.isEmpty)) return CollectionsPage(emptyList(), offset, false)
            val data = value.asObjectOrNull() ?: error("波点音乐收藏响应格式无效")
            if (data.size() == 0) return CollectionsPage(emptyList(), offset, false)
            val list = data.getAsJsonArray("playLists") ?: error("波点音乐收藏列表缺失")
            val rows = list.drop(offset % limit)
            val parsed = rows.mapNotNull { it.asObjectOrNull()?.let(::parseCollection) }
            check(parsed.size == rows.size) { "波点音乐收藏列表格式暂不支持，无法确认收藏状态" }
            val items = parsed.filter { it.isAlbum == albums }
            val next = (offset / limit + 1) * limit
            val total = data.value("total").toIntOrNull()?.takeIf { it >= 0 }
            val more = list.size() > 0 && if (total != null) next < total else list.size() == limit
            return CollectionsPage(items, if (more) next else maxOf(offset + list.size(), total?.coerceAtMost(next) ?: 0), more)
        }
        private fun numericId(raw: String): Long {
            val id = raw.removePrefix("bodian_")
            require(id.matches(Regex("[1-9][0-9]{0,15}"))) { "无效的波点音乐 ID" }
            return requireNotNull(id.toLongOrNull()?.takeIf { it <= 9_007_199_254_740_991L }) { "无效的波点音乐 ID" }
        }
        private fun JsonElement.asObjectOrNull() = if (isJsonObject) asJsonObject else null
        private fun JsonObject.value(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        internal fun parseCollection(data: JsonObject): Collection? {
            val source = data.value("sourceType").toIntOrNull() ?: 4
            val isAlbum = source == 6
            val id = (if (isAlbum) data.value("albumId").ifBlank { data.value("id") } else data.value("id"))
            if (!id.matches(Regex("[1-9][0-9]{0,19}"))) return null
            val cover = data.value("pic").replace(Regex("^http://(?=[\\w-]+\\.kuwo\\.cn/)"), "https://")
            return Collection(id, data.value("name"), cover,
                data.value("musicCount").ifBlank { data.value("musicCnt") }.toIntOrNull()?.coerceAtLeast(0) ?: 0, source, isAlbum)
        }
    }
}
