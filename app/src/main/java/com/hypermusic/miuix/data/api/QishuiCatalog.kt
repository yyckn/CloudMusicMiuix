package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.SearchEntity
import com.hypermusic.miuix.data.model.ArtistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.UUID

internal class QishuiCatalog(private val client: QishuiClient) {
    data class Page<T>(val items: List<T>, val nextOffset: Int, val hasMore: Boolean)
    data class PlaylistPage(val playlist: JsonObject?, val songs: Page<Track>)

    suspend fun searchSongs(query: String, offset: Int = 0, limit: Int = 20): Page<Track> = withContext(Dispatchers.IO) {
        search(query, "track", offset, limit) { parseTrack(it) }
    }

    suspend fun searchPlaylists(query: String, offset: Int = 0, limit: Int = 20): Page<JsonObject> = withContext(Dispatchers.IO) {
        search(query, "playlist", offset, limit) { it }
    }

    suspend fun searchArtists(query: String, offset: Int = 0, limit: Int = 20): Page<JsonObject> = withContext(Dispatchers.IO) {
        search(query, "artist", offset, limit) { it }
    }

    private suspend fun <T> search(query: String, type: String, offset: Int, limit: Int, parse: (JsonObject) -> T): Page<T> {
        require(query.isNotBlank() && query.length <= 500 && offset >= 0 && limit in 1..100)
        val searchId = UUID.randomUUID().toString()
        return readSearchPages(type, offset, limit, parse) { cursor ->
            val body = JsonObject().apply {
                addProperty("q", query.trim()); addProperty("cursor", cursor.toString()); addProperty("search_id", searchId)
                addProperty("from_search_id", searchId); addProperty("search_method", "input"); addProperty("search_type", type)
                addProperty("search_scene", "normal"); addProperty("scene_name", "search"); addProperty("ab_param", "")
                add("last_viewed_media", JsonObject())
            }
            client.read("/search/", body)
        }
    }

    suspend fun songDetail(id: String): JsonObject = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[0-9]{1,24}")))
        val body = JsonObject().apply {
            add("media_list", com.google.gson.JsonArray().apply { add(JsonObject().apply { addProperty("id", id); addProperty("type", "track") }) })
            add("card_context", JsonObject())
        }
        val entries = client.read("/media/mget/", body).getAsJsonArray("media_list") ?: error("汽水音乐歌曲信息缺失")
        entries.mapNotNull { if (it.isJsonObject) it.asJsonObject.getAsJsonObject("track_entity") else null }
            .firstOrNull { it.getAsJsonObject("base_info")?.text("id") == id } ?: error("汽水音乐未返回该歌曲")
    }

    /** 接口游标不是行号，按服务端游标向前遍历并检测循环。 */
    suspend fun playlistSongs(id: String, ownerId: String = "", offset: Int = 0, limit: Int = 50): PlaylistPage = withContext(Dispatchers.IO) {
        readPlaylistPages(id, ownerId, offset, limit) { body -> client.read("/playlist/detail/", body) }
    }

    suspend fun artistDetail(id: String): JsonObject = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[0-9]{1,24}")))
        client.read("/artist/detail/", JsonObject().apply { addProperty("artist_id", id) }).getAsJsonObject("artist_info")
            ?: error("汽水音乐未返回歌手资料")
    }

    suspend fun artistSongs(id: String, offset: Int = 0, limit: Int = 50): Page<Track> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[0-9]{1,24}")) && offset >= 0 && limit in 1..100)
        val body = JsonObject().apply { addProperty("artist_id", id); addProperty("count", 50); addProperty("cursor", "0") }
        val tracks = client.read("/artist/tracks/", body).getAsJsonArray("tracks") ?: error("汽水音乐歌手歌曲缺失")
        val selected = tracks.drop(offset).take(limit).map { parseTrack(unwrapTrack(it.asJsonObject)) }
        Page(selected, offset + selected.size, offset + selected.size < tracks.size())
    }

    companion object {
    internal suspend fun <T> readSearchPages(type: String, offset: Int, limit: Int, parse: (JsonObject) -> T,
        fetch: suspend (Int) -> JsonObject): Page<T> {
        require(type in setOf("track", "playlist", "artist") && offset >= 0 && limit in 1..100)
        var cursor = 0
        var consumed = 0
        val seen = mutableSetOf<Int>()
        val items = mutableListOf<T>()
        repeat(500) {
            currentCoroutineContext().ensureActive()
            check(seen.add(cursor)) { "汽水音乐搜索游标重复" }
            val data = fetch(cursor)
            val groups = data.getAsJsonArray("result_groups") ?: error("汽水音乐搜索结果格式无效")
            val group = groups.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
                .firstOrNull { it.text("id") == "${type}s" } ?: return Page(items, consumed, false)
            val batch = group.getAsJsonArray("data") ?: error("汽水音乐搜索列表缺失")
            check(batch.size() == 0 || batch.any { row ->
                row.isJsonObject && row.asJsonObject.getAsJsonObject("entity")?.getAsJsonObject(type) != null
            }) { "汽水音乐搜索结果格式不受支持" }
            val remoteMore = group.get("has_more")?.asBoolean == true
            for ((index, row) in batch.withIndex()) {
                if (consumed++ < offset) continue
                val entity = row.takeIf { it.isJsonObject }?.asJsonObject?.getAsJsonObject("entity")?.getAsJsonObject(type)
                if (entity != null) items += parse(entity)
                if (items.size == limit) return Page(items, consumed, index + 1 < batch.size() || remoteMore)
            }
            if (!remoteMore) return Page(items, consumed, false)
            val next = group.text("next_cursor").toIntOrNull() ?: error("汽水音乐搜索游标无效")
            check(next > cursor && batch.size() > 0) { "汽水音乐搜索游标未前进" }
            cursor = next
        }
        error("汽水音乐搜索分页次数过多")
    }


    internal suspend fun readPlaylistPages(id: String, ownerId: String, offset: Int, limit: Int,
        fetch: suspend (JsonObject) -> JsonObject): PlaylistPage {
        require(id.matches(Regex("[0-9]{1,24}")) && offset >= 0 && limit in 1..20_000)
        var cursor = "0"
        var consumed = 0
        var playlist: JsonObject? = null
        val seen = mutableSetOf<String>()
        val tracks = mutableListOf<Track>()
        var hasMore = false
        var pages = 0
        while (tracks.size < limit && pages++ < 500) {
            currentCoroutineContext().ensureActive()
            check(seen.add(cursor)) { "汽水音乐歌单游标重复" }
            val body = JsonObject().apply {
                addProperty("playlist_id", id); addProperty("owner_id", ownerId)
                addProperty("count", 100); addProperty("cursor", cursor); add("type", com.google.gson.JsonNull.INSTANCE)
            }
            val data = fetch(body)
            if (playlist == null) playlist = data.getAsJsonObject("playlist")
            val batch = data.getAsJsonArray("media_entities") ?: error("汽水音乐歌单缺少歌曲列表")
            val skip = (offset - consumed).coerceAtLeast(0)
            val take = (limit - tracks.size).coerceAtMost((batch.size() - skip).coerceAtLeast(0))
            tracks += batch.drop(skip).take(take).map { unwrapTrack(it.asJsonObject) }.map(::parseTrack)
            consumed += batch.size()
            val remoteMore = data.get("has_more")?.asBoolean == true
            val next = data.text("next_cursor")
            if (remoteMore) check(next.isNotBlank() && next !in seen && batch.size() > 0) { "汽水音乐歌单游标未前进" }
            hasMore = consumed > offset + tracks.size || remoteMore
            if (!remoteMore || tracks.size == limit) break
            cursor = next
        }
        check(pages < 500 || tracks.size == limit || !hasMore) { "汽水音乐歌单分页次数过多" }
        return PlaylistPage(playlist, Page(tracks, offset + tracks.size, hasMore && tracks.isNotEmpty()))
    }

        internal fun parseArtist(item: JsonObject): ArtistInfo {
            val base = item.getAsJsonObject("base_info") ?: error("汽水音乐歌手资料缺失")
            val id = base.text("id")
            require(id.matches(Regex("[0-9]{1,24}"))) { "汽水音乐歌手标识无效" }
            val cover = image(base.getAsJsonObject("url_avatar"))
            val counts = item.getAsJsonObject("resource_cnt")
            return ArtistInfo(id = id.toLongOrNull() ?: 0L, name = base.text("name").ifBlank { base.text("title") },
                picUrl = cover, briefDesc = base.text("desc").ifBlank { base.text("intro") },
                musicSize = (counts?.text("track_cnt")?.toIntOrNull() ?: base.text("count_tracks").toIntOrNull())?.coerceAtLeast(0) ?: 0,
                albumSize = (counts?.text("album_cnt")?.toIntOrNull() ?: base.text("count_albums").toIntOrNull())?.coerceAtLeast(0) ?: 0)
        }
        internal fun artistEntity(item: JsonObject): SearchEntity? {
            val base = item.getAsJsonObject("base_info") ?: return null
            val id = base.text("id").takeIf { it.matches(Regex("[0-9]{1,24}")) } ?: return null
            return SearchEntity(id = id, title = base.text("name").ifBlank { base.text("full_display_name") },
                imageUrl = image(base.getAsJsonObject("url_avatar")),
                songCount = base.text("count_tracks").toLongOrNull()?.takeIf { it >= 0 },
                albumCount = base.text("count_albums").toLongOrNull()?.takeIf { it >= 0 })
        }
        internal fun playlistEntity(item: JsonObject): SearchEntity? {
            val base = item.getAsJsonObject("base_info") ?: return null
            val id = base.text("id").takeIf { it.matches(Regex("[0-9]{1,24}")) } ?: return null
            val owner = item.getAsJsonObject("relation_info")?.getAsJsonObject("owner")
            val ownerId = base.text("owner_id").ifBlank { owner?.text("id").orEmpty() }
            if (ownerId.length > 512 || ownerId.any { it == ':' || it.isISOControl() }) return null
            val display = item.getAsJsonObject("display_info")
            val cover = image(display?.getAsJsonObject("url_cover") ?: display?.getAsJsonObject("cover_url"))
            val count = item.getAsJsonObject("resource_cnt")?.text("track_cnt")?.toLongOrNull()?.takeIf { it >= 0 }
            return SearchEntity(id = "qishui:$id:$ownerId", title = base.text("title").ifBlank { base.text("name") },
                imageUrl = cover, creator = owner?.text("nickname").orEmpty(), songCount = count)
        }
        internal fun unwrapTrack(item: JsonObject): JsonObject = item.getAsJsonObject("track_entity")
            ?: item.getAsJsonObject("entity")?.getAsJsonObject("media")?.getAsJsonObject("track_entity")
            ?: item.getAsJsonObject("entity")?.getAsJsonObject("track") ?: item.getAsJsonObject("track") ?: item
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        internal fun image(item: JsonObject?): String {
            if (item == null) return ""
            val url = item.getAsJsonArray("urls")?.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
                ?.firstOrNull { it.startsWith("https://") }.orEmpty()
            if (url.isEmpty() || !url.endsWith('/')) return url
            val uri = item.text("uri")
            val template = item.text("template_prefix")
            return url + uri + if (uri.isNotBlank() && template.isNotBlank()) "~$template-image.image" else ""
        }
        internal fun parseTrack(item: JsonObject): Track {
            val base = item.getAsJsonObject("base_info") ?: error("汽水音乐歌曲信息缺失")
            val id = base.text("id").ifBlank { item.text("id") }
            require(id.matches(Regex("[0-9]{1,24}")))
            val related = item.getAsJsonObject("related_info") ?: JsonObject()
            val album = related.getAsJsonObject("album_link") ?: JsonObject()
            val artists = related.getAsJsonArray("artist_links")?.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }.orEmpty()
                .ifEmpty { base.getAsJsonObject("song_maker_team")?.getAsJsonArray("composers")?.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }.orEmpty() }
                .filter { it.text("name").isNotBlank() }
            return Track(id = "qishui_$id", title = base.text("name"), artist = artists.joinToString(" / ") { it.text("name") },
                artistId = artists.firstOrNull()?.text("id").orEmpty(), album = album.text("name"), albumId = album.text("id"),
                source = "qishui", sourceUrl = "https://www.qishui.com/track/$id", imgUrl = image(item.getAsJsonObject("display_info")?.getAsJsonObject("cover_url")),
                duration = base.text("duration_ms").toLongOrNull()?.coerceAtLeast(0) ?: 0L,
                artistNames = artists.map { it.text("name") }, artistIds = artists.map { it.text("id") })
        }
    }
}
