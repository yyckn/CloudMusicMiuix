package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.SearchCategory
import com.hypermusic.miuix.data.model.SearchEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

internal object SearchEntityParser {
    /** 移动端与桌面端搜索使用不同的数组字段，统一后复用现有解析。 */
    fun normalizeQqMobile(root: JsonObject, type: Int): JsonObject {
        val (source, target) = when (type) {
            0 -> "item_song" to "song"
            1 -> "singer" to "singer"
            2 -> "item_album" to "album"
            3 -> "item_songlist" to "songlist"
            else -> error("不支持的QQ音乐搜索类型")
        }
        val list = root.getAsJsonArray(source) ?: error("QQ音乐移动端搜索结果格式异常")
        return JsonObject().apply { add(target, JsonObject().apply { add("list", list) }) }
    }
    private fun JsonObject.value(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
        get(key)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
    }.orEmpty()
    private fun JsonObject.number(vararg keys: String): Long? = value(*keys).toLongOrNull()
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun dateMillis(value: Long?): String = value?.takeIf { it > 0 }?.let {
        SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Shanghai") }.format(Date(it))
    }.orEmpty()
    private fun dateString(value: String): String {
        val digits = value.filter(Char::isDigit)
        return if (digits.length >= 8) "${digits.take(4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}" else value
    }
    fun parseNetease(root: JsonObject, category: SearchCategory): List<SearchEntity> {
        val key = when (category) { SearchCategory.PLAYLIST -> "playlists"; SearchCategory.ALBUM -> "albums"; SearchCategory.ARTIST -> "artists"; else -> return emptyList() }
        return root.getAsJsonArray(key)?.mapNotNull { element ->
            val o = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = o.value("id"); val title = o.value("name")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            SearchEntity(id, title, o.value("coverImgUrl", "picUrl", "img1v1Url"),
                o.obj("creator")?.value("nickname") ?: o.obj("artist")?.value("name").orEmpty(),
                o.number("trackCount", "musicSize"), o.number("playCount"), o.number("albumSize"), dateMillis(o.number("publishTime")))
        }.orEmpty()
    }
    fun parseQq(root: JsonObject, category: SearchCategory): List<SearchEntity> {
        val key = when (category) { SearchCategory.PLAYLIST -> "songlist"; SearchCategory.ALBUM -> "album"; SearchCategory.ARTIST -> "singer"; else -> return emptyList() }
        val array = root.obj(key)?.getAsJsonArray("list") ?: return emptyList()
        return array.mapNotNull { element ->
            val o = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
            val id = when (category) {
                SearchCategory.PLAYLIST -> o.value("dissid", "diss_id", "id", "tid")
                SearchCategory.ALBUM -> o.value("albumID", "albumid", "id")
                SearchCategory.ARTIST -> o.value("singerMID", "singermid", "mid")
                else -> ""
            }
            val title = when (category) {
                SearchCategory.PLAYLIST -> o.value("dissname", "diss_name", "title", "name")
                SearchCategory.ALBUM -> o.value("albumName", "albumname", "name", "title")
                SearchCategory.ARTIST -> o.value("singerName", "singername", "name", "title")
                else -> ""
            }.replace(Regex("</?em>", RegexOption.IGNORE_CASE), "")
            if (id.isBlank() || title.isBlank()) return@mapNotNull null
            val mid = o.value("albumMID", "albummid", "mid")
            val image = o.value("imgurl", "picurl", "cover", "singerPic", "singerpic", "logo").ifBlank {
                when (category) {
                    SearchCategory.ALBUM -> if (mid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R500x500M000$mid.jpg" else ""
                    SearchCategory.ARTIST -> "https://y.gtimg.cn/music/photo_new/T001R500x500M000$id.jpg"
                    else -> ""
                }
            }.replace("http://", "https://")
            val singer = o.getAsJsonArray("singer_list")?.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject?.value("name").orEmpty()
            SearchEntity(id, title, image, o.value("creator_name", "creatorName", "singerName", "singername", "author", "nickname")
                .ifBlank { o.obj("creator")?.value("name", "nickname").orEmpty() }.ifBlank { singer },
                o.number("song_count", "song_count_num", "songnum", "songNum", "song_num", "song_count_show"),
                o.number("listen_num", "listennum", "listenNum", "play_count"),
                o.number("album_count", "album_num", "albumNum"), dateString(o.value("publicTime", "publish_date", "aDate", "publish_time")))
        }
    }
}
