package com.cloudmusic.miuix.data.api

import com.cloudmusic.miuix.data.model.Playlist
import com.cloudmusic.miuix.data.model.PlaylistDetail
import com.cloudmusic.miuix.data.model.RecommendPlaylist
import com.cloudmusic.miuix.data.model.Track
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonArray
import kotlinx.coroutines.Dispatchers
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

    fun init(appContext: android.content.Context) {
        context = appContext.applicationContext
        // Load saved cookies
        val prefs = context!!.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
        cookies = prefs.getString("cookies", "") ?: ""
        android.util.Log.d("NeteaseApi", "Init - Loaded cookies: $cookies")
    }

    fun setCookie(newCookies: String) {
        cookies = newCookies
        // Save to SharedPreferences
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookies).apply()
        }
        android.util.Log.d("NeteaseApi", "Cookie set and saved: $cookies")
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

    suspend fun getSongUrl(songId: String): Result<String> = withContext(Dispatchers.IO) {
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
                Result.success(songUrl)
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
}
