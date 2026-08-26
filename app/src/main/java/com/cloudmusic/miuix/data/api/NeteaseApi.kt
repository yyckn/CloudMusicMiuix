package com.cloudmusic.miuix.data.api

import com.cloudmusic.miuix.data.model.LyricLine
import com.cloudmusic.miuix.data.model.Playlist
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.SongLyrics
import com.cloudmusic.miuix.data.model.Track
import com.cloudmusic.miuix.data.model.UserProfile
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object NeteaseApi {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val gson = Gson()

    private var cookies: String = ""
    private var context: android.content.Context? = null
    private var userId: Long? = null
    private var onProfileChanged: (() -> Unit)? = null

    fun setOnProfileChangedListener(listener: (() -> Unit)?) {
        onProfileChanged = listener
    }

    fun init(appContext: android.content.Context) {
        context = appContext.applicationContext
        // Load saved cookies
        val prefs = context!!.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
        cookies = prefs.getString("cookies", "") ?: ""
        android.util.Log.d("NeteaseApi", "Init - Loaded cookies: $cookies")
        // 获取用户ID
        if (cookies.isNotEmpty()) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                userId = getUserId()
                android.util.Log.d("NeteaseApi", "Init - User ID: $userId")
            }
        }
    }

    fun setCookie(newCookies: String) {
        cookies = newCookies
        // Save to SharedPreferences
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookies).apply()
        }
        android.util.Log.d("NeteaseApi", "Cookie set and saved: $cookies")
        // 获取用户ID
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val oldUserId = userId
            userId = getUserId()
            android.util.Log.d("NeteaseApi", "User ID: $userId")
            // 如果用户ID变化，清除旧的用户资料缓存并通知UI
            if (oldUserId != null && oldUserId != userId) {
                android.util.Log.d("NeteaseApi", "User changed from $oldUserId to $userId, clearing profile cache")
                clearUserProfileCache()
                // 通知UI更新
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    onProfileChanged?.invoke()
                }
            }
        }
    }

    fun getCookie(): String = cookies

    fun isLoggedIn(): Boolean {
        return cookies.contains("MUSIC_U")
    }

    fun getCookiesFromWebView(): String {
        try {
            val cookieManager = android.webkit.CookieManager.getInstance()
            val music163Cookies = cookieManager.getCookie("https://music.163.com") ?: ""
            val yMusicCookies = cookieManager.getCookie("https://y.music.163.com") ?: ""
            
            val allCookies = buildString {
                if (music163Cookies.isNotEmpty()) append(music163Cookies)
                if (yMusicCookies.isNotEmpty()) {
                    if (isNotEmpty()) append("; ")
                    append(yMusicCookies)
                }
            }
            
            if (allCookies.isNotEmpty() && allCookies.contains("MUSIC_U")) {
                setCookie(allCookies)
                android.util.Log.d("NeteaseApi", "Cookies from WebView: $cookies")
            }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "Error getting cookies from WebView: ${e.message}")
        }
        return cookies
    }

    private fun ensureCookie() {
        if (cookies.isNotEmpty()) return
        
        try {
            val nuid = createSecretKey(32)
            val nnidValue = "${nuid},${System.currentTimeMillis()}"
            cookies = "_ntes_nuid=${nuid}; _ntes_nnid3=${nnidValue}; NMTID=0"
            
            // Visit homepage to set cookies
            val request = Request.Builder()
                .url("https://music.163.com")
                .get()
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .build()
            client.newCall(request).execute()
            
            android.util.Log.d("NeteaseApi", "Default cookie set: $cookies")
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "ensureCookie error: ${e.message}")
        }
    }

    private fun createSecretKey(size: Int): String {
        val chars = "012345679abcdef".toList()
        return buildString {
            repeat(size) {
                append(chars[kotlin.random.Random.nextInt(chars.size)])
            }
        }
    }

    suspend fun getRecommendPlaylists(): Result<List<RecommendPlaylist>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/personalized/playlist"
            val reqData = """{"limit":30,"total":true,"n":1000}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val formBody = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookies)
                .build()

            android.util.Log.d("NeteaseApi", "getRecommendPlaylists - Cookie: $cookies")

            val response = client.newCall(request).execute()
            android.util.Log.d("NeteaseApi", "getRecommendPlaylists - Response code: ${response.code}")
            
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "getRecommendPlaylists - Response body length: ${responseBody?.length}")
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response body"))
            }

            val json = try {
                gson.fromJson(responseBody, JsonObject::class.java)
            } catch (e: Exception) {
                return@withContext Result.failure(Exception("JSON parse error: ${e.message}"))
            }

            if (json == null) {
                return@withContext Result.failure(Exception("Parsed JSON is null"))
            }

            if (!json.has("result")) {
                val code = if (json.has("code")) json.get("code").asInt else -1
                android.util.Log.e("NeteaseApi", "No result field, code: $code, response: $responseBody")
                return@withContext Result.failure(Exception("No result field, code: $code"))
            }

            val resultArray = json.getAsJsonArray("result")
            val playlists = mutableListOf<RecommendPlaylist>()
            for (i in 0 until resultArray.size()) {
                try {
                    val obj = resultArray.get(i).asJsonObject
                    playlists.add(
                        RecommendPlaylist(
                            id = "neplaylist_${obj.get("id").asLong}",
                            title = obj.get("name").asString,
                            coverImgUrl = obj.get("picUrl").asString,
                            playCount = if (obj.has("playCount")) obj.get("playCount").asLong else 0,
                            description = if (obj.has("description")) obj.get("description").asString else ""
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            Result.success(playlists)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getRecommendPlaylists exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTopPlaylists(limit: Int = 35): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/toplist/detail"
            val reqData = "{}"
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("list")) {
                return@withContext Result.failure(Exception("No list field in response"))
            }

            val listArray = json.getAsJsonArray("list")
            val playlists = mutableListOf<Playlist>()
            val count = minOf(limit, listArray.size())
            for (i in 0 until count) {
                val obj = listArray.get(i).asJsonObject
                playlists.add(
                    Playlist(
                        id = "neplaylist_${obj.get("id").asLong}",
                        title = obj.get("name").asString,
                        coverImgUrl = obj.get("coverImgUrl").asString,
                        sourceUrl = "https://music.163.com/#/playlist?id=${obj.get("id").asLong}"
                    )
                )
            }
            Result.success(playlists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getPlaylistDetail(playlistId: String): Result<PlaylistDetail> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = playlistId.removePrefix("neplaylist_")
            val url = "https://music.163.com/weapi/v3/playlist/detail"
            val reqData = """{"id":$id,"offset":0,"total":true,"limit":1000,"n":1000,"csrf_token":""}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            android.util.Log.d("NeteaseApi", "Request URL: $url")
            android.util.Log.d("NeteaseApi", "Request Cookie: $cookies")

            val response = client.newCall(request).execute()
            android.util.Log.d("NeteaseApi", "Response code: ${response.code}")
            
            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                android.util.Log.e("NeteaseApi", "Error body: $errorBody")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "Response body length: ${responseBody?.length}")
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("playlist")) {
                android.util.Log.e("NeteaseApi", "Response: $responseBody")
                return@withContext Result.failure(Exception("Invalid response: no playlist field"))
            }

            val playlist = json.getAsJsonObject("playlist")
            val info = Playlist(
                id = "neplaylist_$id",
                title = playlist.get("name").asString,
                coverImgUrl = playlist.get("coverImgUrl").asString,
                sourceUrl = "https://music.163.com/#/playlist?id=$id"
            )

            val trackIdsArray = playlist.getAsJsonArray("trackIds")
            val trackIds = mutableListOf<Long>()
            for (i in 0 until trackIdsArray.size()) {
                trackIds.add(trackIdsArray.get(i).asJsonObject.get("id").asLong)
            }

            val allTracks = mutableListOf<Track>()
            val batchSize = 1000
            for (i in 0 until trackIds.size step batchSize) {
                val batch = trackIds.subList(i, minOf(i + batchSize, trackIds.size))
                val result = getTracksByIds(batch)
                result.onSuccess { tracks ->
                    allTracks.addAll(tracks)
                }
                result.onFailure { e ->
                    android.util.Log.e("NeteaseApi", "Failed to get tracks: ${e.message}")
                    if (allTracks.isEmpty()) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            Result.success(PlaylistDetail(info = info, tracks = allTracks))
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "Exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getTracksByIds(trackIds: List<Long>): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/v3/song/detail"

            val cArray = trackIds.joinToString(",") { """{"id":$it}""" }
            val cValue = "[$cArray]"
            val idsValue = "[${trackIds.joinToString(",")}]"

            val reqJson = JsonObject().apply {
                addProperty("c", cValue)
                addProperty("ids", idsValue)
            }
            val reqData = gson.toJson(reqJson)

            val encrypted = NeteaseCrypto.weapi(reqData)

            val formBody = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()

            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("songs")) {
                return@withContext Result.failure(Exception("No songs in response"))
            }

            val songsArray = json.getAsJsonArray("songs")
            val tracks = mutableListOf<Track>()
            for (i in 0 until songsArray.size()) {
                val song = songsArray.get(i).asJsonObject
                val artists = song.getAsJsonArray("ar")
                val artistName = if (artists.size() > 0) artists.get(0).asJsonObject.get("name").asString else "Unknown"
                val artistId = if (artists.size() > 0) artists.get(0).asJsonObject.get("id").asLong else 0L
                val album = song.getAsJsonObject("al")

                tracks.add(
                    Track(
                        id = "netrack_${song.get("id").asLong}",
                        title = song.get("name").asString,
                        artist = artistName,
                        artistId = "neartist_$artistId",
                        album = album.get("name").asString,
                        albumId = "nealbum_${album.get("id").asLong}",
                        sourceUrl = "https://music.163.com/#/song?id=${song.get("id").asLong}",
                        imgUrl = album.get("picUrl").asString,
                        duration = if (song.has("dt")) song.get("dt").asLong else 0L
                    )
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getTracksByIds exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSongUrl(songId: String): Result<Triple<String, Int, String>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val url = "https://interface3.music.163.com/eapi/song/enhance/player/url"
            val eapiUrl = "/api/song/enhance/player/url"
            val reqData = """{"ids":"[$id]","br":999000}"""
            val encrypted = NeteaseCrypto.eapi(eapiUrl, reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .build()

            val cookieStr = "os=pc; ${cookies}"

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookieStr)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            android.util.Log.d("NeteaseApi", "getSongUrl - Request Cookie: $cookieStr")

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "getSongUrl - Response: $responseBody")

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("data")) {
                return@withContext Result.failure(Exception("No data field in response"))
            }

            val data = json.getAsJsonArray("data")
            if (data.size() > 0) {
                val songData = data.get(0).asJsonObject
                val songUrl = if (songData.has("url") && !songData.get("url").isJsonNull) {
                    songData.get("url").asString
                } else {
                    return@withContext Result.failure(Exception("Song not available"))
                }
                val bitrate = if (songData.has("br")) songData.get("br").asInt else 128000
                val type = if (songData.has("type") && !songData.get("type").isJsonNull) {
                    songData.get("type").asString.lowercase()
                } else {
                    // 从URL推断格式
                    when {
                        songUrl.contains(".flac") -> "flac"
                        songUrl.contains(".mp3") -> "mp3"
                        else -> "mp3"
                    }
                }
                android.util.Log.d("NeteaseApi", "getSongUrl - URL: $songUrl, bitrate: $bitrate, type: $type")
                Result.success(Triple(songUrl, bitrate, type))
            } else {
                Result.failure(Exception("No data"))
            }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getSongUrl exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun searchSongs(keyword: String, page: Int = 1): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/search/pc"
            val offset = 20 * (page - 1)

            val body = FormBody.Builder()
                .add("s", keyword)
                .add("offset", offset.toString())
                .add("limit", "20")
                .add("type", "1")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("result")) {
                return@withContext Result.failure(Exception("No result field in response"))
            }

            val result = json.getAsJsonObject("result")
            if (!result.has("songs")) {
                return@withContext Result.failure(Exception("No songs field in result"))
            }

            val songsArray = result.getAsJsonArray("songs")
            val tracks = mutableListOf<Track>()
            for (i in 0 until songsArray.size()) {
                val song = songsArray.get(i).asJsonObject
                val artists = song.getAsJsonArray("artists")
                val artistName = if (artists.size() > 0) artists.get(0).asJsonObject.get("name").asString else "Unknown"
                val artistId = if (artists.size() > 0) artists.get(0).asJsonObject.get("id").asLong else 0L
                val album = song.getAsJsonObject("album")

                tracks.add(
                    Track(
                        id = "netrack_${song.get("id").asLong}",
                        title = song.get("name").asString,
                        artist = artistName,
                        artistId = "neartist_$artistId",
                        album = album.get("name").asString,
                        albumId = "nealbum_${album.get("id").asLong}",
                        sourceUrl = "https://music.163.com/#/song?id=${song.get("id").asLong}",
                        imgUrl = album.get("picUrl").asString,
                        duration = if (song.has("duration")) song.get("duration").asLong else 0L,
                        isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false
                    )
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // 二维码登录相关API
    suspend fun getLoginQrKey(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/weapi/login/qrcode/unikey"
            val reqData = """{"type":1}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (json.has("unikey")) {
                Result.success(json.get("unikey").asString)
            } else {
                Result.failure(Exception("No unikey in response"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getQrCodeUrl(key: String): String {
        return "https://music.163.com/login?codekey=$key"
    }

    suspend fun checkQrLoginStatus(key: String): Result<Int> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            // 使用weapi
            val url = "https://music.163.com/weapi/login/qrcode/client/login"
            val reqData = """{"key":"$key","type":1}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val cookieStr = buildString {
                if (cookies.isNotEmpty()) append(cookies)
                if (isNotEmpty()) append("; ")
                append("os=pc; appver=2.9.7; osver=13; channel=netease; deviceId=0")
            }
            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com/")
                .header("User-Agent", "NeteaseMusic/2.9.7 (Linux; Android 13; Pixel 7 Pro)")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Cookie", cookieStr)
                .header("X-Real-IP", "192.168.1.1")
                .header("X-Forwarded-For", "192.168.1.1")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "checkQrLoginStatus response: $responseBody")

            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val code = if (json.has("code")) json.get("code").asInt else -1
            android.util.Log.d("NeteaseApi", "checkQrLoginStatus code: $code, json: $json")

            // 800: 二维码过期, 801: 等待扫码, 802: 待确认, 803: 登录成功
            if (code == 803) {
                // 从响应头中获取cookie
                val responseCookies = response.headers("Set-Cookie")
                android.util.Log.d("NeteaseApi", "Login success, cookies: $responseCookies")
                
                // 构建cookie字符串
                val cookieBuilder = StringBuilder()
                for (cookie in responseCookies) {
                    // 提取cookie的name=value部分
                    val cookieParts = cookie.split(";")[0]
                    if (cookieBuilder.isNotEmpty()) {
                        cookieBuilder.append("; ")
                    }
                    cookieBuilder.append(cookieParts)
                }
                
                if (cookieBuilder.isNotEmpty()) {
                    setCookie(cookieBuilder.toString())
                    android.util.Log.d("NeteaseApi", "Saved cookies: $cookieBuilder")
                } else {
                    // 如果没有从响应头获取到cookie，尝试从JSON中获取
                    if (json.has("cookie")) {
                        setCookie(json.get("cookie").asString)
                        android.util.Log.d("NeteaseApi", "Saved cookies from JSON: ${json.get("cookie").asString}")
                    }
                }
            }
            
            Result.success(code)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "checkQrLoginStatus error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getUserInfo(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/api/nuser/account/get"
            val encrypted = NeteaseCrypto.weapi("{}")

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (json.has("profile") && !json.get("profile").isJsonNull) {
                val profile = json.getAsJsonObject("profile")
                val nickname = if (profile.has("nickname")) profile.get("nickname").asString else ""
                Result.success(nickname)
            } else {
                Result.success("")
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/api/nuser/account/get"
            val encrypted = NeteaseCrypto.weapi("{}")

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "getUserProfile response: $responseBody")
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (json.has("profile") && !json.get("profile").isJsonNull) {
                val profile = json.getAsJsonObject("profile")
                android.util.Log.d("NeteaseApi", "Parsing profile: ${profile.toString().take(200)}")
                val userProfile = UserProfile(
                    userId = if (profile.has("userId") && !profile.get("userId").isJsonNull) profile.get("userId").asLong else 0,
                    nickname = if (profile.has("nickname") && !profile.get("nickname").isJsonNull) profile.get("nickname").asString else "",
                    avatarUrl = if (profile.has("avatarUrl") && !profile.get("avatarUrl").isJsonNull) profile.get("avatarUrl").asString else "",
                    gender = if (profile.has("gender") && !profile.get("gender").isJsonNull) profile.get("gender").asInt else 0,
                    birthday = if (profile.has("birthday") && !profile.get("birthday").isJsonNull) profile.get("birthday").asLong else 0,
                    province = if (profile.has("province") && !profile.get("province").isJsonNull) profile.get("province").asInt else 0,
                    city = if (profile.has("city") && !profile.get("city").isJsonNull) profile.get("city").asInt else 0,
                    signature = if (profile.has("signature") && !profile.get("signature").isJsonNull) profile.get("signature").asString else "",
                    description = if (profile.has("description") && !profile.get("description").isJsonNull) profile.get("description").asString else "",
                    detailDescription = if (profile.has("detailDescription") && !profile.get("detailDescription").isJsonNull) profile.get("detailDescription").asString else "",
                    backgroundUrl = if (profile.has("backgroundUrl") && !profile.get("backgroundUrl").isJsonNull) profile.get("backgroundUrl").asString else "",
                    vipType = if (profile.has("vipType") && !profile.get("vipType").isJsonNull) profile.get("vipType").asInt else 0,
                    authStatus = if (profile.has("authStatus") && !profile.get("authStatus").isJsonNull) profile.get("authStatus").asInt else 0,
                    createTime = if (profile.has("createTime") && !profile.get("createTime").isJsonNull) profile.get("createTime").asLong else 0
                )
                android.util.Log.d("NeteaseApi", "UserProfile created: nickname=${userProfile.nickname}, userId=${userProfile.userId}, createTime=${userProfile.createTime}")
                // 保存到缓存
                saveUserProfileToCache(userProfile)
                Result.success(userProfile)
            } else {
                Result.failure(Exception("No profile found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun saveUserProfileToCache(profile: UserProfile) {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().apply {
                putLong("profile_userId", profile.userId)
                putString("profile_nickname", profile.nickname)
                putString("profile_avatarUrl", profile.avatarUrl)
                putInt("profile_gender", profile.gender)
                putLong("profile_birthday", profile.birthday)
                putInt("profile_province", profile.province)
                putInt("profile_city", profile.city)
                putString("profile_signature", profile.signature)
                putString("profile_description", profile.description)
                putString("profile_detailDescription", profile.detailDescription)
                putString("profile_backgroundUrl", profile.backgroundUrl)
                putInt("profile_vipType", profile.vipType)
                putInt("profile_authStatus", profile.authStatus)
                putLong("profile_createTime", profile.createTime)
                apply()
            }
            android.util.Log.d("NeteaseApi", "UserProfile saved to cache")
        }
    }

    fun getUserProfileFromCache(): UserProfile? {
        return context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            val userId = prefs.getLong("profile_userId", 0)
            if (userId == 0L) return null
            UserProfile(
                userId = userId,
                nickname = prefs.getString("profile_nickname", "") ?: "",
                avatarUrl = prefs.getString("profile_avatarUrl", "") ?: "",
                gender = prefs.getInt("profile_gender", 0),
                birthday = prefs.getLong("profile_birthday", 0),
                province = prefs.getInt("profile_province", 0),
                city = prefs.getInt("profile_city", 0),
                signature = prefs.getString("profile_signature", "") ?: "",
                description = prefs.getString("profile_description", "") ?: "",
                detailDescription = prefs.getString("profile_detailDescription", "") ?: "",
                backgroundUrl = prefs.getString("profile_backgroundUrl", "") ?: "",
                vipType = prefs.getInt("profile_vipType", 0),
                authStatus = prefs.getInt("profile_authStatus", 0),
                createTime = prefs.getLong("profile_createTime", 0)
            )
        }
    }

    private fun clearUserProfileCache() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove("profile_userId")
                remove("profile_nickname")
                remove("profile_avatarUrl")
                remove("profile_gender")
                remove("profile_birthday")
                remove("profile_province")
                remove("profile_city")
                remove("profile_signature")
                remove("profile_description")
                remove("profile_detailDescription")
                remove("profile_backgroundUrl")
                remove("profile_vipType")
                remove("profile_authStatus")
                remove("profile_createTime")
                apply()
            }
            android.util.Log.d("NeteaseApi", "User profile cache cleared")
        }
    }

    suspend fun logout() {
        cookies = ""
        userId = null
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().apply {
                remove("cookies")
                // 清除用户资料缓存
                remove("profile_userId")
                remove("profile_nickname")
                remove("profile_avatarUrl")
                remove("profile_gender")
                remove("profile_birthday")
                remove("profile_province")
                remove("profile_city")
                remove("profile_signature")
                remove("profile_description")
                remove("profile_detailDescription")
                remove("profile_backgroundUrl")
                remove("profile_vipType")
                remove("profile_authStatus")
                remove("profile_createTime")
                apply()
            }
        }
    }

    suspend fun getUserId(): Long? = withContext(Dispatchers.IO) {
        try {
            val url = "https://music.163.com/api/nuser/account/get"
            val encrypted = NeteaseCrypto.weapi("{}")

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("NeteaseApi", "getUserId failed: HTTP ${response.code}")
                return@withContext null
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "getUserId response: $responseBody")
            if (responseBody.isNullOrEmpty()) {
                return@withContext null
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java) ?: return@withContext null

            if (json.has("account") && !json.get("account").isJsonNull) {
                val account = json.getAsJsonObject("account")
                val userId = if (account.has("id")) account.get("id").asLong else null
                android.util.Log.d("NeteaseApi", "getUserId result: $userId")
                userId
            } else {
                android.util.Log.d("NeteaseApi", "getUserId: no account field")
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun getCurrentUserId(): Long? = userId

    suspend fun getUserPlaylists(userId: Long): Result<List<RecommendPlaylist>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/user/playlist"

            android.util.Log.d("NeteaseApi", "getUserPlaylists - userId: $userId")
            android.util.Log.d("NeteaseApi", "getUserPlaylists - url: $url")
            android.util.Log.d("NeteaseApi", "getUserPlaylists - cookies: $cookies")

            val body = FormBody.Builder()
                .add("uid", userId.toString())
                .add("limit", "1000")
                .add("offset", "0")
                .add("includeVideo", "true")
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d("NeteaseApi", "getUserPlaylists - response code: ${response.code}")
            
            if (!response.isSuccessful) {
                android.util.Log.e("NeteaseApi", "getUserPlaylists - HTTP ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "getUserPlaylists - response length: ${responseBody?.length}")
            android.util.Log.d("NeteaseApi", "getUserPlaylists - response body: $responseBody")
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("playlist")) {
                android.util.Log.e("NeteaseApi", "getUserPlaylists - No playlist field")
                return@withContext Result.failure(Exception("No playlist field in response"))
            }

            val playlistArray = json.getAsJsonArray("playlist")
            android.util.Log.d("NeteaseApi", "getUserPlaylists - playlist count: ${playlistArray.size()}")
            val playlists = mutableListOf<RecommendPlaylist>()
            for (i in 0 until playlistArray.size()) {
                try {
                    val obj = playlistArray.get(i).asJsonObject
                    playlists.add(
                        RecommendPlaylist(
                            id = "neplaylist_${obj.get("id").asLong}",
                            title = obj.get("name").asString,
                            coverImgUrl = if (obj.has("coverImgUrl")) obj.get("coverImgUrl").asString else "",
                            playCount = if (obj.has("playCount")) obj.get("playCount").asLong else 0,
                            trackCount = if (obj.has("trackCount")) obj.get("trackCount").asInt else 0,
                            description = if (obj.has("description") && !obj.get("description").isJsonNull) obj.get("description").asString else ""
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            android.util.Log.d("NeteaseApi", "getUserPlaylists - parsed ${playlists.size} playlists")
            Result.success(playlists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getDailyRecommendSongs(): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/v3/discovery/recommend/songs"
            val reqData = """{"offset":0,"total":true,"limit":30}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (!json.has("data")) {
                return@withContext Result.failure(Exception("No data field in response"))
            }

            val data = json.getAsJsonObject("data")
            if (!data.has("dailySongs")) {
                return@withContext Result.failure(Exception("No dailySongs field"))
            }

            val songsArray = data.getAsJsonArray("dailySongs")
            val tracks = mutableListOf<Track>()
            for (i in 0 until songsArray.size()) {
                try {
                    val song = songsArray.get(i).asJsonObject
                    val artists = song.getAsJsonArray("ar")
                    val artistName = if (artists.size() > 0) artists.get(0).asJsonObject.get("name").asString else "Unknown"
                    val artistId = if (artists.size() > 0) artists.get(0).asJsonObject.get("id").asLong else 0L
                    val album = song.getAsJsonObject("al")

                    tracks.add(
                        Track(
                            id = "netrack_${song.get("id").asLong}",
                            title = song.get("name").asString,
                            artist = artistName,
                            artistId = "neartist_$artistId",
                            album = album.get("name").asString,
                            albumId = "nealbum_${album.get("id").asLong}",
                            sourceUrl = "https://music.163.com/#/song?id=${song.get("id").asLong}",
                            imgUrl = album.get("picUrl").asString,
                            duration = if (song.has("dt")) song.get("dt").asLong else 0L,
                            isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            Result.success(tracks)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    data class CloudDiskResult(
        val tracks: List<Track>,
        val hasMore: Boolean
    )

    suspend fun getCloudDiskSongs(offset: Int = 0, limit: Int = 100): Result<CloudDiskResult> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/v1/cloud/get"
            val reqData = """{"offset":$offset,"limit":$limit}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            android.util.Log.d("NeteaseApi", "getCloudDiskSongs - offset: $offset, limit: $limit")

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val hasMore = if (json.has("hasMore")) json.get("hasMore").asBoolean else false
            android.util.Log.d("NeteaseApi", "getCloudDiskSongs - hasMore: $hasMore")

            if (!json.has("data")) {
                return@withContext Result.failure(Exception("No data field in response"))
            }

            val data = json.getAsJsonArray("data")
            android.util.Log.d("NeteaseApi", "getCloudDiskSongs - received ${data.size()} items")

            val tracks = mutableListOf<Track>()
            for (i in 0 until data.size()) {
                try {
                    val item = data.get(i).asJsonObject
                    val song = if (item.has("simpleSong")) item.getAsJsonObject("simpleSong") else item
                    
                    val songId = if (song.has("id")) song.get("id").asLong else 0L
                    val songName = if (song.has("name") && !song.get("name").isJsonNull) song.get("name").asString else ""
                    
                    var artistName = "Unknown"
                    if (song.has("ar") && song.get("ar").isJsonArray) {
                        val artists = song.getAsJsonArray("ar")
                        if (artists.size() > 0) {
                            val artist = artists.get(0).asJsonObject
                            if (artist.has("name") && !artist.get("name").isJsonNull) {
                                artistName = artist.get("name").asString
                            }
                        }
                    } else if (song.has("artist") && !song.get("artist").isJsonNull) {
                        artistName = song.get("artist").asString
                    }
                    
                    var albumName = ""
                    if (song.has("al") && song.get("al").isJsonObject) {
                        val album = song.getAsJsonObject("al")
                        if (album.has("name") && !album.get("name").isJsonNull) {
                            albumName = album.get("name").asString
                        }
                    } else if (song.has("album") && !song.get("album").isJsonNull) {
                        albumName = song.get("album").asString
                    }
                    
                    val duration = if (song.has("dt")) song.get("dt").asLong else 0L
                    
                    tracks.add(
                        Track(
                            id = "netrack_$songId",
                            title = songName,
                            artist = artistName,
                            artistId = "neartist_0",
                            album = albumName,
                            albumId = "nealbum_0",
                            sourceUrl = "https://music.163.com/#/song?id=$songId",
                            imgUrl = "",
                            duration = duration,
                            isVip = false
                        )
                    )
                } catch (e: Exception) {
                    android.util.Log.e("NeteaseApi", "getCloudDiskSongs - parse item error: ${e.message}")
                }
            }
            android.util.Log.d("NeteaseApi", "getCloudDiskSongs - loaded ${tracks.size} tracks, hasMore: $hasMore")
            Result.success(CloudDiskResult(tracks, hasMore))
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getCloudDiskSongs error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun likeSong(songId: String, like: Boolean = true): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_").toLongOrNull() ?: 0L
            // 使用 weapi 接口，和 NeriPlayer 一致
            val url = "https://music.163.com/weapi/song/like"
            val reqData = """{"trackId":$id,"like":$like}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            android.util.Log.d("NeteaseApi", "likeSong - songId: $songId, id: $id, like: $like")

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; CloudMusicMiuix) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "*/*")
                .header("Accept-Language", "zh-CN,zh-Hans;q=0.9")
                .header("Connection", "keep-alive")
                .build()

            val response = client.newCall(request).execute()
            android.util.Log.d("NeteaseApi", "likeSong - response code: ${response.code}")
            
            if (!response.isSuccessful) {
                android.util.Log.e("NeteaseApi", "likeSong - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            android.util.Log.d("NeteaseApi", "likeSong - response body: $responseBody")
            
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val code = if (json.has("code")) json.get("code").asInt else -1
            android.util.Log.d("NeteaseApi", "likeSong - response code: $code")
            
            Result.success(code == 200)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "likeSong error: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun checkSongLiked(songId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            // 使用获取用户喜欢的歌曲列表API
            val userId = getUserId() ?: return@withContext Result.failure(Exception("Not logged in"))
            val url = "https://music.163.com/api/song/like/get"
            val reqData = """{"uid":$userId}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("X-Real-IP", "118.88.88.88")
                .header("X-Forwarded-For", "118.88.88.88")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val ids = if (json.has("ids") && json.get("ids").isJsonArray) {
                json.getAsJsonArray("ids").map { it.asLong }
            } else {
                emptyList()
            }
            
            val isLiked = ids.contains(id.toLong())
            android.util.Log.d("NeteaseApi", "checkSongLiked - songId: $id, isLiked: $isLiked")
            Result.success(isLiked)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "checkSongLiked error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getSongLyric(songId: String, songTitle: String = "", songArtist: String = "", songDuration: Long = 0): Result<SongLyrics> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val url = "https://music.163.com/weapi/song/lyric?csrf_token="
            val reqData = """{"id":$id,"lv":-1,"tv":-1,"csrf_token":""}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            android.util.Log.d("NeteaseApi", "getSongLyric - songId: $songId, id: $id")

            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                android.util.Log.e("NeteaseApi", "getSongLyric - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                android.util.Log.e("NeteaseApi", "getSongLyric - Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }

            android.util.Log.d("NeteaseApi", "getSongLyric - response length: ${responseBody.length}")
            android.util.Log.d("NeteaseApi", "getSongLyric - response body: ${responseBody.take(500)}")

            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val lrcLyric = if (json.has("lrc") && !json.get("lrc").isJsonNull) {
                val lrc = json.getAsJsonObject("lrc")
                if (lrc.has("lyric") && !lrc.get("lyric").isJsonNull) lrc.get("lyric").asString else ""
            } else ""

            val tLyric = if (json.has("tlyric") && !json.get("tlyric").isJsonNull) {
                val tlyric = json.getAsJsonObject("tlyric")
                if (tlyric.has("lyric") && !tlyric.get("lyric").isJsonNull)
                    tlyric.get("lyric").asString else ""
            } else ""

            // 获取YRC逐字歌词
            val yrcLyric = if (json.has("yrc") && !json.get("yrc").isJsonNull) {
                val yrc = json.getAsJsonObject("yrc")
                if (yrc.has("lyric") && !yrc.get("lyric").isJsonNull) yrc.get("lyric").asString else ""
            } else ""

            android.util.Log.d("NeteaseApi", "getSongLyric - lrc length: ${lrcLyric.length}")
            android.util.Log.d("NeteaseApi", "getSongLyric - tlyric length: ${tLyric.length}")
            android.util.Log.d("NeteaseApi", "getSongLyric - yrc length: ${yrcLyric.length}")

            // 优先使用YRC逐字歌词，否则使用LRC
            val lyrics = if (yrcLyric.isNotEmpty()) {
                parseYrc(yrcLyric, tLyric)
            } else {
                parseLrc(lrcLyric, tLyric)
            }
            
            // 判断是否为纯音乐：
            // 1. 歌词为空
            // 2. 歌词小于3行且包含"纯音乐"关键词
            val isPureMusic = lyrics.lines.isEmpty() || 
                (lyrics.lines.size < 3 && lyrics.lines.any { 
                    it.text.contains("纯音乐") || it.text.contains("纯音乐，请欣赏") 
                })
            
            val result = if (isPureMusic) {
                SongLyrics(lines = lyrics.lines, isPureMusic = true)
            } else if (songTitle.isNotEmpty() && songArtist.isNotEmpty() && lyrics.lines.isNotEmpty()) {
                // 非纯音乐，在歌词开头添加"歌名 - 歌手"
                val titleLine = LyricLine(
                    text = "$songTitle - $songArtist",
                    translation = "",
                    startAt = 0L
                )
                SongLyrics(lines = listOf(titleLine) + lyrics.lines, isPureMusic = false)
            } else {
                lyrics
            }
            
            android.util.Log.d("NeteaseApi", "getSongLyric - parsed ${result.lines.size} lyric lines, isPureMusic: ${result.isPureMusic}")
            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getSongLyric error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseLrc(lrc: String, tlyric: String): SongLyrics {
        val regex = Regex("""\[(\d+):(\d+\.\d+)](.*)""")
        val tlyricMap = mutableMapOf<Long, String>()
        
        // 解析翻译歌词
        if (tlyric.isNotEmpty()) {
            for (line in tlyric.split("\n")) {
                val match = regex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = (match.groupValues[2].toDouble() * 1000).toLong()
                    val time = min * 60 * 1000 + sec
                    val text = match.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        tlyricMap[time] = text
                    }
                }
            }
        }

        // 解析原文歌词
        val lines = mutableListOf<LyricLine>()
        var foundFirstNonColonLine = false
        
        for (line in lrc.split("\n")) {
            val match = regex.find(line)
            if (match != null) {
                val min = match.groupValues[1].toLong()
                val sec = (match.groupValues[2].toDouble() * 1000).toLong()
                val time = min * 60 * 1000 + sec
                val text = match.groupValues[3].trim()
                
                if (text.isNotEmpty()) {
                    // 跳过开头连续的冒号行（如"作词:xxx"、"作曲:xxx"）
                    if (!foundFirstNonColonLine) {
                        if (text.contains(":") || text.contains("：")) {
                            continue // 跳过开头的冒号行
                        } else {
                            foundFirstNonColonLine = true // 找到第一个非冒号行
                        }
                    }
                    
                    val translation = tlyricMap[time] ?: ""
                    lines.add(LyricLine(text = text, translation = translation, startAt = time))
                }
            }
        }

        // 过滤末尾的"歌曲制作团队"信息
        val filteredLines = filterTailCredits(lines)

        return SongLyrics(lines = filteredLines.sortedBy { it.startAt })
    }

    /**
     * 解析YRC逐字歌词格式
     * 格式示例: [12580,3470](12580,250,0)难(12830,300,0)忘
     */
    private fun parseYrc(yrc: String, tlyric: String): SongLyrics {
        val headerRegex = Regex("""\[(\d{1,19}),\s*(\d{1,19})]""")
        val segRegex = Regex("""\((\d{1,19}),\s*(\d{1,19}),\s*[-\d]{1,20}\)([^()\n\r]*)""")
        val tlyricRegex = Regex("""\[(\d+):(\d+\.\d+)](.*)""")
        
        // 解析翻译歌词
        val tlyricMap = mutableMapOf<Long, String>()
        if (tlyric.isNotEmpty()) {
            for (line in tlyric.split("\n")) {
                val match = tlyricRegex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = (match.groupValues[2].toDouble() * 1000).toLong()
                    val time = min * 60 * 1000 + sec
                    val text = match.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        tlyricMap[time] = text
                    }
                }
            }
        }

        val lines = mutableListOf<LyricLine>()
        var foundFirstNonColonLine = false

        for (line in yrc.split("\n")) {
            val header = headerRegex.find(line) ?: continue
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val end = start + dur

            val segs = segRegex.findAll(line).toList()
            val words = mutableListOf<com.cloudmusic.miuix.data.model.LyricWord>()
            val sb = StringBuilder()
            
            for (m in segs) {
                val ws = m.groupValues[1].toLong()
                val wd = m.groupValues[2].toLong()
                val t = m.groupValues[3]
                sb.append(t)
                words.add(com.cloudmusic.miuix.data.model.LyricWord(
                    text = t,
                    startAt = ws,
                    endAt = ws + wd
                ))
            }

            val text = sb.toString().trim()
            if (text.isNotEmpty()) {
                // 跳过开头连续的冒号行
                if (!foundFirstNonColonLine) {
                    if (text.contains(":") || text.contains("：")) {
                        continue
                    } else {
                        foundFirstNonColonLine = true
                    }
                }

                // 查找翻译（使用开始时间匹配）
                val translation = tlyricMap[start] ?: ""
                
                lines.add(LyricLine(
                    text = text,
                    translation = translation,
                    startAt = start,
                    endAt = end,
                    words = words.takeIf { it.isNotEmpty() }
                ))
            }
        }

        // 过滤末尾的"歌曲制作团队"信息
        val filteredLines = filterTailCredits(lines)

        return SongLyrics(lines = filteredLines.sortedBy { it.startAt })
    }

    /**
     * 过滤末尾的"歌曲制作团队"信息
     * 1. 当末尾歌词有冒号时，直接移除末尾这几句带有冒号的歌词
     * 2. 当末尾歌词没有冒号时，使用末尾的前一句歌词判断，
     *    但需要有>=2句相连的带有冒号的歌词才能判断为"歌曲制作团队"，
     *    连同这几句和最后一句都要删掉
     */
    private fun filterTailCredits(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        
        val result = lines.toMutableList()
        
        // 从末尾开始检查
        while (result.isNotEmpty()) {
            val lastLine = result.last()
            
            // 情况1：末尾歌词有冒号（中文或英文）
            if (lastLine.text.contains("：") || lastLine.text.contains(":")) {
                result.removeAt(result.size - 1)
                continue
            }
            
            // 情况2：末尾歌词没有冒号，检查前一句
            if (result.size >= 2) {
                val secondLast = result[result.size - 2]
                if (secondLast.text.contains("：") || secondLast.text.contains(":")) {
                    // 找到连续的冒号行
                    var colonCount = 0
                    for (i in result.size - 2 downTo 0) {
                        if (result[i].text.contains("：") || result[i].text.contains(":")) {
                            colonCount++
                        } else {
                            break
                        }
                    }
                    
                    // 如果有>=2句相连的带有冒号的歌词，删除这些和最后一句
                    if (colonCount >= 2) {
                        // 删除最后一句（无用词）
                        result.removeAt(result.size - 1)
                        // 删除连续的冒号行
                        for (i in 0 until colonCount) {
                            if (result.isNotEmpty()) {
                                result.removeAt(result.size - 1)
                            }
                        }
                        continue
                    }
                }
            }
            
            // 无法匹配，退出循环
            break
        }
        
        // 过滤开头包含"未经著作权人书面许可"的歌词
        while (result.isNotEmpty() && result.first().text.contains("未经著作权人书面许可")) {
            result.removeAt(0)
        }
        
        return result
    }
}
