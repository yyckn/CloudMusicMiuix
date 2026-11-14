package com.hypermusic.miuix.data.api

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonNull
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

internal data class RecommendationCard(val id: String, val title: String, val cover: String = "",
    val subtitle: String = "", val radio: Boolean = false, val background: String = "cover",
    val tracks: List<Track> = emptyList())
internal data class RecommendationSection(val title: String, val cards: List<RecommendationCard>)
internal data class AdditionalHome(val large: List<RecommendationCard> = emptyList(),
    val sections: List<RecommendationSection> = emptyList(), val error: String? = null)

/** Folia's provider protocols; account selection remains owned by the platform's auth store. */
internal class AdditionalRecommendations(context: Context) {
    private val context = context.applicationContext
    private val bodian = BodianClient(this.context)
    private val kugou = KugouClient(this.context)
    private val qishui = QishuiClient(this.context, okhttp3.OkHttpClient.Builder()
        .addInterceptor(com.hypermusic.miuix.data.lyrics.LyricRequests)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(25, java.util.concurrent.TimeUnit.SECONDS)
        .callTimeout(35, java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build())
    val homes = mutableStateMapOf<MusicPlatform, AdditionalHome>()
    val loading = mutableStateMapOf<MusicPlatform, Boolean>()
    private val stamps = mutableMapOf<MusicPlatform, String>()
    private val locks = MusicPlatform.entries.associateWith { Mutex() }
    private val collections = mutableMapOf<String, RecommendationCard>()
    private val qishuiLock = Mutex()
    private var feedKey = ""
    private var feedStamp = ""
    private var feedCount = 0
    private var totalFeedCount = 0
    private var selectedAt = 0L

    fun accountStamp(platform: MusicPlatform): String = when (platform) {
        MusicPlatform.NETEASE -> NeteaseApi.getCurrentUserId()?.toString().orEmpty()
        MusicPlatform.QQ -> QqMusicApi.dataAccountId.orEmpty()
        else -> AdditionalAccounts(context, platform).dataId.orEmpty()
    }
    fun card(id: String): RecommendationCard? = collections[id]

    suspend fun loadHome(platform: MusicPlatform, force: Boolean = false) = locks.getValue(platform).withLock {
        val stamp = accountStamp(platform)
        if (!force && homes.containsKey(platform) && stamps[platform] == stamp) return@withLock
        if (stamps[platform] != stamp) {
            homes.remove(platform)
            collections.keys.removeAll { it.startsWith("extra:${platform.storageKey}:") }
        }
        loading[platform] = true
        try {
            val home = when (platform) {
                MusicPlatform.BODIAN -> bodianHome()
                MusicPlatform.KUGOU -> kugouHome()
                MusicPlatform.QISHUI -> qishuiHome()
                else -> error("不支持的推荐平台")
            }
            currentCoroutineContext().ensureActive()
            if (stamp != accountStamp(platform)) return@withLock
            homes[platform] = home
            stamps[platform] = stamp
            collections.keys.removeAll { it.startsWith("extra:${platform.storageKey}:") }
            (home.large + home.sections.flatMap { it.cards }).forEach { collections[it.id] = it }
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) {
            if (stamp == accountStamp(platform)) homes[platform] = (homes[platform] ?: AdditionalHome()).copy(error = e.message ?: "推荐加载失败")
        } finally { loading[platform] = false }
    }

    suspend fun collection(id: String, refresh: Boolean): RecommendationCard {
        val parts = id.split(':')
        require(parts.size == 4 && parts[0] == "extra") { "推荐标识无效" }
        val platform = MusicPlatform.fromStorageKey(parts[1]) ?: error("推荐平台无效")
        val stamp = accountStamp(platform)
        if (!refresh && stamps[platform] == stamp) collections[id]?.takeIf { it.tracks.isNotEmpty() }?.let { return it }
        val card = when (platform) {
            MusicPlatform.BODIAN -> {
                require(parts[2] == "discover")
                bodianDiscover(parts[3].toInt(), collections[id]?.title.orEmpty())
            }
            MusicPlatform.KUGOU -> kugouCollection(parts[2], parts[3].toInt())
            else -> error("该内容为电台，请直接播放")
        }
        check(stamp == accountStamp(platform)) { "账号已切换，请重试" }
        collections[id] = card
        homes[platform]?.let { home -> homes[platform] = home.copy(
            large = home.large.map { if (it.id == id) card.copy(background = it.background) else it },
            sections = home.sections.map { it.copy(cards = it.cards.map { old -> if (old.id == id) card else old }) }) }
        return card
    }

    suspend fun radio(id: String, first: Boolean): List<Track> = withContext(Dispatchers.IO) {
        val p = id.split(':')
        require(p.size >= 3 && p[0] == "extra") { "电台标识无效" }
        val platform = MusicPlatform.fromStorageKey(p[1]) ?: error("电台平台无效")
        val stamp = accountStamp(platform)
        val songs = when (platform) {
            MusicPlatform.BODIAN -> {
                require(p[2] == "fm")
                parseBodianSongs(BodianCatalog.data(bodian.call("/api/service/music/recommendList")), "musicList")
            }
            MusicPlatform.KUGOU -> {
                require(p[2] == "fm")
                parseKugouRecommendations(kugou.recommendation("fm", account = KugouAuth(context, kugou).account()))
            }
            MusicPlatform.QISHUI -> qishuiLock.withLock {
                require(p[2] == "fm" || p[2] == "scene")
                val scene = if (p[2] == "scene") p.getOrNull(3)?.takeIf { it.matches(Regex("[0-9]{1,15}")) } ?: error("场景标识无效") else null
                if (first || feedKey != id || feedStamp != stamp) {
                    feedKey = id; feedStamp = stamp; feedCount = 0; selectedAt = System.currentTimeMillis()
                }
                val preference = JsonObject().apply {
                    if (scene == null) { add("scene_mode_id", JsonNull.INSTANCE); add("scene_mode_select_time", JsonNull.INSTANCE) }
                    else { addProperty("scene_mode_id", scene.toLong()); addProperty("scene_mode_select_time", selectedAt) }
                    add("preference_mode", JsonNull.INSTANCE)
                    addProperty("total_request_count", ++totalFeedCount)
                    addProperty("preference_mode_request_count", feedCount + 1); addProperty("current_mode_request_count", feedCount + 1)
                }
                val data = qishui.read("/feed/song-tab/", JsonObject().apply {
                    add("played_media", JsonArray()); addProperty("is_first_request", feedCount == 0)
                    addProperty("with_client_cache", feedCount != 0); addProperty("is_did_first_request", false)
                    addProperty("count", 20); add("feed_preference", preference)
                })
                val tracks = parseQishuiFeed(data)
                feedCount++
                tracks
            }
            else -> error("不支持的电台平台")
        }
        check(stamp == accountStamp(platform)) { "账号已切换，请重试" }
        check(songs.isNotEmpty()) { "暂无推荐歌曲，请稍后重试" }
        songs.distinctBy { it.id }
    }

    private suspend fun bodianDiscover(index: Int, fallbackTitle: String): RecommendationCard = withContext(Dispatchers.IO) {
        require(index in 0..100)
        val data = BodianCatalog.data(bodian.call("/api/service/home/aiPlaylistDetail", mapOf("index" to index.toString())))
        val tracks = parseBodianSongs(data, "musicList")
        val title = when (index) { 0 -> "潮趣日推"; 1 -> "新歌打赏"; else -> data.text("title").ifBlank { fallbackTitle } }
        RecommendationCard("extra:bodian:discover:$index", title, tracks.firstOrNull()?.imgUrl.orEmpty(), data.text("subTitle"), tracks = tracks)
    }

    private suspend fun bodianHome(): AdditionalHome = supervisorScope {
        val failures = java.util.concurrent.CopyOnWriteArrayList<String>()
        suspend fun <T> attempt(block: suspend () -> T): T? = try { block() }
            catch (e: CancellationException) { throw e } catch (e: Exception) { failures += e.message ?: "推荐加载失败"; null }
        val discover = async {
            attempt {
                val module = withContext(Dispatchers.IO) { BodianCatalog.data(bodian.call("/api/service/home/module", mapOf("moduleId" to "1"))) }
                val rows = module.getAsJsonArray("songList") ?: error("波点推荐模块缺失")
                val limiter = Semaphore(3)
                rows.mapNotNull { it.objectOrNull() }.map { row -> async {
                    limiter.withPermit { attempt { bodianDiscover(row.text("id").toInt(), row.text("title")) } }
                } }.awaitAll().filterNotNull()
            }.orEmpty()
        }
        val regular = async { attempt {
            withContext(Dispatchers.IO) {
                val data = BodianCatalog.data(bodian.call("/api/service/finds/playlist"))
                parseBodianSections(data)
            }
        }.orEmpty() }
        val cards = discover.await()
        AdditionalHome(listOf(RecommendationCard("extra:bodian:fm", "私人FM", subtitle = "边听边推荐", radio = true, background = "fm")) +
            cards.filter { it.id.endsWith(":0") || it.id.endsWith(":1") },
            listOfNotNull(cards.filterNot { it.id.endsWith(":0") || it.id.endsWith(":1") }
                .takeIf { it.isNotEmpty() }?.let { RecommendationSection("为你推荐", it) }) + regular.await(),
            failures.takeIf { it.isNotEmpty() }?.joinToString("；"))
    }

    private suspend fun kugouCollection(kind: String, cardId: Int): RecommendationCard = withContext(Dispatchers.IO) {
        require(kind == "daily" || kind == "card")
        val tracks = parseKugouRecommendations(kugou.recommendation(kind, cardId, KugouAuth(context, kugou).account()))
        val title = if (kind == "daily") "每日推荐" else kugouCardTitles[cardId] ?: error("未知推荐卡片")
        RecommendationCard("extra:kugou:$kind:$cardId", title, tracks.firstOrNull()?.imgUrl.orEmpty(),
            "共${tracks.size}首歌曲", tracks = tracks,
            background = when (cardId) { 3006 -> "random"; 3001 -> "heart"; else -> "cover" })
    }

    private suspend fun kugouHome(): AdditionalHome = supervisorScope {
        val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
        val limiter = Semaphore(3)
        val cards = (listOf("daily" to 0) + kugouCardTitles.keys.map { "card" to it }).map { (kind, id) -> async {
            limiter.withPermit {
                try { kugouCollection(kind, id) } catch (e: CancellationException) { throw e }
                catch (e: Exception) {
                    val title = if (kind == "daily") "每日推荐" else kugouCardTitles[id] ?: "推荐"
                    errors += "$title：${e.message}"
                    null
                }
            }
        } }.awaitAll().filterNotNull().filter { it.tracks.isNotEmpty() }
        AdditionalHome(cards.filter { it.id.endsWith(":0") } +
            RecommendationCard("extra:kugou:fm", "私人FM", subtitle = "边听边推荐", radio = true, background = "fm") +
            cards.filter { it.id.endsWith(":3006") || it.id.endsWith(":3001") },
            listOf(RecommendationSection("为你推荐", cards.filterNot { it.id.endsWith(":0") || it.id.endsWith(":3006") || it.id.endsWith(":3001") })),
            errors.takeIf { it.isNotEmpty() }?.joinToString("；"))
    }

    private suspend fun qishuiHome(): AdditionalHome = withContext(Dispatchers.IO) {
        val scenes = parseQishuiScenes(qishui.read("/feed/mode/", JsonObject()))
        val focus = scenes.firstOrNull { it.title.contains("专注") }
        val fm = RecommendationCard("extra:qishui:fm", "私人FM", subtitle = "边听边推荐", radio = true, background = "fm")
        AdditionalHome(listOf(fm) + listOfNotNull(focus?.copy(title = "专注模式", background = "heart")),
            scenes.filterNot { it.id == focus?.id }.groupBy { it.subtitle }.map { (title, rows) -> RecommendationSection(title.ifBlank { "场景电台" }, rows) })
    }

    companion object {
        private val kugouCardTitles = linkedMapOf(3006 to "VIP专属推荐", 3001 to "私人专属好歌", 3004 to "小众宝藏佳作",
            3014 to "喜欢这首歌的TA也喜欢", 3101 to "概念er新推", 3005 to "潮流尝鲜")

        internal fun parseBodianSongs(data: JsonObject, field: String): List<Track> {
            val rows = data.getAsJsonArray(field) ?: error("波点推荐歌曲列表缺失")
            val tracks = rows.mapNotNull { it.objectOrNull()?.let { row -> runCatching { BodianCatalog.parseSong(row) }.getOrNull() } }
            check(rows.isEmpty || tracks.isNotEmpty()) { "波点推荐歌曲格式不支持" }
            return tracks
        }

        internal fun parseBodianSections(data: JsonObject): List<RecommendationSection> {
            val groups = data.getAsJsonArray("lists") ?: error("波点推荐歌单缺失")
            return groups.mapNotNull { it.objectOrNull() }.map { group ->
                RecommendationSection(group.text("title"), group.getAsJsonArray("playLists")?.toList().orEmpty().mapNotNull { item ->
                    val row = item.objectOrNull() ?: return@mapNotNull null
                    val id = row.text("id").takeIf { it.matches(Regex("[1-9][0-9]{0,19}")) } ?: return@mapNotNull null
                    RecommendationCard("bodian:${row.text("sourceType").ifBlank { "4" }}:$id", row.text("name"),
                        row.text("pic").replace("http://", "https://"), row.text("playNum").toLongOrNull()?.let(::formatBodianPlayCount).orEmpty())
                })
            }.filter { it.cards.isNotEmpty() }
        }

        /** Recommendation songs use both flat and nested audio_info / album_info envelopes. */
        internal fun parseKugouRecommendations(data: JsonElement): List<Track> {
            val found = mutableListOf<Track>()
            val songListKeys = setOf("song_list", "songlist", "songList", "songs", "audios", "list", "lists", "info", "items", "records")
            var sawSongList = false
            var songRowCount = 0
            fun visit(value: JsonElement, depth: Int) {
                if (depth > 12) return
                if (value.isJsonArray) { value.asJsonArray.forEach { visit(it, depth + 1) }; return }
                val row = value.objectOrNull() ?: return
                val audio = row.get("audio_info").objectOrNull()
                val album = row.get("album_info").objectOrNull()
                val flat = row.deepCopy()
                audio?.entrySet()?.forEach { (key, v) -> if (!flat.has(key) || flat.get(key).isJsonNull) flat.add(key, v) }
                // Youth cards may put an uploader's non-audio hash on the outer object.
                audio?.text("hash")?.takeIf { it.matches(Regex("[A-Fa-f0-9]{32}")) }?.let { flat.addProperty("hash", it) }
                fun alias(target: String, vararg keys: String) { if (flat.text(target).isBlank()) keys.firstNotNullOfOrNull { flat.get(it)?.takeIf { v -> v.isJsonPrimitive && v.asString.isNotBlank() } }?.let { flat.add(target, it) } }
                alias("hash", "audio_hash", "Hash", "fileHash", "hash_128")
                alias("audio_name", "songname", "songName", "ori_audio_name", "SongName")
                alias("mixsongid", "songid", "songId")
                alias("author_name", "singername", "singer_name")
                alias("timelen", "timelength", "timelength_128")
                if (flat.text("timelen").isBlank()) {
                    val seconds = flat.text("time_length").ifBlank { flat.text("duration") }.toLongOrNull()
                    if (seconds != null && seconds in 0..86400) flat.addProperty("timelen", seconds * 1000)
                }
                alias("cover", "sizable_cover", "image", "pic")
                row.get("authors")?.takeIf { it.isJsonArray }?.asJsonArray?.let { authors ->
                    if (flat.get("Singers")?.isJsonArray != true && flat.get("singerinfo")?.isJsonArray != true) flat.add("Singers", JsonArray().apply {
                        authors.mapNotNull { it.objectOrNull() }.forEach { author -> add(JsonObject().apply {
                            addProperty("name", author.text("author_name")); addProperty("id", author.text("author_id"))
                        }) }
                    })
                }
                if (album != null) {
                    if (flat.text("album_name").isBlank()) flat.addProperty("album_name", album.text("album_name").ifBlank { album.text("name") })
                    if (flat.text("album_id").isBlank()) flat.addProperty("album_id", album.text("album_id").ifBlank { album.text("id") })
                    if (flat.text("cover").isBlank()) flat.addProperty("cover", album.text("sizable_cover").ifBlank { album.text("cover") })
                }
                runCatching { KugouCatalog.parseTrack(flat) }.getOrNull()?.takeIf { it.title.isNotBlank() }?.let { found += it; return }
                row.entrySet().forEach { (key, v) ->
                    if (key in songListKeys && v.isJsonArray) {
                        sawSongList = true
                        songRowCount += v.asJsonArray.size()
                    }
                    if (v.isJsonArray || v.isJsonObject) visit(v, depth + 1)
                }
            }
            visit(data, 0)
            // Optional recommendations (including card 3014) may legitimately return an empty list.
            // Missing lists and nonempty, unparseable songs remain failures rather than silently disappearing.
            if (found.isEmpty() && sawSongList && songRowCount == 0) return emptyList()
            check(found.isNotEmpty()) {
                if (sawSongList) "酷狗推荐歌曲格式暂不支持" else "酷狗推荐响应缺少歌曲列表"
            }
            return found.distinctBy { it.id }
        }

        internal fun parseQishuiScenes(data: JsonObject): List<RecommendationCard> {
            val blocks = data.getAsJsonArray("feed_mode_block") ?: error("汽水场景电台列表缺失")
            return blocks.flatMap { block ->
                val b = block.objectOrNull() ?: return@flatMap emptyList()
                b.getAsJsonArray("feed_mode")?.toList().orEmpty().mapNotNull { item ->
                    val mode = item.objectOrNull()?.getAsJsonObject("entity")?.getAsJsonObject("feed_mode") ?: return@mapNotNull null
                    val id = mode.getAsJsonObject("scene_mode")?.text("scene_mode_id")?.takeIf { it.matches(Regex("[0-9]{1,15}")) } ?: return@mapNotNull null
                    val title = mode.text("text").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    RecommendationCard("extra:qishui:scene:$id", title, QishuiCatalog.image(mode.getAsJsonObject("url_info")), b.text("title"), radio = true)
                }
            }.distinctBy { it.id }
        }

        internal fun parseQishuiFeed(data: JsonObject): List<Track> {
            val items = data.getAsJsonArray("items") ?: error("汽水推荐歌曲列表缺失")
            return items.mapNotNull { it.objectOrNull() }.filter { it.text("type").let { type -> type.isBlank() || type == "track" } }
                .map { QishuiCatalog.parseTrack(QishuiCatalog.unwrapTrack(it)) }
        }

        internal fun formatBodianPlayCount(count: Long): String = if (count >= 10_000)
            java.math.BigDecimal.valueOf(count).movePointLeft(4).setScale(1, java.math.RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString() + "万次播放"
            else "${count.coerceAtLeast(0)}次播放"

        private fun JsonElement?.objectOrNull(): JsonObject? = this?.takeIf { it.isJsonObject }?.asJsonObject
        private fun JsonObject.text(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    }
}
