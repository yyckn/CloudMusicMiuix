package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.SearchCategory
import com.hypermusic.miuix.data.model.SearchEntity
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.ArtistInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 波点目录读取，返回应用已有的歌曲模型。 */
internal class BodianCatalog(private val client: BodianClient) {
    data class Page<T>(val items: List<T>, val nextOffset: Int, val hasMore: Boolean, val total: Int?)

    suspend fun searchSongs(query: String, offset: Int = 0, limit: Int = 50): Page<Track> = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 500)
        songPage("/api/search/music/list", "resultList", offset, limit,
            mapOf("keyword" to query.trim(), "correct" to "1"))
    }

    suspend fun searchEntities(query: String, category: SearchCategory, offset: Int = 0, limit: Int = 50): Page<SearchEntity> = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 500 && offset >= 0 && limit in 1..100)
        val type = when (category) { SearchCategory.PLAYLIST -> "playlist"; SearchCategory.ALBUM -> "album"; SearchCategory.ARTIST -> "artist"; else -> error("无效的波点音乐搜索类型") }
        val result = data(client.call("/api/search/$type/list", mapOf("keyword" to query.trim(), "correct" to "1",
            "pn" to (offset / limit).toString(), "rn" to limit.toString())))
        val raw = result.getAsJsonArray("resultList") ?: error("波点音乐搜索结果缺失")
        val selected = raw.drop(offset % limit)
        val items = selected.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject?.let { row -> parseSearchEntity(row, category) } }
        val total = result.text("total").toIntOrNull()?.takeIf { it >= 0 }
        val next = (offset / limit + 1) * limit
        val more = raw.size() > 0 && if (total != null) next < total else raw.size() == limit
        Page(items, if (more) next else offset + selected.size, more, total)
    }

    suspend fun playlistSongs(id: String, source: Int, offset: Int = 0, limit: Int = 50): Page<Track> = withContext(Dispatchers.IO) {
        songPage("/api/service/playlist/${mediaId(id)}/musicList", "list", offset, limit, mapOf("source" to source.toString()), firstPage = 1)
    }

    suspend fun albumSongs(id: String, offset: Int = 0, limit: Int = 50): Page<Track> = withContext(Dispatchers.IO) {
        songPage("/api/service/album/music/${mediaId(id)}", "resultList", offset, limit)
    }

    suspend fun artistSongs(id: String, offset: Int = 0, limit: Int = 50): Page<Track> = withContext(Dispatchers.IO) {
        songPage("/api/service/artist/music/${mediaId(id)}", "resultList", offset, limit)
    }

    suspend fun artistAlbums(id: String, offset: Int = 0, limit: Int = 50): Page<AlbumInfo> = withContext(Dispatchers.IO) {
        require(offset >= 0 && limit in 1..100)
        val result = data(client.call("/api/service/artist/album/${mediaId(id)}", mapOf("pn" to (offset / limit).toString(), "rn" to limit.toString())))
        val rows = result.getAsJsonArray("resultList") ?: error("波点音乐歌手专辑缺失")
        val selected = rows.drop(offset % limit)
        val items = selected.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject?.let { row -> runCatching { parseAlbum(row) }.getOrNull() } }
        val total = result.text("total").toIntOrNull()?.takeIf { it >= 0 }
        val next = (offset / limit + 1) * limit
        val more = rows.size() > 0 && if (total != null) next < total else rows.size() == limit
        Page(items, if (more) next else offset + selected.size, more, total)
    }

    suspend fun songDetail(id: String): JsonObject = withContext(Dispatchers.IO) {
        data(client.call("/api/service/music/info", mapOf("musicId" to mediaId(id))))
    }

    suspend fun playlistDetail(id: String, source: Int): JsonObject = withContext(Dispatchers.IO) {
        data(client.call("/api/service/playlist/info/${mediaId(id)}", mapOf("source" to source.toString())))
    }

    suspend fun albumDetail(id: String): JsonObject = withContext(Dispatchers.IO) {
        data(client.call("/api/service/album/${mediaId(id)}"))
    }

    suspend fun artistDetail(id: String): JsonObject = withContext(Dispatchers.IO) {
        data(client.call("/api/service/artist/${mediaId(id)}"))
    }

    private fun songPage(path: String, field: String, offset: Int, limit: Int, extra: Map<String, String> = emptyMap(), firstPage: Int = 0): Page<Track> {
        require(offset >= 0 && limit in 1..100)
        val params = extra + mapOf("pn" to (offset / limit + firstPage).toString(), "rn" to limit.toString())
        val result = data(client.call(path, params))
        val raw = result.getAsJsonArray(field) ?: error("波点音乐歌曲列表缺失")
        val selected = raw.drop(offset % limit)
        val tracks = selected.mapNotNull { runCatching { parseSong(it.asJsonObject) }.getOrNull() }
        check(selected.isEmpty() || tracks.isNotEmpty()) { "波点音乐歌曲列表格式暂不支持" }
        val total = result.get("total")?.takeIf { it.isJsonPrimitive }?.asString?.toIntOrNull()?.takeIf { it >= 0 }
        val next = ((offset / limit) + 1) * limit
        val more = raw.size() > 0 && if (total != null) next < total else raw.size() == limit
        return Page(tracks, if (more) next else offset + tracks.size, more, total)
    }

    companion object {
        internal fun parseArtist(data: JsonObject): ArtistInfo {
            val row = data.getAsJsonObject("artistInfo") ?: data
            val id = row.text("id").toLongOrNull()?.takeIf { it > 0 } ?: error("波点音乐歌手标识无效")
            return ArtistInfo(id = id, name = row.text("name"), picUrl = cover(row.text("pic")),
                albumSize = row.text("albumCnt").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                musicSize = row.text("musicCnt").toIntOrNull()?.coerceAtLeast(0) ?: 0, briefDesc = row.text("desc"))
        }
        internal fun parseAlbum(data: JsonObject): AlbumInfo {
            val row = data.getAsJsonObject("albumInfo") ?: data
            val id = row.text("albumId").ifBlank { row.text("id") }.toLongOrNull()?.takeIf { it > 0 } ?: error("波点音乐专辑标识无效")
            val date = runCatching { java.time.LocalDate.parse(row.text("showtime").take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)
            return AlbumInfo(id = id, name = row.text("name"), picUrl = cover(row.text("pic")),
                size = row.text("musicCount").toIntOrNull()?.coerceAtLeast(0) ?: 0, publishTime = date,
                artist = row.text("artist"), artistId = row.text("artistId").toLongOrNull() ?: 0)
        }
        internal fun parseSearchEntity(row: JsonObject, category: SearchCategory): SearchEntity? {
            val id = (when (category) { SearchCategory.ALBUM -> row.text("albumId"); SearchCategory.ARTIST -> row.text("artistId"); else -> row.text("id") })
                .ifBlank { row.text("id") }.takeIf { it.matches(Regex("[1-9][0-9]{0,19}")) } ?: return null
            val source = row.text("source").toIntOrNull() ?: 4
            if (category == SearchCategory.PLAYLIST && source !in 1..6) return null
            val cover = row.text("pic").replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kuwo\\.cn/)"), "https://")
            fun count(vararg keys: String) = keys.firstNotNullOfOrNull { row.text(it).toLongOrNull()?.takeIf { n -> n >= 0 } }
            return SearchEntity(id = if (category == SearchCategory.PLAYLIST) "bodian:$source:$id" else id,
                title = row.text("name"), imageUrl = cover, creator = row.text("creator_name").ifBlank { row.text("artist") },
                songCount = count("musicnum", "musicCount", "songNum"), playCount = count("playnum"),
                albumCount = count("albumNum"), date = row.text("showtime").take(10))
        }
        private fun mediaId(id: String): String = id.removePrefix("bodian_").also {
            require(it.matches(Regex("[0-9]{1,20}")) && it.any { ch -> ch != '0' }) { "无效的波点音乐 ID" }
        }
        internal fun data(json: JsonObject) = json.getAsJsonObject("data") ?: error("波点音乐响应缺少数据")
        private fun JsonObject.text(key: String) = get(key)?.takeUnless { it.isJsonNull }?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        private fun cover(url: String) = url.replace(Regex("^http://(?=[\\w-]+\\.kuwo\\.cn/)"), "https://")
        internal fun parseSong(item: JsonObject): Track {
            val id = mediaId(item.text("id"))
            val artists = item.getAsJsonArray("artists")?.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject }
                ?.filter { it.text("name").isNotBlank() }.orEmpty()
            val artistNames = artists.map { it.text("name") }.ifEmpty { listOf(item.text("artist")).filter { it.isNotBlank() } }
            val artistIds = artists.map { it.text("id") }.ifEmpty { listOf(item.text("artistId")) }
            val duration = item.text("duration").toDoubleOrNull()?.coerceAtLeast(0.0)?.times(1000)?.toLong() ?: 0L
            return Track(id = "bodian_$id", title = item.text("songName").ifBlank { item.text("name") },
                artist = artistNames.joinToString(" / "), artistId = artistIds.firstOrNull().orEmpty(),
                album = item.text("album"), albumId = item.text("albumId"), source = "bodian",
                sourceUrl = "", imgUrl = cover(item.text("albumPic")), duration = duration,
                artistNames = artistNames, artistIds = artistIds,
                isVip = item.get("payInfo")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("feeType")?.takeIf { it.isJsonObject }?.asJsonObject
                    ?.get("vip")?.takeIf { it.isJsonPrimitive }?.asString?.let { it == "1" || it.equals("true", true) } == true,
                bodianFreeSign = item.text("freeSign").ifBlank { item.text("fsig") })
        }
    }
}
