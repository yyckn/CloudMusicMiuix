package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.SearchEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.StringReader
import java.util.concurrent.TimeUnit

/** 汽水公开专辑分享页；不使用其他应用的登录凭据。 */
internal object QishuiShareAlbum {
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()

    suspend fun artistAlbums(id: String): List<AlbumInfo> = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[0-9]{1,24}")))
        val url = "https://www.qishui.com/share/artist".toHttpUrl().newBuilder().addQueryParameter("artist_id", id).build()
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36").build()
        val html = http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "汽水音乐歌手专辑加载失败（HTTP ${response.code}）" }
            val body = response.body ?: error("汽水音乐歌手响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "汽水音乐歌手响应过大" }
            source.readUtf8()
        }
        parseArtistAlbums(html, id)
    }

    internal fun parseArtistAlbums(html: String, artistId: String): List<AlbumInfo> {
        val page = routerData(html).obj("loaderData")?.obj("artist_page") ?: error("汽水音乐歌手数据缺失")
        check(page.obj("artistInfo")?.text("id") == artistId) { "汽水音乐歌手标识不匹配" }
        return page.getAsJsonArray("albumList")?.mapNotNull { value ->
            val row = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = row.text("id").toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
            val artists = row.getAsJsonArray("artists")?.mapNotNull { it.takeIf { a -> a.isJsonObject }?.asJsonObject }.orEmpty()
            AlbumInfo(id = id, name = row.text("name"), picUrl = QishuiCatalog.image(row.obj("url_cover")),
                size = row.text("count_tracks").toIntOrNull()?.coerceAtLeast(0) ?: 0,
                publishTime = (row.text("release_date").toLongOrNull()?.coerceAtLeast(0) ?: 0) * 1000,
                artist = artists.joinToString(" / ") { it.text("name") }, artistId = artists.firstOrNull()?.text("id")?.toLongOrNull() ?: 0)
        }.orEmpty()
    }

    suspend fun search(query: String, offset: Int, limit: Int = 20): QishuiCatalog.Page<SearchEntity> = withContext(Dispatchers.IO) {
        require(query.isNotBlank() && query.length <= 500 && offset >= 0 && limit in 1..100)
        val url = "https://api.qishui.com/luna/search/album".toHttpUrl().newBuilder()
            .addQueryParameter("q", query.trim()).addQueryParameter("cursor", offset.toString())
            .addQueryParameter("count", limit.toString()).addQueryParameter("aid", "386088").build()
        val request = Request.Builder().url(url).header("User-Agent", "com.luna.music/100198030 (Linux; U; Android 15; zh_CN; tt-ok/3.12.13.19)").build()
        val body = http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "汽水音乐专辑搜索失败（HTTP ${response.code}）" }
            val source = response.body?.source() ?: error("汽水音乐搜索响应为空")
            check(!source.request(8L * 1024 * 1024 + 1)) { "汽水音乐搜索响应过大" }
            JsonParser.parseString(source.readUtf8()).asJsonObject
        }
        parseSearch(body, offset)
    }

    internal fun parseSearch(root: JsonObject, offset: Int): QishuiCatalog.Page<SearchEntity> {
        val status = root.text("status_code").ifBlank { root.text("err_no") }.toIntOrNull() ?: 0
        check(status == 0) { "汽水音乐专辑搜索失败（代码 $status）" }
        val groups = root.getAsJsonArray("result_groups") ?: error("汽水音乐专辑搜索结果缺失")
        val group = groups.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject }.firstOrNull { it.text("id") == "albums" }
            ?: return QishuiCatalog.Page(emptyList(), offset, false)
        val rows = group.getAsJsonArray("data") ?: error("汽水音乐专辑搜索列表缺失")
        val albums = rows.mapNotNull { row ->
            val album = row.takeIf { it.isJsonObject }?.asJsonObject?.obj("entity")?.obj("album") ?: return@mapNotNull null
            val id = album.text("id").takeIf { it.matches(Regex("[0-9]{1,24}")) } ?: return@mapNotNull null
            val artists = album.getAsJsonArray("artists")?.mapNotNull { it.takeIf { a -> a.isJsonObject }?.asJsonObject }.orEmpty()
            val date = album.text("release_date").toLongOrNull()?.takeIf { it > 0 }?.let {
                runCatching { java.time.Instant.ofEpochSecond(it).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString() }.getOrDefault("")
            }.orEmpty()
            SearchEntity(id = id, title = album.text("name"), imageUrl = QishuiCatalog.image(album.obj("url_cover")),
                creator = artists.joinToString(" / ") { it.text("name") }, songCount = album.text("count_tracks").toLongOrNull(), date = date)
        }
        val more = group.get("has_more")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        val next = group.text("next_cursor").toIntOrNull() ?: if (more) error("汽水音乐专辑搜索游标缺失") else offset + rows.size()
        check(!more || next > offset) { "汽水音乐专辑搜索游标未前进" }
        return QishuiCatalog.Page(albums, next, more)
    }

    suspend fun load(id: Long): Pair<AlbumInfo, List<Track>> = withContext(Dispatchers.IO) {
        require(id > 0)
        val url = "https://www.qishui.com/share/album".toHttpUrl().newBuilder().addQueryParameter("album_id", id.toString()).build()
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36").build()
        val share = try {
            val html = http.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "汽水音乐专辑加载失败（HTTP ${response.code}）" }
                val body = response.body ?: error("汽水音乐专辑响应为空")
                val source = body.source()
                check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "汽水音乐专辑响应过大" }
                source.readUtf8()
            }
            parse(html, id)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { return@withContext QishuiArtistWorks.album(id) }
        if (share.first.size > share.second.size) {
            try {
                val pc = QishuiArtistWorks.album(id)
                if (pc.second.size > share.second.size) pc else share
            } catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { share }
        } else share
    }

    internal fun parse(html: String, expectedId: Long): Pair<AlbumInfo, List<Track>> {
        val root = routerData(html)
        val page = root.obj("loaderData")?.obj("album_page") ?: error("汽水音乐专辑数据缺失")
        val info = page.obj("albumInfo") ?: error("汽水音乐专辑信息缺失")
        val rawTracks = page.getAsJsonArray("trackList") ?: error("汽水音乐专辑歌曲缺失")
        return parseAlbumData(info, rawTracks, expectedId)
    }

    internal fun parsePcAlbum(root: JsonObject, expectedId: Long): Pair<AlbumInfo, List<Track>> {
        val status = root.text("status_code").toIntOrNull() ?: 0
        check(status == 0) { "汽水音乐专辑加载失败（代码 $status）" }
        return parseAlbumData(root.obj("album_info") ?: error("汽水音乐专辑信息缺失"),
            root.getAsJsonArray("tracks") ?: error("汽水音乐专辑歌曲缺失"), expectedId)
    }

    private fun parseAlbumData(info: JsonObject, rawTracks: com.google.gson.JsonArray, expectedId: Long): Pair<AlbumInfo, List<Track>> {
        check(info.text("id") == expectedId.toString()) { "汽水音乐专辑标识不匹配" }
        val artists = info.getAsJsonArray("artists")?.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject }.orEmpty()
        val cover = QishuiCatalog.image(info.obj("url_cover"))
        val name = info.text("name")
        val songs = rawTracks.mapNotNull { value ->
            val row = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val trackId = row.text("id").takeIf { it.matches(Regex("[0-9]{1,24}")) } ?: return@mapNotNull null
            val performers = row.getAsJsonArray("artists")?.mapNotNull { it.takeIf { a -> a.isJsonObject }?.asJsonObject }.orEmpty()
            Track(id = "qishui_$trackId", title = row.text("name"), artist = performers.joinToString(" / ") { it.text("name") },
                artistId = performers.firstOrNull()?.text("id").orEmpty(), album = name, albumId = expectedId.toString(),
                source = "qishui", sourceUrl = "https://www.qishui.com/track/$trackId", imgUrl = cover, duration = row.text("duration").toLongOrNull()?.coerceAtLeast(0) ?: 0,
                artistNames = performers.map { it.text("name") }, artistIds = performers.map { it.text("id") })
        }
        val release = info.text("release_date").toLongOrNull()?.coerceAtLeast(0)?.times(1000) ?: 0
        val album = AlbumInfo(id = expectedId, name = name, picUrl = cover, size = info.text("count_tracks").toIntOrNull()?.coerceAtLeast(0) ?: songs.size,
            publishTime = release, artist = artists.joinToString(" / ") { it.text("name") }, artistId = artists.firstOrNull()?.text("id")?.toLongOrNull() ?: 0)
        return album to songs
    }
    private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun routerData(html: String): JsonObject {
        val marker = "_ROUTER_DATA = "
        val position = html.indexOf(marker)
        check(position >= 0) { "汽水音乐分享页数据缺失" }
        return JsonParser.parseReader(JsonReader(StringReader(html.substring(position + marker.length)))).asJsonObject
    }
}
