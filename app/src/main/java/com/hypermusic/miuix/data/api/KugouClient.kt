package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** 酷狗账号和其他音乐平台保持独立。 */
internal class KugouClient(context: Context, private val http: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(com.hypermusic.miuix.data.lyrics.LyricRequests)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).followRedirects(false).build()) {
    private val prefs = context.applicationContext.getSharedPreferences("kugou_prefs", Context.MODE_PRIVATE)
    private val mid = synchronized(deviceLock) {
        prefs.getString("mid", null)?.takeIf { it.matches(Regex("[A-F0-9]{32}")) } ?: ByteArray(16)
            .also { SecureRandom().nextBytes(it) }.joinToString("") { "%02X".format(it.toInt() and 255) }
            .also { check(prefs.edit().putString("mid", it).commit()) { "无法保存酷狗设备标识" } }
    }

    internal fun qrRequest(path: String, extra: Map<String, String>): JsonObject {
        require(path == "/v2/qrcode" || path == "/v2/get_userinfo_qrcode")
        val parameters = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005",
            "clientver" to "20489", "clienttime" to (System.currentTimeMillis() / 1000).toString())
        parameters.putAll(extra)
        parameters["signature"] = webSignature(parameters)
        val url = "https://login-user.kugou.com".toHttpUrl().newBuilder().encodedPath(path)
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("mid", mid).header("dfid", "-").header("clienttime", parameters.getValue("clienttime")).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐响应过大" }
            JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐响应格式无效")
        }
    }

    internal fun searchRequest(type: String, keyword: String, page: Int, size: Int): JsonObject {
        require(type in setOf("song", "album", "author", "special") && keyword.isNotBlank() && keyword.length <= 500 && page > 0 && size in 1..100)
        val parameters = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005",
            "clientver" to "20489", "clienttime" to (System.currentTimeMillis() / 1000).toString(),
            "albumhide" to "0", "iscorrection" to "1", "keyword" to keyword.trim(), "nocollect" to "0",
            "page" to page.toString(), "pagesize" to size.toString(), "platform" to "AndroidFilter")
        parameters["signature"] = androidSignature(parameters)
        val path = "/${if (type == "song") "v3" else "v1"}/search/$type"
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath(path)
            .apply { parameters.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("x-router", "complexsearch.kugou.com").header("mid", mid).header("dfid", "-")
            .header("clienttime", parameters.getValue("clienttime")).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐搜索失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐搜索响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "酷狗音乐搜索响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐搜索响应格式无效")
            check(json.get("status")?.asInt == 1) { "酷狗音乐搜索失败（${json.get("errcode")?.asString ?: "未知错误"}）" }
            json.getAsJsonObject("data") ?: error("酷狗音乐搜索响应缺少数据")
        }
    }

    internal fun audioRequest(hash: String, albumId: String, albumAudioId: String, quality: String, account: KugouAuth.Account?): JsonObject {
        require(hash.matches(Regex("[a-fA-F0-9]{32}")) && quality in setOf("128", "320", "flac", "high"))
        require(albumId.isEmpty() || albumId.matches(Regex("[0-9]{1,20}")))
        require(albumAudioId.isEmpty() || albumAudioId.matches(Regex("[0-9]{1,20}")))
        val normalized = hash.lowercase()
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "11430",
            "clienttime" to (System.currentTimeMillis() / 1000).toString(), "album_id" to albumId.ifEmpty { "0" },
            "area_code" to "1", "hash" to normalized, "ssa_flag" to "is_fromtrack", "version" to "11430",
            "page_id" to "151369488", "quality" to quality, "album_audio_id" to albumAudioId.ifEmpty { "0" },
            "behavior" to "play", "pid" to "2", "cmd" to "26", "pidversion" to "3001", "IsFreePart" to "0",
            "ppage_id" to "463467626,350369493,788954147", "cdnBackup" to "1", "module" to "")
        if (account != null) { p["userid"] = account.userId; p["token"] = account.token }
        p["key"] = md5(normalized + "57ae12eb6890223e355ccfcb74edf70d" + "1005" + mid + (account?.userId ?: "0"))
        p["signature"] = androidSignature(p)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath("/v5/url")
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("x-router", "trackercdn.kugou.com").header("mid", mid).header("dfid", "-")
            .header("clienttime", p.getValue("clienttime")).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐播放请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐播放响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐播放响应过大" }
            JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐播放响应无效")
        }
    }

    internal fun userPlaylistRequest(account: KugouAuth.Account, page: Int, size: Int): JsonObject {
        require(account.userId.matches(Regex("[1-9][0-9]{0,19}")) && account.token.isNotBlank() && page > 0 && size in 1..100)
        val data = JsonObject().apply {
            addProperty("userid", account.userId); addProperty("token", account.token); addProperty("total_ver", 979)
            addProperty("type", 2); addProperty("page", page); addProperty("pagesize", size)
        }.toString()
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to (System.currentTimeMillis() / 1000).toString(), "plat" to "1", "userid" to account.userId, "token" to account.token)
        p["signature"] = androidSignature(p, data)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath("/v7/get_all_list")
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("x-router", "cloudlist.service.kugou.com").header("mid", mid).header("dfid", "-")
            .header("clienttime", p.getValue("clienttime")).post(data.toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐歌单请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐歌单响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "酷狗音乐歌单响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐歌单响应格式无效")
            val status = json.get("status")?.takeIf { it.isJsonPrimitive }?.asString
            check(status == null || status == "1") { "酷狗音乐歌单请求失败（${json.get("error_code")?.asString ?: json.get("errcode")?.asString ?: "未知错误"}）" }
            json
        }
    }

    internal fun playlistRequest(id: String, offset: Int, limit: Int, account: KugouAuth.Account? = null): JsonObject {
        require(id.matches(Regex("[A-Za-z0-9_-]{1,128}")) && offset >= 0 && limit in 1..100)
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to (System.currentTimeMillis() / 1000).toString(), "area_code" to "1", "begin_idx" to offset.toString(),
            "plat" to "1", "type" to "1", "mode" to "1", "personal_switch" to "1", "extend_fields" to "abtags,hot_cmt,popularization",
            "pagesize" to limit.toString(), "global_collection_id" to id)
        if (account != null) { p["userid"] = account.userId; p["token"] = account.token }
        p["signature"] = androidSignature(p)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath("/pubsongs/v2/get_other_list_file_nofilt")
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("mid", mid).header("dfid", "-").header("clienttime", p.getValue("clienttime")).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐歌单请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐歌单响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "酷狗音乐歌单响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐歌单响应无效")
            check(json.get("status")?.asInt == 1) { "酷狗音乐歌单请求失败（${json.get("errcode")?.asString ?: "未知错误"}）" }
            json.getAsJsonObject("data") ?: error("酷狗音乐歌单响应缺少数据")
        }
    }

    /** 收藏外部歌单或专辑；调用方必须提供来源创建者和原始列表标识。 */
    internal fun addCollection(account: KugouAuth.Account, name: String, creatorId: String,
        originalId: String, creatorGid: String, album: Boolean) {
        require(account.userId.matches(Regex("[1-9][0-9]{0,19}")) && account.token.isNotBlank())
        require(name.isNotBlank() && name.length <= 512 && creatorId.matches(Regex("[0-9]{1,20}")))
        require(originalId.matches(Regex("[A-Za-z0-9_-]{1,128}")) && creatorGid.matches(Regex("[A-Za-z0-9_-]{0,128}")))
        val payload = JsonObject().apply {
            addProperty("userid", account.userId); addProperty("token", account.token)
            addProperty("total_ver", 0); addProperty("name", name); addProperty("type", 1)
            addProperty("source", if (album) 2 else 1); addProperty("is_pri", 0)
            addProperty("list_create_userid", creatorId); addProperty("list_create_listid", originalId)
            addProperty("list_create_gid", creatorGid); addProperty("from_shupinmv", 0)
        }.toString()
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val params = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to timestamp, "userid" to account.userId, "token" to account.token)
        params["signature"] = androidSignature(params, payload)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath("/cloudlist.service/v5/add_list")
            .apply { params.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("mid", mid).header("dfid", "-").header("clienttime", timestamp)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐收藏操作失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐收藏响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐收藏响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐收藏响应无效")
            check(json.get("status")?.asString == "1") { "酷狗音乐收藏失败（${json.get("error_code")?.asString ?: json.get("errcode")?.asString ?: "未知错误"}）" }
        }
    }

    /** 对齐 KuGouMusicApi playlist_del：AES 请求体、RSA 会话信封，返回体同样为 AES。 */
    internal fun removeCollection(account: KugouAuth.Account, listId: String) {
        val id = listId.toLongOrNull()?.takeIf { it > 0 } ?: error("酷狗音乐收藏引用无效")
        val seed = ByteArray(3).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it.toInt() and 255) }
        val digest = md5(seed)
        val key = javax.crypto.spec.SecretKeySpec(digest.substring(0, 16).toByteArray(), "AES")
        val iv = javax.crypto.spec.IvParameterSpec(digest.substring(16).toByteArray())
        val aes = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        aes.init(javax.crypto.Cipher.ENCRYPT_MODE, key, iv)
        val data = JsonObject().apply { addProperty("listid", id); addProperty("total_ver", 0); addProperty("type", 1) }
        val payload = android.util.Base64.encodeToString(aes.doFinal(data.toString().toByteArray()), android.util.Base64.NO_WRAP)
        val envelope = JsonObject().apply {
            addProperty("aes", seed); addProperty("uid", account.userId.toLong()); addProperty("token", account.token)
        }.toString().toByteArray()
        // 公共 API 的服务器公钥，不含用户或应用私钥。
        val publicKey = "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQDIAG7QOELSYoIJvTFJhMpe1s/gbjDJX51HBNnEl5HXqTW6lQ7LC8jr9fWZTwusknp+sVGzwd40MwP6U5yDE27M/X1+UR4tvOGOqp94TJtQ1EPnWGWXngpeIW5GxoQGao1rmYWAu6oi1z9XkChrsUdC6DJE5E221wf/4WLFxwAtRQIDAQAB"
        val rsa = javax.crypto.Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(javax.crypto.Cipher.ENCRYPT_MODE, java.security.KeyFactory.getInstance("RSA").generatePublic(
            java.security.spec.X509EncodedKeySpec(android.util.Base64.decode(publicKey, android.util.Base64.DEFAULT))))
        val sealed = envelope.toList().chunked(117).flatMap { chunk -> rsa.doFinal(chunk.toByteArray()).toList() }
            .joinToString("") { "%02X".format(it.toInt() and 255) }
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val params = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to timestamp, "key" to md5("1005" + "OIlwieks28dk2k092lksi2UIkp" + "20489" + timestamp),
            "last_area" to "gztx", "last_time" to timestamp, "p" to sealed)
        params["signature"] = androidSignature(params, payload)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath("/v2/delete_list")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        val request = Request.Builder().url(url).header("x-router", "cloudlist.service.kugou.com")
            .header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("mid", mid).header("dfid", "-").header("clienttime", timestamp)
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐取消收藏失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐取消收藏响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐取消收藏响应过大" }
            val bytes = source.readByteArray()
            val json = if (bytes.toString(Charsets.UTF_8).trimStart().startsWith("{")) {
                JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
            } else {
                aes.init(javax.crypto.Cipher.DECRYPT_MODE, key, iv)
                JsonParser.parseString(aes.doFinal(bytes).toString(Charsets.UTF_8)).asJsonObject
            }
            check(json.get("status")?.asString == "1") { "酷狗音乐取消收藏失败（${json.get("error_code")?.asString ?: json.get("errcode")?.asString ?: "未知错误"}）" }
        }
    }

    internal fun songMutation(account: KugouAuth.Account, listId: String, track: com.hypermusic.miuix.data.model.Track, fileId: String?, add: Boolean) {
        require(listId.matches(Regex("[1-9][0-9]{0,18}")))
        require(track.kugouHash.matches(Regex("[A-Fa-f0-9]{32}")))
        val timestamp = (System.currentTimeMillis() / 1000).toString()
        val row = JsonObject().apply {
            if (add) {
                addProperty("number", 1); addProperty("name", track.title); addProperty("hash", track.kugouHash.uppercase())
                addProperty("size", 0); addProperty("sort", 0); addProperty("timelen", 0); addProperty("bitrate", 0)
                addProperty("album_id", track.albumId.toLongOrNull() ?: 0); addProperty("mixsongid", track.kugouAlbumAudioId.toLongOrNull() ?: 0)
            } else {
                val numeric = fileId?.toLongOrNull()?.takeIf { it > 0 } ?: error("酷狗音乐收藏条目标识缺失")
                addProperty("fileid", numeric)
            }
        }
        val payload = JsonObject().apply {
            if (add) addProperty("userid", account.userId)
            addProperty("token", account.token); addProperty("listid", listId); addProperty("list_ver", 0); addProperty("type", 0)
            if (!add) addProperty("userid", account.userId)
            if (add) { addProperty("slow_upload", 1); addProperty("scene", "false;null") }
            add("data", com.google.gson.JsonArray().apply { add(row) })
        }.toString()
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to timestamp, "userid" to account.userId, "token" to account.token)
        if (add) { p["last_time"] = timestamp; p["last_area"] = "gztx" }
        p["signature"] = androidSignature(p, payload)
        val path = if (add) "/cloudlist.service/v6/add_song" else "/v4/delete_songs"
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath(path)
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("mid", mid).header("dfid", "-").header("clienttime", timestamp)
            .apply { if (!add) header("x-router", "cloudlist.service.kugou.com") }
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐收藏操作失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐收藏操作响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐收藏响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐收藏响应无效")
            check(json.get("status")?.asString == "1") { "酷狗音乐收藏操作失败（${json.get("error_code")?.asString ?: json.get("errcode")?.asString ?: "未知错误"}）" }
        }
    }

    internal fun legacyAudioRequest(hash: String): JsonObject {
        require(hash.matches(Regex("[a-fA-F0-9]{32}")))
        val url = "https://m.kugou.com/app/i/getSongInfo.php".toHttpUrl().newBuilder()
            .addQueryParameter("cmd", "playInfo").addQueryParameter("hash", hash).build()
        val request = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124.0.0.0 Mobile Safari/537.36").build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐备用播放请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐备用播放响应为空")
            val source = body.source()
            check(body.contentLength() <= 2 * 1024 * 1024 && !source.request(2L * 1024 * 1024 + 1)) { "酷狗音乐备用播放响应过大" }
            JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐备用播放响应无效")
        }
    }

    internal fun catalogRequest(path: String, data: JsonObject, tid: Int): JsonObject {
        require(path in setOf("/kmr/v2/albums", "/v1/album_audio/lite", "/kmr/v3/author", "/kmr/v1/author/albums", "/kmr/v1/audio_group/author", "/kmr/v2/audio"))
        require(tid in setOf(36, 220, 238, 255))
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to (System.currentTimeMillis() / 1000).toString())
        val requestData = data.deepCopy()
        if (path == "/kmr/v1/audio_group/author") {
            requestData.addProperty("appid", 1005); requestData.addProperty("clientver", 20489)
            requestData.addProperty("mid", mid); requestData.addProperty("clienttime", p.getValue("clienttime").toLong())
            requestData.addProperty("key", md5("1005OIlwieks28dk2k092lksi2UIkp20489${p.getValue("clienttime")}"))
        }
        val payload = requestData.toString()
        p["signature"] = androidSignature(p, payload)
        val host = if (path == "/kmr/v1/audio_group/author") "https://openapi.kugou.com" else "https://gateway.kugou.com"
        val url = host.toHttpUrl().newBuilder().encodedPath(path)
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .header("x-router", "openapi.kugou.com").header("kg-tid", tid.toString()).header("mid", mid)
            .header("dfid", "-").header("clienttime", p.getValue("clienttime"))
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗音乐目录请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗音乐目录响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "酷狗音乐目录响应过大" }
            val json = JsonParser.parseString(source.readUtf8()).takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐目录响应无效")
            check(json.get("status")?.asInt == 1) { "酷狗音乐目录请求失败（${json.get("errcode")?.asString ?: json.get("error_code")?.asString ?: "未知错误"}）" }
            json
        }
    }

    /** Recommendation protocols used by Folia / KuGouMusicApi v1.6.0. */
    internal fun recommendation(kind: String, cardId: Int = 0, account: KugouAuth.Account?): JsonObject {
        require(kind in setOf("fm", "daily", "card"))
        val now = System.currentTimeMillis()
        val p = linkedMapOf("dfid" to "-", "mid" to mid, "uuid" to "-", "appid" to "1005", "clientver" to "20489",
            "clienttime" to (now / 1000).toString())
        account?.let { p["userid"] = it.userId; p["token"] = it.token }
        val data = JsonObject()
        val path: String
        val router: String
        when (kind) {
            "fm" -> {
                path = "/v2/personal_recommend"; router = "persnfm.service.kugou.com"
                data.addProperty("appid", 1005); data.addProperty("clienttime", now); data.addProperty("mid", mid)
                data.addProperty("action", "play"); data.addProperty("recommend_source_locked", 0)
                data.addProperty("song_pool_id", 0); data.addProperty("callerid", 0); data.addProperty("m_type", 1)
                data.addProperty("platform", "ios"); data.addProperty("area_code", 1); data.addProperty("remain_songcnt", 0)
                data.addProperty("clientver", 20489); data.addProperty("is_overplay", 0); data.addProperty("mode", "normal")
                data.addProperty("fakem", "ca981cfc583a4c37f28d2d49000013c16a0a")
                data.addProperty("key", md5("1005OIlwieks28dk2k092lksi2UIkp20489$now"))
                account?.let { data.addProperty("userid", it.userId); data.addProperty("kguid", it.userId); data.addProperty("token", it.token) }
            }
            "daily" -> { path = "/everyday_song_recommend"; router = "everydayrec.service.kugou.com"; p["platform"] = "ios" }
            else -> {
                require(cardId in setOf(3006, 3001, 3004, 3014, 3101, 3005))
                path = "/youth/v1/song/single_card_recommend"; router = "gateway.kugou.com"
                p.putAll(mapOf("card_id" to cardId.toString(), "area_code" to "1", "platform" to "ios", "module_id" to "1",
                    "ver" to "v2", "pagesize" to "30", "clientver" to "11490"))
                data.addProperty("tagid", ""); data.addProperty("u_info", ""); data.addProperty("source_mixsong", "")
            }
        }
        // The daily endpoint has an empty POST body rather than an empty JSON object.
        val payload = if (kind == "daily") "" else data.toString()
        p["signature"] = androidSignature(p, payload)
        val url = "https://gateway.kugou.com".toHttpUrl().newBuilder().encodedPath(path)
            .apply { p.forEach { (key, value) -> addQueryParameter(key, value) } }.build()
        val request = Request.Builder().url(url).header("User-Agent", "Android15-1070-11083-46-0-DiscoveryDRADProtocol-wifi")
            .apply { if (kind != "card") header("x-router", router) }
            .header("kg-rc", "1").header("kg-thash", "5d816a0").header("kg-rec", "1").header("kg-rf", "B9EDA08A64250DEFFBCADDEE00F8F25F")
            .header("mid", mid).header("dfid", "-").header("clienttime", p.getValue("clienttime"))
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        return http.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "酷狗推荐请求失败（HTTP ${response.code}）" }
            val body = response.body ?: error("酷狗推荐响应为空")
            val source = body.source()
            check(body.contentLength() <= 8 * 1024 * 1024 && !source.request(8L * 1024 * 1024 + 1)) { "酷狗推荐响应过大" }
            val result = JsonParser.parseString(source.readUtf8()).asJsonObject
            check(result.get("status")?.asInt != 0 && result.get("error_code")?.asInt.let { it == null || it == 0 }) {
                "酷狗推荐请求失败（${result.get("error_code") ?: result.get("errcode") ?: "未知错误"}）"
            }
            result
        }
    }

    internal fun trackHash(track: com.hypermusic.miuix.data.model.Track): String {
        if (track.kugouHash.matches(Regex("[A-Fa-f0-9]{32}"))) return track.kugouHash.uppercase()
        val embedded = track.id.removePrefix("kugou_").takeIf { track.id.startsWith("kugou_") && it.matches(Regex("[A-Fa-f0-9]{32}")) }
        if (embedded != null) return embedded.uppercase()
        val rawId = track.kugouAlbumAudioId.ifBlank { track.id.removePrefix("kugou_") }
        val id = rawId.toLongOrNull()?.takeIf { it in 1..9_007_199_254_740_991L } ?: error("酷狗音乐歌曲标识缺失")
        val result = catalogRequest("/kmr/v2/audio", JsonObject().apply {
            add("data", com.google.gson.JsonArray().apply { add(JsonObject().apply { addProperty("entity_id", id) }) })
            addProperty("fields", "base,audio_info")
        }, 238)
        val rows = result.getAsJsonArray("data") ?: error("酷狗音乐歌曲详情缺失")
        val item = rows.firstOrNull()?.takeIf { it.isJsonObject }?.asJsonObject ?: error("酷狗音乐歌曲详情缺失")
        val responseId = item.getAsJsonObject("base")?.get("album_audio_id")?.asString.orEmpty()
        check(responseId == id.toString()) { "酷狗音乐歌曲标识不匹配" }
        val hash = item.getAsJsonObject("audio_info")?.get("hash")?.asString.orEmpty()
        check(hash.matches(Regex("[A-Fa-f0-9]{32}"))) { "酷狗音乐歌曲哈希缺失" }
        return hash.uppercase()
    }

    companion object {
        private val deviceLock = Any()
        internal fun webSignature(params: Map<String, String>): String {
            val salt = "NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt"
            val text = salt + params.entries.map { "${it.key}=${it.value}" }.sorted().joinToString("") + salt
            return md5(text)
        }
        internal fun androidSignature(params: Map<String, String>, body: String = ""): String {
            val salt = "OIlwieks28dk2k092lksi2UIkp"
            val text = salt + params.toSortedMap().entries.joinToString("") { "${it.key}=${it.value}" } + body + salt
            return md5(text)
        }
        private fun md5(text: String) = MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}
