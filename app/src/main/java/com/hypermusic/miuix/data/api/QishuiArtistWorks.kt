package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.AlbumInfo
import com.hypermusic.miuix.data.model.ArtistInfo
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 汽水 PC 公开歌手作品接口，游标由服务端提供，不按列表长度推算。 */
internal object QishuiArtistWorks {
    data class Page<T>(val items: List<T>, val cursor: String?, val more: Boolean)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).build()
    private val device = System.currentTimeMillis().toString()

    suspend fun songs(id: String, cursor: String): Page<Track> = withContext(Dispatchers.IO) {
        parseSongs(request(id, cursor, "tracks"), cursor)
    }
    suspend fun albums(id: String, cursor: String): Page<AlbumInfo> = withContext(Dispatchers.IO) {
        parseAlbums(request(id, cursor, "albums"), cursor)
    }
    suspend fun album(id: Long): Pair<AlbumInfo, List<Track>> = withContext(Dispatchers.IO) {
        require(id > 0)
        QishuiShareAlbum.parsePcAlbum(request(id.toString(), "", "album"), id)
    }
    suspend fun artist(id: String): ArtistInfo = withContext(Dispatchers.IO) {
        parseArtist(request(id, "", "artist"), id)
    }
    private fun request(id: String, cursor: String, type: String): JsonObject {
        require(id.matches(Regex("[0-9]{1,24}")) && cursor.length <= 128 && type in setOf("tracks", "albums", "album", "artist"))
        val params = mapOf("aid" to "386088", "app_name" to "luna_pc", "region" to "cn", "geo_region" to "cn",
            "os_region" to "cn", "sim_region" to "", "device_id" to device, "cdid" to "", "iid" to device,
            "version_name" to "3.3.0", "version_code" to "30030000", "channel" to "official", "build_mode" to "master",
            "network_carrier" to "", "ac" to "wifi", "tz_name" to "Asia/Shanghai", "resolution" to "",
            "device_platform" to "windows", "device_type" to "Windows", "os_version" to "Windows 11", "fp" to device,
            "cursor" to cursor, "count" to "50")
        val path = when (type) { "album" -> "/luna/pc/albums/$id"; "artist" -> "/luna/pc/artists/$id"; else -> "/luna/pc/artists/$id/$type" }
        val url = "https://api.qishui.com".toHttpUrl().newBuilder().encodedPath(path)
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val response = http.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
            .header("x-luna-background-type", "foreground").header("x-luna-is-background-req", "0")
            .header("x-luna-is-local-user", "1").build()).execute()
        return response.use {
            check(it.isSuccessful) { "汽水音乐歌手作品加载失败（HTTP ${it.code}）" }
            val source = it.body?.source() ?: error("汽水音乐歌手作品响应为空")
            check(!source.request(8L * 1024 * 1024 + 1)) { "汽水音乐歌手作品响应过大" }
            JsonParser.parseString(source.readUtf8()).asJsonObject
        }
    }
    internal fun parseArtist(root: JsonObject, expectedId: String): ArtistInfo {
        val status = root.text("status_code").toIntOrNull() ?: 0
        check(status == 0) { "汽水音乐歌手资料请求失败（代码 $status）" }
        val info = root.obj("artist_info") ?: error("汽水音乐歌手资料缺失")
        check(info.text("id") == expectedId) { "汽水音乐歌手标识不匹配" }
        return ArtistInfo(id = expectedId.toLongOrNull() ?: 0, name = info.text("name"), picUrl = QishuiCatalog.image(info.obj("url_avatar")),
            albumSize = info.text("count_albums").toIntOrNull()?.coerceAtLeast(0) ?: 0,
            musicSize = info.text("count_tracks").toIntOrNull()?.coerceAtLeast(0) ?: 0,
            briefDesc = info.obj("artist_profile")?.text("intro").orEmpty())
    }
    internal fun parseSongs(root: JsonObject, cursor: String): Page<Track> = parse(root, cursor, "tracks") { row ->
        val id = row.text("id").takeIf { it.matches(Regex("[0-9]{1,24}")) } ?: return@parse null
        val album = row.obj("album")
        val artists = row.objects("artists")
        Track(id = "qishui_$id", title = row.text("name"), artist = artists.joinToString(" / ") { it.text("name") },
            artistId = artists.firstOrNull()?.text("id").orEmpty(), album = album?.text("name").orEmpty(),
            albumId = album?.text("id").orEmpty(), source = "qishui", sourceUrl = "https://www.qishui.com/track/$id",
            imgUrl = QishuiCatalog.image(album?.obj("url_cover")), duration = row.text("duration").toLongOrNull()?.coerceAtLeast(0) ?: 0,
            artistNames = artists.map { it.text("name") }, artistIds = artists.map { it.text("id") })
    }
    internal fun parseAlbums(root: JsonObject, cursor: String): Page<AlbumInfo> = parse(root, cursor, "albums") { row ->
        val id = row.text("id").toLongOrNull()?.takeIf { it > 0 } ?: return@parse null
        val artists = row.objects("artists")
        AlbumInfo(id = id, name = row.text("name"), picUrl = QishuiCatalog.image(row.obj("url_cover")),
            size = row.text("count_tracks").toIntOrNull()?.coerceAtLeast(0) ?: 0,
            publishTime = (row.text("release_date").toLongOrNull()?.coerceAtLeast(0) ?: 0) * 1000,
            artist = artists.joinToString(" / ") { it.text("name") }, artistId = artists.firstOrNull()?.text("id")?.toLongOrNull() ?: 0)
    }
    private fun <T> parse(root: JsonObject, cursor: String, key: String, convert: (JsonObject) -> T?): Page<T> {
        val status = root.text("status_code").toIntOrNull() ?: 0
        check(status == 0) { "汽水音乐歌手作品请求失败（代码 $status）" }
        val more = root.get("has_more")?.takeIf { it.isJsonPrimitive }?.asBoolean == true
        val rows = root.getAsJsonArray(key) ?: error("汽水音乐歌手作品列表缺失")
        val items = rows.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject?.let(convert) }
        check(rows.size() == 0 || items.isNotEmpty()) { "汽水音乐歌手作品格式不受支持" }
        val next = root.text("next_cursor").takeIf { it.isNotBlank() }
        check(!more || (next != null && next != cursor && rows.size() > 0)) { "汽水音乐歌手作品游标未前进" }
        return Page(items, next, more)
    }
    private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.objects(key: String) = getAsJsonArray(key)?.mapNotNull { it.takeIf { row -> row.isJsonObject }?.asJsonObject }.orEmpty()
}
