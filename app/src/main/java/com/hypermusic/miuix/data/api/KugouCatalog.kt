package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.TrackPlatformData
import com.hypermusic.miuix.data.model.SearchCategory
import com.hypermusic.miuix.data.model.SearchEntity
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.ArtistInfo
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class KugouCatalog(private val client: KugouClient) {
    data class Page<T>(val items: List<T>, val nextOffset: Int, val hasMore: Boolean, val total: Int)
    data class PlaylistPage(val info: JsonObject, val songs: Page<Track>)

    suspend fun searchSongs(query: String, offset: Int = 0, limit: Int = 30): Page<Track> = withContext(Dispatchers.IO) {
        search("song", query, offset, limit) { parseTrack(it) }
    }
    suspend fun searchEntities(type: String, query: String, offset: Int = 0, limit: Int = 30): Page<JsonObject> = withContext(Dispatchers.IO) {
        require(type in setOf("album", "author", "special"))
        search(type, query, offset, limit) { it }
    }

    suspend fun playlist(id: String, offset: Int = 0, limit: Int = 50): PlaylistPage = withContext(Dispatchers.IO) {
        parsePlaylist(client.playlistRequest(id, offset, limit), offset)
    }

    suspend fun album(id: Long): Pair<AlbumInfo, List<Track>> = withContext(Dispatchers.IO) {
        require(id > 0)
        val metadata = client.catalogRequest("/kmr/v2/albums", JsonObject().apply {
            add("data", JsonArray().apply { add(JsonObject().apply { addProperty("album_id", id.toString()) }) })
            addProperty("is_buy", 0)
            addProperty("fields", "album_id,album_name,publish_date,sizable_cover,intro,language,is_publish,heat,type,quality,authors,exclusive,author_name,trans_param")
        }, 255)
        val info = metadata.getAsJsonArray("data")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐专辑信息缺失")
        val tracks = mutableListOf<Track>()
        var page = 1; var total: Int
        do {
            val response = client.catalogRequest("/v1/album_audio/lite", JsonObject().apply {
                addProperty("album_id", id.toString()); addProperty("is_buy", ""); addProperty("page", page); addProperty("pagesize", 30)
            }, 255).getAsJsonObject("data") ?: error("酷狗音乐专辑歌曲缺失")
            val rows = response.getAsJsonArray("songs") ?: error("酷狗音乐专辑歌曲列表缺失")
            total = response.text("total").toIntOrNull()?.takeIf { it >= 0 } ?: error("酷狗音乐专辑歌曲数量缺失")
            tracks += rows.map { parseAlbumTrack(it.asJsonObject) }
            check(rows.size() > 0 || tracks.size >= total) { "酷狗音乐专辑分页未前进" }
            page++
        } while (tracks.size < total && page <= 200)
        check(tracks.size >= total) { "酷狗音乐专辑过长，未能完整加载" }
        parseAlbum(info, total) to tracks
    }

    suspend fun artist(id: String): ArtistInfo = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[1-9][0-9]{0,18}")))
        val data = client.catalogRequest("/kmr/v3/author", JsonObject().apply { addProperty("author_id", id) }, 36)
            .getAsJsonObject("data") ?: error("酷狗音乐歌手资料缺失")
        parseArtist(data)
    }

    suspend fun artistSongs(id: String, offset: Int = 0, limit: Int = 50): List<Track> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[1-9][0-9]{0,18}")) && offset >= 0 && limit in 1..100 && offset % limit == 0)
        val response = client.catalogRequest("/kmr/v1/audio_group/author", JsonObject().apply {
            addProperty("author_id", id); addProperty("page", offset / limit + 1); addProperty("pagesize", limit)
            addProperty("sort", 1); addProperty("area_code", "all")
        }, 220)
        val rows = response.getAsJsonArray("data") ?: error("酷狗音乐歌手歌曲缺失")
        rows.map { parseTrack(it.asJsonObject) }
    }

    suspend fun artistAlbums(id: String, offset: Int = 0, limit: Int = 50): List<AlbumInfo> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[1-9][0-9]{0,18}")) && offset >= 0 && limit in 1..100 && offset % limit == 0)
        val response = client.catalogRequest("/kmr/v1/author/albums", JsonObject().apply {
            addProperty("author_id", id); addProperty("page", offset / limit + 1); addProperty("pagesize", limit)
            addProperty("sort", 1); addProperty("category", 1); addProperty("area_code", "all")
        }, 36)
        val rows = response.getAsJsonArray("data") ?: error("酷狗音乐歌手专辑缺失")
        rows.map { parseAlbum(it.asJsonObject, 0) }
    }

    private fun <T> search(type: String, query: String, offset: Int, limit: Int, parse: (JsonObject) -> T): Page<T> {
        require(offset >= 0 && limit in 1..100)
        val items = mutableListOf<T>()
        var index = offset
        var total = 0
        while (items.size < limit) {
            val page = index / 100 + 1
            val data = client.searchRequest(type, query, page, 100)
            val rows = data.get("lists")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("酷狗音乐搜索列表缺失")
            total = data.text("total").toIntOrNull()?.coerceAtLeast(0) ?: error("酷狗音乐搜索数量缺失")
            val selected = rows.drop(index % 100).take(limit - items.size)
            selected.forEach { row -> items += parse(row.asJsonObject) }
            index += selected.size
            if (selected.isEmpty() || rows.size() < 100 || index >= total) break
        }
        return Page(items, index, index < total && items.isNotEmpty(), total)
    }

    companion object {
        internal fun parseArtist(row: JsonObject): ArtistInfo {
            val id = row.text("author_id").toLongOrNull()?.takeIf { it > 0 } ?: error("酷狗音乐歌手标识无效")
            val avatar = row.text("sizable_avatar").replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            return ArtistInfo(id = id, name = row.text("author_name"), picUrl = avatar,
                albumSize = row.text("album_count").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                musicSize = row.text("song_count").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                briefDesc = row.text("long_intro").ifBlank { row.text("intro") })
        }
        internal fun parseAlbum(row: JsonObject, count: Int): AlbumInfo {
            val id = row.text("album_id").toLongOrNull()?.takeIf { it > 0 } ?: error("酷狗音乐专辑标识无效")
            val date = runCatching { java.time.LocalDate.parse(row.text("publish_date").take(10)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli() }.getOrDefault(0L)
            val image = row.text("sizable_cover").ifBlank { row.text("cover") }.replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            val author = row.getAsJsonArray("authors")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject
            val authorBase = author?.obj("base") ?: author
            val artistId = row.text("author_id").ifBlank { authorBase?.text("author_id").orEmpty() }
                .ifBlank { authorBase?.text("id").orEmpty() }.toLongOrNull() ?: 0
            return AlbumInfo(id = id, name = row.text("album_name"), picUrl = image, size = count, publishTime = date,
                artist = row.text("author_name"), artistId = artistId)
        }
        internal fun parseAlbumTrack(row: JsonObject): Track {
            val base = row.obj("base") ?: error("酷狗音乐专辑歌曲信息缺失")
            val audio = row.obj("audio_info") ?: JsonObject()
            val album = row.obj("album_info")
            val singers = row.getAsJsonArray("authors")?.mapNotNull { it.takeIf { item -> item.isJsonObject }?.asJsonObject }
                ?.filter { it.text("author_name").isNotBlank() }.orEmpty()
            val hash = audio.text("hash").uppercase()
            val audioId = base.text("album_audio_id").takeIf { it.matches(Regex("[1-9][0-9]{0,19}")) }.orEmpty()
            require(hash.matches(Regex("[A-F0-9]{32}")) || audioId.matches(Regex("[1-9][0-9]{0,19}"))) { "酷狗音乐音频标识无效" }
            val cover = album?.text("cover").orEmpty().replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            return Track(id = "kugou_" + audioId.ifBlank { hash }, title = base.text("audio_name"), artist = base.text("author_name"),
                artistId = singers.firstOrNull()?.text("author_id").orEmpty().ifBlank { base.text("author_id") }, artistNames = singers.map { it.text("author_name") },
                artistIds = singers.map { it.text("author_id") }, album = album?.text("album_name").orEmpty(), albumId = base.text("album_id"),
                source = "kugou", sourceUrl = "", imgUrl = cover, duration = audio.text("duration").toLongOrNull()?.coerceAtLeast(0) ?: 0,
                platformData = TrackPlatformData(kugouHash = hash, kugouAlbumAudioId = audioId))
        }
        internal fun parseSearchEntity(row: JsonObject, category: SearchCategory): SearchEntity? {
            fun first(vararg names: String) = names.firstNotNullOfOrNull { row.text(it).takeIf(String::isNotBlank) }.orEmpty()
            fun number(vararg names: String) = first(*names).toLongOrNull()?.takeIf { it >= 0 }
            val id = when (category) {
                SearchCategory.PLAYLIST -> first("gid", "global_collection_id")
                SearchCategory.ALBUM -> first("albumid", "album_id")
                SearchCategory.ARTIST -> first("AuthorId", "author_id")
                else -> ""
            }.takeIf(String::isNotBlank) ?: return null
            val image = first("img", "Avatar", "cover").replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            return SearchEntity(id = id, title = when (category) {
                SearchCategory.PLAYLIST -> first("specialname", "name")
                SearchCategory.ALBUM -> first("albumname", "title")
                SearchCategory.ARTIST -> first("AuthorName", "name")
                else -> ""
            }, imageUrl = image, creator = first("nickname", "singer"),
                songCount = number("song_count", "songcount", "AudioCount"), playCount = number("total_play_count", "play_count"),
                albumCount = number("AlbumCount"), date = first("publish_time").take(10))
        }
        internal fun parseTrack(row: JsonObject): Track {
            val hash = row.text("FileHash").ifBlank { row.text("hash") }.uppercase()
            val audioId = listOf("MixSongID", "mixsongid", "add_mixsongid", "album_audio_id")
                .firstNotNullOfOrNull { row.text(it).takeIf { id -> id.matches(Regex("[1-9][0-9]{0,19}")) } }.orEmpty()
            require(hash.matches(Regex("[A-F0-9]{32}")) || audioId.matches(Regex("[1-9][0-9]{0,19}"))) { "酷狗音乐歌曲标识无效" }
            val singers = (row.get("Singers") ?: row.get("singerinfo"))?.takeIf { it.isJsonArray }?.asJsonArray?.mapNotNull {
                it.takeIf { element -> element.isJsonObject }?.asJsonObject
            }.orEmpty()
            val namedSingers = singers.filter { it.text("name").isNotBlank() }
            val names = namedSingers.map { it.text("name") }
            val ids = namedSingers.map { it.text("id") }
            val artist = row.text("SingerName").ifBlank { row.text("author_name") }.ifBlank { names.joinToString(" / ") }
            val title = row.text("OriSongName").ifBlank { row.text("audio_name") }.ifBlank { row.text("FileName").ifBlank { row.text("name") }.removePrefix("$artist - ") }
            val cover = row.text("Image").ifBlank { row.text("cover") }.ifBlank { row.obj("trans_param")?.text("union_cover").orEmpty() }
                .replace("{size}", "400").replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            val albumId = row.text("AlbumID").ifBlank { row.text("album_id") }.ifBlank { row.obj("albuminfo")?.text("id").orEmpty() }
            val duration = row.text("timelen").ifBlank { row.text("timelength") }.toLongOrNull() ?: (row.text("Duration").toLongOrNull() ?: 0) * 1000
            val payType = row.text("PayType").ifBlank { row.text("pay_type") }.toIntOrNull() ?: row.get("download")?.takeIf { it.isJsonArray }?.asJsonArray
                ?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.text("pay_type")?.toIntOrNull() ?: 0
            return Track(id = "kugou_" + audioId.ifBlank { hash }, title = title, artist = artist,
                artistId = ids.firstOrNull().orEmpty().ifBlank { row.text("author_id").ifBlank { row.text("SingerId") } }, album = row.text("AlbumName").ifBlank { row.text("album_name") }.ifBlank { row.obj("albuminfo")?.text("name").orEmpty() }, albumId = albumId,
                source = "kugou", sourceUrl = "", imgUrl = cover,
                duration = duration.coerceAtLeast(0), isVip = payType > 0,
                artistNames = names, artistIds = ids, platformData = TrackPlatformData(kugouHash = hash, kugouAlbumAudioId = audioId))
        }
        internal fun parsePlaylist(data: JsonObject, offset: Int): PlaylistPage {
            val info = data.obj("list_info") ?: error("酷狗音乐歌单信息缺失")
            val rows = data.get("songs")?.takeIf { it.isJsonArray }?.asJsonArray ?: error("酷狗音乐歌单歌曲缺失")
            val total = data.text("count").toIntOrNull()?.coerceAtLeast(0) ?: error("酷狗音乐歌单数量缺失")
            val tracks = rows.map { parseTrack(it.asJsonObject) }
            val next = offset + rows.size()
            return PlaylistPage(info, Page(tracks, next, next < total && rows.size() > 0, total))
        }
        internal fun playlistInfo(id: String, page: PlaylistPage): com.hypermusic.miuix.data.model.Playlist {
            val info = page.info
            fun first(vararg keys: String) = keys.firstNotNullOfOrNull { info.text(it).takeIf(String::isNotBlank) }.orEmpty()
            val cover = first("pic").replace("{size}", "400")
                .replace(Regex("^http://(?=[a-zA-Z0-9.-]+\\.kugou\\.com/)"), "https://")
            return com.hypermusic.miuix.data.model.Playlist(id, first("name"), cover, "", page.songs.total, first("intro"),
                kugouCreatorId = first("create_userid", "list_create_userid", "userid"),
                kugouOriginalListId = first("create_listid", "list_create_listid", "listid"),
                kugouCreatorGid = first("gid", "list_create_gid", "global_collection_id"))
        }
        private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
