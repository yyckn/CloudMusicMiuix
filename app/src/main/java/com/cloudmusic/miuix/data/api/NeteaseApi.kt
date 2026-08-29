package com.cloudmusic.miuix.data.api

import com.cloudmusic.miuix.data.model.AlbumInfo
import com.cloudmusic.miuix.data.model.ArtistInfo
import com.cloudmusic.miuix.data.model.Comment
import com.cloudmusic.miuix.data.model.CommentResult
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
import android.util.Log
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
    
    // 多账号管理
    data class AccountData(
        val userId: String,
        val nickname: String,
        val avatarUrl: String,
        val cookies: String
    )
    private var accounts: MutableList<AccountData> = mutableListOf()

    fun setOnProfileChangedListener(listener: (() -> Unit)?) {
        onProfileChanged = listener
    }

    fun init(appContext: android.content.Context) {
        context = appContext.applicationContext
        // Load saved cookies
        val prefs = context!!.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
        cookies = prefs.getString("cookies", "") ?: ""
        // Load saved accounts
        loadAccounts()
        android.util.Log.d("NeteaseApi", "Init - Loaded cookies: $cookies")
        android.util.Log.d("NeteaseApi", "Init - Loaded ${accounts.size} accounts")
        // 获取用户ID
        if (cookies.isNotEmpty()) {
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                userId = getUserId()
                android.util.Log.d("NeteaseApi", "Init - User ID: $userId")
                // 如果已登录但账号列表中没有当前账号，自动保存
                if (userId != null && cookies.contains("MUSIC_U")) {
                    val currentUserId = userId.toString()
                    if (!accounts.any { it.userId == currentUserId }) {
                        saveCurrentAccount()
                    }
                }
            }
        }
    }
    
    private fun loadAccounts() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            val accountsJson = prefs.getString("accounts", "[]") ?: "[]"
            try {
                val jsonArray = com.google.gson.JsonParser.parseString(accountsJson).asJsonArray
                accounts.clear()
                val seenIds = mutableSetOf<String>()
                for (i in 0 until jsonArray.size()) {
                    val obj = jsonArray.get(i).asJsonObject
                    val userId = obj.get("userId").asString
                    // 去重：同一ID只保留一个
                    if (seenIds.contains(userId)) {
                        continue
                    }
                    seenIds.add(userId)
                    accounts.add(
                        AccountData(
                            userId = userId,
                            nickname = obj.get("nickname").asString,
                            avatarUrl = obj.get("avatarUrl").asString,
                            cookies = obj.get("cookies").asString
                        )
                    )
                }
                // 如果有重复，保存去重后的列表
                if (accounts.size < jsonArray.size()) {
                    saveAccounts()
                }
            } catch (e: Exception) {
                android.util.Log.e("NeteaseApi", "Error loading accounts: ${e.message}")
            }
        }
    }
    
    private fun saveAccounts() {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            val jsonArray = com.google.gson.JsonArray()
            for (account in accounts) {
                val obj = com.google.gson.JsonObject()
                obj.addProperty("userId", account.userId)
                obj.addProperty("nickname", account.nickname)
                obj.addProperty("avatarUrl", account.avatarUrl)
                obj.addProperty("cookies", account.cookies)
                jsonArray.add(obj)
            }
            prefs.edit().putString("accounts", jsonArray.toString()).apply()
        }
    }
    
    fun saveCurrentAccount() {
        // 保存当前账号到列表
        if (cookies.contains("MUSIC_U") && userId != null) {
            val currentUserId = userId.toString()
            // 检查是否已存在
            val existingIndex = accounts.indexOfFirst { it.userId == currentUserId }
            var nickname = getUserNicknameFromCache()
            var avatarUrl = getUserAvatarFromCache()
            
            // 如果缓存中没有用户资料，尝试从API获取
            if (nickname == null || nickname.isEmpty()) {
                kotlinx.coroutines.runBlocking {
                    try {
                        val result = getUserProfile()
                        result.onSuccess { profile ->
                            nickname = profile.nickname
                            avatarUrl = profile.avatarUrl
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("NeteaseApi", "Error getting user profile: ${e.message}")
                    }
                }
            }
            
            if (nickname == null || nickname.isEmpty()) {
                nickname = "用户$currentUserId"
            }
            if (avatarUrl == null) {
                avatarUrl = ""
            }
            
            val accountData = AccountData(currentUserId, nickname!!, avatarUrl!!, cookies)
            if (existingIndex >= 0) {
                accounts[existingIndex] = accountData
            } else {
                accounts.add(accountData)
            }
            saveAccounts()
            android.util.Log.d("NeteaseApi", "Saved account: $currentUserId, nickname: $nickname, total: ${accounts.size}")
        }
    }
    
    fun getAccounts(): List<AccountData> = accounts.toList()
    
    fun getAccountCookie(userId: String): String? {
        return accounts.find { it.userId == userId }?.cookies
    }
    
    fun switchToAccount(userId: String): Boolean {
        val account = accounts.find { it.userId == userId }
        if (account != null) {
            cookies = account.cookies
            this.userId = userId.toLongOrNull()
            // 保存当前cookies
            context?.let { ctx ->
                val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
                prefs.edit().putString("cookies", cookies).apply()
            }
            android.util.Log.d("NeteaseApi", "Switched to account: $userId")
            return true
        }
        return false
    }
    
    private fun getUserNicknameFromCache(): String? {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            return prefs.getString("profile_nickname", null)
        }
        return null
    }
    
    private fun getUserAvatarFromCache(): String? {
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            return prefs.getString("profile_avatarUrl", null)
        }
        return null
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
            // 保存当前账号到账号列表
            if (userId != null && cookies.contains("MUSIC_U")) {
                saveCurrentAccount()
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

    /**
     * 格式化歌手名称
     * - 4个及以上歌手显示为"群星"
     * - 否则用"/"分隔
     */
    private fun formatArtistNames(artists: List<String>): String {
        return when {
            artists.isEmpty() -> "Unknown"
            artists.size >= 4 -> "群星"
            else -> artists.joinToString("/")
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
                val artistNames = mutableListOf<String>()
                val artistIds = mutableListOf<String>()
                for (j in 0 until artists.size()) {
                    val artist = artists.get(j).asJsonObject
                    if (artist.has("name") && !artist.get("name").isJsonNull) {
                        artistNames.add(artist.get("name").asString)
                    }
                    if (artist.has("id")) {
                        artistIds.add("neartist_${artist.get("id").asLong}")
                    }
                }
                val artistName = formatArtistNames(artistNames)
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
                        artistNames = artistNames,
                        artistIds = artistIds
                    )
                )
            }
            Result.success(tracks)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getTracksByIds exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    suspend fun getSongUrl(songId: String, bitrate: Int = 999000): Result<Triple<String, Int, String>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val url = "https://interface3.music.163.com/eapi/song/enhance/player/url"
            val eapiUrl = "/api/song/enhance/player/url"
            val reqData = """{"ids":"[$id]","br":$bitrate}"""
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
                val artistNames = mutableListOf<String>()
                for (j in 0 until artists.size()) {
                    val artist = artists.get(j).asJsonObject
                    if (artist.has("name") && !artist.get("name").isJsonNull) {
                        artistNames.add(artist.get("name").asString)
                    }
                }
                val artistName = formatArtistNames(artistNames)
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
                        isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false,
                        artistNames = artistNames
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
                    val artistNames = mutableListOf<String>()
                    for (j in 0 until artists.size()) {
                        val artist = artists.get(j).asJsonObject
                        if (artist.has("name") && !artist.get("name").isJsonNull) {
                            artistNames.add(artist.get("name").asString)
                        }
                    }
                    val artistName = formatArtistNames(artistNames)
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
                            isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false,
                            artistNames = artistNames
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

    suspend fun getCloudDiskSongs(offset: Int = 0, limit: Int = 10): Result<CloudDiskResult> = withContext(Dispatchers.IO) {
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

            val count = if (json.has("count")) json.get("count").asInt else 0
            val hasMore = if (json.has("hasMore")) json.get("hasMore").asBoolean else offset + limit < count
            android.util.Log.d("NeteaseApi", "getCloudDiskSongs - hasMore: $hasMore, count: $count, offset: $offset, limit: $limit")

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
                    var artistNames = mutableListOf<String>()
                    var artistId = 0L
                    if (song.has("ar") && song.get("ar").isJsonArray) {
                        val artists = song.getAsJsonArray("ar")
                        for (j in 0 until artists.size()) {
                            val artist = artists.get(j).asJsonObject
                            if (artist.has("name") && !artist.get("name").isJsonNull) {
                                artistNames.add(artist.get("name").asString)
                            }
                            if (j == 0 && artist.has("id")) {
                                artistId = artist.get("id").asLong
                            }
                        }
                        artistName = formatArtistNames(artistNames)
                    } else if (song.has("artist") && !song.get("artist").isJsonNull) {
                        artistName = song.get("artist").asString
                    }
                    
                    var albumName = ""
                    var albumId = 0L
                    var picUrl = ""
                    if (song.has("al") && song.get("al").isJsonObject) {
                        val album = song.getAsJsonObject("al")
                        if (album.has("name") && !album.get("name").isJsonNull) {
                            albumName = album.get("name").asString
                        }
                        if (album.has("id")) {
                            albumId = album.get("id").asLong
                        }
                        if (album.has("picUrl") && !album.get("picUrl").isJsonNull) {
                            picUrl = album.get("picUrl").asString
                        }
                    } else if (song.has("album") && song.get("album").isJsonObject) {
                        val album = song.getAsJsonObject("album")
                        if (album.has("name") && !album.get("name").isJsonNull) {
                            albumName = album.get("name").asString
                        }
                        if (album.has("id")) {
                            albumId = album.get("id").asLong
                        }
                        if (album.has("picUrl") && !album.get("picUrl").isJsonNull) {
                            picUrl = album.get("picUrl").asString
                        }
                    }
                    
                    val duration = if (song.has("dt")) song.get("dt").asLong else 0L
                    
                    tracks.add(
                        Track(
                            id = "netrack_$songId",
                            title = songName,
                            artist = artistName,
                            artistId = "neartist_$artistId",
                            album = albumName,
                            albumId = "nealbum_$albumId",
                            sourceUrl = "https://music.163.com/#/song?id=$songId",
                            imgUrl = picUrl,
                            duration = duration,
                            isVip = false,
                            artistNames = artistNames,
                            artistIds = artistNames.mapIndexed { index, _ -> 
                                if (index == 0) "neartist_$artistId" else "neartist_0"
                            }
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

    suspend fun getSongLyric(songId: String, songTitle: String = "", songArtist: String = "", songDuration: Long = 0, artistNames: List<String> = emptyList()): Result<SongLyrics> = withContext(Dispatchers.IO) {
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
            // 使用传入的artistNames，如果没有则从songArtist分割
            val effectiveArtistNames = if (artistNames.isNotEmpty()) {
                artistNames
            } else {
                songArtist.split("/", "、", " ").map { it.trim() }.filter { it.isNotEmpty() }
            }
            val lyrics = if (yrcLyric.isNotEmpty()) {
                parseYrc(yrcLyric, tLyric, effectiveArtistNames)
            } else {
                parseLrc(lrcLyric, tLyric, effectiveArtistNames)
            }
            
            // 判断是否为纯音乐：刨去无用信息的歌词小于等于3行
            val isPureMusic = lyrics.lines.size <= 3
            
            val result = if (isPureMusic) {
                SongLyrics(lines = lyrics.lines, isPureMusic = true)
            } else if (songTitle.isNotEmpty() && songArtist.isNotEmpty() && lyrics.lines.isNotEmpty()) {
                // 非纯音乐，在歌词开头添加空行
                val titleLine = LyricLine(
                    text = " ",
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

    private fun parseLrc(lrc: String, tlyric: String, artistNames: List<String> = emptyList()): SongLyrics {
        val singleTimeRegex = Regex("""\[(\d+):(\d+\.\d+)](.*)""")
        val multiTimeRegex = Regex("""(\[\d+:\d+\.\d+])+(.*)""")
        val timeTagRegex = Regex("""\[(\d+):(\d+\.\d+)]""")
        val tlyricMap = mutableMapOf<Long, String>()
        
        // 解析翻译歌词
        if (tlyric.isNotEmpty()) {
            for (line in tlyric.split("\n")) {
                val match = singleTimeRegex.find(line)
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
        
        // 先收集所有歌词行及其时间（处理多时间戳）
        val rawLines = mutableListOf<Pair<Long, String>>()
        for (line in lrc.split("\n")) {
            val multiMatch = multiTimeRegex.find(line)
            if (multiMatch != null) {
                val timeTags = timeTagRegex.findAll(line).toList()
                val text = multiMatch.groupValues[2].trim()
                
                if (text.isNotEmpty() && timeTags.isNotEmpty()) {
                    // 为每个时间戳创建一行歌词
                    for (timeTag in timeTags) {
                        val min = timeTag.groupValues[1].toLong()
                        val sec = (timeTag.groupValues[2].toDouble() * 1000).toLong()
                        val time = min * 60 * 1000 + sec
                        rawLines.add(Pair(time, text))
                    }
                }
            }
        }
        
        // 按时间排序
        rawLines.sortBy { it.first }
        
        // 计算每行歌词的时长并过滤
        for (i in rawLines.indices) {
            val (time, text) = rawLines[i]
            val duration = if (i < rawLines.size - 1) {
                rawLines[i + 1].first - time
            } else {
                0L // 最后一行
            }
            
            if (!isExcludedLyric(text, duration)) {
                val translation = tlyricMap[time] ?: ""
                val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)
                
                if (isArtist && splitLyrics != null) {
                    // 需要拆行：冒号前是歌手行，冒号后是正常歌词
                    val artistText = formatArtistText(splitLyrics.first)
                    val lyricText = splitLyrics.second
                    // 歌手行
                    lines.add(LyricLine(text = artistText, translation = "", startAt = time, isArtistLine = true))
                    // 正常歌词（使用相同的时间戳）
                    lines.add(LyricLine(text = lyricText, translation = translation, startAt = time, isArtistLine = false))
                } else if (isArtist) {
                    // 整行是歌手行
                    val artistText = formatArtistText(text)
                    lines.add(LyricLine(text = artistText, translation = translation, startAt = time, isArtistLine = true))
                } else {
                    // 正常歌词
                    lines.add(LyricLine(text = text, translation = translation, startAt = time, isArtistLine = false))
                }
            }
        }

        return SongLyrics(lines = lines.sortedBy { it.startAt })
    }

    /**
     * 格式化歌手行文本
     * 1. 去掉冒号（中英文）
     */
    private fun formatArtistText(text: String): String {
        var result = text.trim()
        
        // 去掉末尾的冒号（中英文）
        if (result.endsWith("：")) {
            result = result.substring(0, result.length - 1).trim()
        } else if (result.endsWith(":")) {
            result = result.substring(0, result.length - 1).trim()
        }
        
        return result
    }

    /**
     * 为歌手行文本添加括号（用于传递给词幕）
     */
    private fun addBracketsToArtistText(text: String): String {
        val trimmed = text.trim()
        
        // 检查是否已经有括号
        val hasBrackets = (trimmed.startsWith("（") && trimmed.endsWith("）")) ||
                         (trimmed.startsWith("(") && trimmed.endsWith(")")) ||
                         (trimmed.startsWith("【") && trimmed.endsWith("】"))
        
        // 如果没有括号，加括号
        return if (!hasBrackets) {
            "（$trimmed）"
        } else {
            trimmed
        }
    }

    /**
     * 去掉外围完整括号
     * 只有当歌词最外围是完整括号时才去掉
     */
    private fun removeOuterBrackets(text: String): String {
        val trimmed = text.trim()
        
        // 检查是否是完整括号
        if (trimmed.startsWith("（") && trimmed.endsWith("）")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        if (trimmed.startsWith("【") && trimmed.endsWith("】")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        
        return trimmed
    }

    /**
     * 解析歌手行
     * 返回：Pair<Boolean, Pair<String, String>?>
     * - 第一个值：是否是歌手行
     * - 第二个值：如果需要拆行，返回Pair(歌手文本, 歌词文本)；否则返回null
     */
    private fun parseArtistLine(text: String, artistNames: List<String>): Pair<Boolean, Pair<String, String>?> {
        val trimmedText = text.trim()
        
        // 1. 去掉外围完整括号
        val contentWithoutBrackets = removeOuterBrackets(trimmedText)
        
        // 2. 关键词列表
        val keywords = mutableListOf<String>()
        // 歌手名称（拆分/、顿号、空格）
        keywords.addAll(artistNames.flatMap { it.split("/", "、", " ") }.map { it.trim() }.filter { it.isNotEmpty() })
        // 额外关键词
        keywords.addAll(listOf("童声", "合", "何炅", "万妮达", "袁娅维", "时代少年团宋亚轩"))
        
        // 3. 检查是否包含冒号（中英文都算）
        val hasChineseColon = contentWithoutBrackets.contains("：")
        val hasEnglishColon = contentWithoutBrackets.contains(":")
        
        if (hasChineseColon || hasEnglishColon) {
            // 找到冒号的位置（优先中文冒号）
            val colonIndex = if (hasChineseColon) contentWithoutBrackets.indexOf("：") 
                            else contentWithoutBrackets.indexOf(":")
            
            // 检查冒号前是否有括号字符（如果有则不处理）
            val beforeColon = contentWithoutBrackets.substring(0, colonIndex)
            if (beforeColon.contains("(") || beforeColon.contains("（") || 
                beforeColon.contains("[") || beforeColon.contains("【")) {
                return Pair(false, null)
            }
            
            val afterColon = contentWithoutBrackets.substring(colonIndex + 1).trim()
            
            if (afterColon.isNotEmpty()) {
                // 3.1.1 冒号后有文字：检查冒号前是否包含关键词
                for (keyword in keywords) {
                    if (beforeColon.contains(keyword)) {
                        // 返回需要拆行的标识
                        return Pair(true, Pair(beforeColon.trim(), afterColon))
                    }
                }
            } else {
                // 3.1.2 冒号后没有文字：除冒号以外的文字和关键词进行比对，剩余逻辑参考3.2
                val beforeColonTrimmed = beforeColon.trim()
                
                // 3.2.1 关键词和歌词完全一样，算作歌手行
                for (keyword in keywords) {
                    if (beforeColonTrimmed == keyword) {
                        return Pair(true, null)
                    }
                }
                
                // 3.2.2 多位歌手识别
                if (beforeColonTrimmed.contains(" ") || beforeColonTrimmed.contains("、") ||
                    beforeColonTrimmed.contains("/") || beforeColonTrimmed.contains("\\")) {
                    val artists = beforeColonTrimmed.split(" ", "、", "/", "\\")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (artists.isNotEmpty()) {
                        var allMatch = true
                        for (artist in artists) {
                            var artistMatch = false
                            for (keyword in keywords) {
                                // 3种情况：歌手和关键字相同/歌手包含关键字/关键字包含歌手
                                if (artist == keyword || artist.contains(keyword) || keyword.contains(artist)) {
                                    artistMatch = true
                                    break
                                }
                            }
                            if (!artistMatch) {
                                allMatch = false
                                break
                            }
                        }
                        if (allMatch) {
                            return Pair(true, null)
                        }
                    }
                }
            }
        } else {
            // 3.2 不包含冒号：直接用整行歌词和关键词做对比
            
            // 3.2.1 关键词和歌词完全一样，算作歌手行
            for (keyword in keywords) {
                if (contentWithoutBrackets == keyword) {
                    return Pair(true, null)
                }
            }
            
            // 3.2.2 多位歌手识别
            if (contentWithoutBrackets.contains(" ") || contentWithoutBrackets.contains("、") ||
                contentWithoutBrackets.contains("/") || contentWithoutBrackets.contains("\\")) {
                val artists = contentWithoutBrackets.split(" ", "、", "/", "\\")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (artists.isNotEmpty()) {
                    var allMatch = true
                    for (artist in artists) {
                        var artistMatch = false
                        for (keyword in keywords) {
                            // 3种情况：歌手和关键字相同/歌手包含关键字/关键字包含歌手
                            if (artist == keyword || artist.contains(keyword) || keyword.contains(artist)) {
                                artistMatch = true
                                break
                            }
                        }
                        if (!artistMatch) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch) {
                        return Pair(true, null)
                    }
                }
            }
        }
        
        return Pair(false, null)
    }
    private fun parseYrc(yrc: String, tlyric: String, artistNames: List<String> = emptyList()): SongLyrics {
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
            if (text.isNotEmpty() && !isExcludedLyric(text, dur)) {
                // 查找翻译（使用开始时间匹配）
                val translation = tlyricMap[start] ?: ""
                val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)
                
                if (isArtist && splitLyrics != null) {
                    // 需要拆行：冒号前是歌手行，冒号后是正常歌词
                    val artistText = formatArtistText(splitLyrics.first)
                    val lyricText = splitLyrics.second
                    // 歌手行
                    lines.add(LyricLine(
                        text = artistText,
                        translation = "",
                        startAt = start,
                        endAt = end,
                        words = null,
                        isArtistLine = true
                    ))
                    // 正常歌词（使用相同的时间戳）
                    lines.add(LyricLine(
                        text = lyricText,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = false
                    ))
                } else if (isArtist) {
                    // 整行是歌手行
                    val artistText = formatArtistText(text)
                    lines.add(LyricLine(
                        text = artistText,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = true
                    ))
                } else {
                    // 正常歌词
                    lines.add(LyricLine(
                        text = text,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = false
                    ))
                }
            }
        }

        return SongLyrics(lines = lines.sortedBy { it.startAt })
    }

    private fun isExcludedLyric(text: String, duration: Long = 0): Boolean {
        val colonKeywords = listOf(
            "编曲", "母带", "OP", "SP", "出品", "贡献者", "调教", "编写", "改编", "和声", "吉他", "鼓", "混音", "剪辑", "MV", "曲绘", "后期", "推广", "出品人", "制作人", "监制", "统筹", "平面视觉", "感谢", "录音棚", "录音", "设计", "手风琴", "巴拉莱卡", "弦乐", "小提琴", "编辑", "录音室", "工程师", "导演", "造型师", "妆发", "文案", "企划", "宣传", "艺人经纪", "经纪人", "营销", "总监", "op", "sp", "pv", "PV", "OA", "OC", "oa", "oc", "制作", "署名", "策划", "表演", "词", "曲", "发行", "原唱", "打击乐", "贝斯", "钢琴", "键盘", "Program", "合音", "合唱", "演唱", "乐谱", "提琴", "和音", "鸣谢", "运营", "执行", "拍摄", "摄影", "花絮", "助理", "服装", "顾问", "版权", "翻译", "制片", "道具师", "灯光师", "调色", "合成", "承制", "艺人", "行销", "媒介", "有限公司", "资产", "技术支持", "合作伙伴", "视频平台", "口哨", "三弦", "长笛", "黑管", "童声", "缩混", "合作", "指导", "PGM", "编舞", "舞团", "宣发", "录混", "唢呐", "萨克斯", "笛子", "声明", "古筝", "箫", "人声", "配唱", "主唱", "封面", "A&R", "乐队", "队长", "女生", "项目支持", "曼陀林", "指挥", "单簧管", "独奏", "乐团", "协力", "项目", "rap", "友情客串", "二胡", "帮帮团", "Guitar", "旋律创作", "陶笛"
        )

        val hasColon = text.contains("：") || text.contains(":")
        if (hasColon) {
            for (keyword in colonKeywords) {
                if (text.contains(keyword)) {
                    return true
                }
            }
        }

        val plainKeywords = listOf(
            "支持好歌曲，为歌曲留名",
            "支持好音乐，为歌曲留名",
            "网易云音乐ACG企划",
            "纯音乐，请欣赏",
            "未经著作权人许可",
            "不得翻唱",
            "本作品经过词曲著作权利方授权",
            "ISRC ",
            "Co.",
            "Ltd.",
            "发布会现场",
            "萨克斯管/",
            "小号/",
            "长号",
            "@牛班NEWBAND",
            "御坂的蓝白",
            "长笛 ",
            "钟琴 ",
            "竖琴 ",
            "YCE ",
            "乐器配置 ",
            "录制 ",
            "音序工坊"
        )
        
        for (keyword in plainKeywords) {
            if (text.contains(keyword)) {
                return true
            }
        }
        
        // 不带冒号的关键词+时长小于1000ms双重判定
        val shortDurationKeywords = listOf(
            "工作团队", "出品", "广播电视台", "故宫博物院", "音乐集团",
            "联合出品", "文化传媒", "微博", "协办单位", "产业大会", "汪斯遥", "Zhang"
        )
        
        if (duration in 1..1999) {
            for (keyword in shortDurationKeywords) {
                if (text.contains(keyword)) {
                    return true
                }
            }
        }
        
        // 过滤开头包含"未经著作权人书面许可"的歌词
        if (text.contains("未经著作权人书面许可")) {
            return true
        }
        
        return false
    }

    suspend fun getArtistInfo(artistId: String): Result<ArtistInfo> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = artistId.removePrefix("neartist_")
            val url = "https://music.163.com/api/artist/$id"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
            
            val artist = json.getAsJsonObject("artist")
            val artistInfo = ArtistInfo(
                id = if (artist.has("id")) artist.get("id").asLong else 0,
                name = if (artist.has("name") && !artist.get("name").isJsonNull) artist.get("name").asString else "",
                picUrl = if (artist.has("picUrl") && !artist.get("picUrl").isJsonNull) artist.get("picUrl").asString else "",
                albumSize = if (artist.has("albumSize")) artist.get("albumSize").asInt else 0,
                mvSize = if (artist.has("mvSize")) artist.get("mvSize").asInt else 0,
                musicSize = if (artist.has("musicSize")) artist.get("musicSize").asInt else 0,
                briefDesc = if (artist.has("briefDesc") && !artist.get("briefDesc").isJsonNull) artist.get("briefDesc").asString else ""
            )
            
            Result.success(artistInfo)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getArtistInfo error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getArtistAlbums(artistId: String, offset: Int = 0, limit: Int = 50): Result<List<AlbumInfo>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = artistId.removePrefix("neartist_")
            val url = "https://music.163.com/api/artist/albums/$id?offset=$offset&limit=$limit"
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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
            
            val hotAlbums = json.getAsJsonArray("hotAlbums")
            val albums = mutableListOf<AlbumInfo>()
            
            for (i in 0 until hotAlbums.size()) {
                val album = hotAlbums.get(i).asJsonObject
                albums.add(
                    AlbumInfo(
                        id = if (album.has("id")) album.get("id").asLong else 0,
                        name = if (album.has("name") && !album.get("name").isJsonNull) album.get("name").asString else "",
                        picUrl = if (album.has("picUrl") && !album.get("picUrl").isJsonNull) album.get("picUrl").asString else "",
                        size = if (album.has("size")) album.get("size").asInt else 0,
                        publishTime = if (album.has("publishTime")) album.get("publishTime").asLong else 0
                    )
                )
            }
            
            Result.success(albums)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getArtistAlbums error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getArtistSongs(artistId: String, offset: Int = 0, limit: Int = 50): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = artistId.removePrefix("neartist_")
            val url = "https://music.163.com/api/v1/artist/songs?id=$id&offset=$offset&limit=$limit"
            
            Log.d("NeteaseApi", "getArtistSongs - artistId: $artistId, id: $id, url: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("NeteaseApi", "getArtistSongs - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("NeteaseApi", "getArtistSongs - Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            Log.d("NeteaseApi", "getArtistSongs - response length: ${responseBody.length}")
            
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))
            
            // 检查返回的JSON结构
            val hotSongs = when {
                json.has("hotSongs") && !json.get("hotSongs").isJsonNull && json.get("hotSongs").isJsonArray -> json.getAsJsonArray("hotSongs")
                json.has("songs") && !json.get("songs").isJsonNull && json.get("songs").isJsonArray -> json.getAsJsonArray("songs")
                else -> {
                    Log.e("NeteaseApi", "getArtistSongs - No hotSongs or songs field, keys: ${json.keySet()}")
                    return@withContext Result.failure(Exception("No songs field in response"))
                }
            }
            
            Log.d("NeteaseApi", "getArtistSongs - found ${hotSongs.size()} songs")
            val tracks = mutableListOf<Track>()
            
            for (i in 0 until hotSongs.size()) {
                try {
                    val song = hotSongs.get(i).asJsonObject
                    // 兼容两种格式：ar/artists 和 al/album
                    val artists = when {
                        song.has("ar") && song.get("ar").isJsonArray -> song.getAsJsonArray("ar")
                        song.has("artists") && song.get("artists").isJsonArray -> song.getAsJsonArray("artists")
                        else -> null
                    }
                    val artistNames = mutableListOf<String>()
                    val artistIds = mutableListOf<String>()
                    if (artists != null) {
                        for (j in 0 until artists.size()) {
                            val artist = artists.get(j).asJsonObject
                            if (artist.has("name") && !artist.get("name").isJsonNull) {
                                artistNames.add(artist.get("name").asString)
                            }
                            if (artist.has("id")) {
                                artistIds.add("neartist_${artist.get("id").asLong}")
                            }
                        }
                    }
                    val artistName = formatArtistNames(artistNames)
                    val artistIdVal = if (artists != null && artists.size() > 0) artists.get(0).asJsonObject.get("id").asLong else 0L
                    val album = when {
                        song.has("al") && song.get("al").isJsonObject -> song.getAsJsonObject("al")
                        song.has("album") && song.get("album").isJsonObject -> song.getAsJsonObject("album")
                        else -> null
                    }
                    
                    tracks.add(
                        Track(
                            id = "netrack_${song.get("id").asLong}",
                            title = song.get("name").asString,
                            artist = artistName,
                            artistId = "neartist_$artistIdVal",
                            album = album?.get("name")?.asString ?: "",
                            albumId = "nealbum_${album?.get("id")?.asLong ?: 0}",
                            sourceUrl = "https://music.163.com/#/song?id=${song.get("id").asLong}",
                            imgUrl = album?.get("picUrl")?.asString ?: "",
                            duration = if (song.has("dt")) song.get("dt").asLong else if (song.has("duration")) song.get("duration").asLong else 0L,
                            isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false,
                            artistNames = artistNames,
                            artistIds = artistIds
                        )
                    )
                } catch (e: Exception) {
                    Log.e("NeteaseApi", "getArtistSongs - parse song error: ${e.message}")
                }
            }
            
            Result.success(tracks)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getArtistSongs error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getAlbumDetail(albumId: String): Result<Pair<AlbumInfo, List<Track>>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/album/$albumId"
            
            Log.d("NeteaseApi", "getAlbumDetail - albumId: $albumId, url: $url")
            
            val request = Request.Builder()
                .url(url)
                .get()
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("NeteaseApi", "getAlbumDetail - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("NeteaseApi", "getAlbumDetail - Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            Log.d("NeteaseApi", "getAlbumDetail - response length: ${responseBody.length}")
            
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))
            
            val album = if (json.has("album") && !json.get("album").isJsonNull) {
                json.getAsJsonObject("album")
            } else {
                Log.e("NeteaseApi", "getAlbumDetail - No album field")
                return@withContext Result.failure(Exception("No album field in response"))
            }
            
            val albumInfo = AlbumInfo(
                id = if (album.has("id")) album.get("id").asLong else 0,
                name = if (album.has("name") && !album.get("name").isJsonNull) album.get("name").asString else "",
                picUrl = if (album.has("picUrl") && !album.get("picUrl").isJsonNull) album.get("picUrl").asString else "",
                size = if (album.has("size")) album.get("size").asInt else 0,
                publishTime = if (album.has("publishTime")) album.get("publishTime").asLong else 0,
                artist = parseAlbumArtist(album).first,
                artistId = parseAlbumArtist(album).second
            )
            
            // 歌曲可能在 album.songs 或者根级别的 songs
            val songsArray = when {
                album.has("songs") && !album.get("songs").isJsonNull && album.get("songs").isJsonArray -> album.getAsJsonArray("songs")
                json.has("songs") && !json.get("songs").isJsonNull && json.get("songs").isJsonArray -> json.getAsJsonArray("songs")
                else -> {
                    Log.e("NeteaseApi", "getAlbumDetail - No songs field or not a JsonArray")
                    return@withContext Result.failure(Exception("No songs field in response"))
                }
            }
            
            Log.d("NeteaseApi", "getAlbumDetail - album: ${albumInfo.name}, songs: ${songsArray.size()}")
            val tracks = mutableListOf<Track>()
            
            for (i in 0 until songsArray.size()) {
                try {
                    val song = songsArray.get(i).asJsonObject
                    // 兼容两种格式：ar/artists 和 al/album
                    val artists = when {
                        song.has("ar") && song.get("ar").isJsonArray -> song.getAsJsonArray("ar")
                        song.has("artists") && song.get("artists").isJsonArray -> song.getAsJsonArray("artists")
                        else -> null
                    }
                    val artistNames = mutableListOf<String>()
                    val artistIds = mutableListOf<String>()
                    if (artists != null) {
                        for (j in 0 until artists.size()) {
                            val artist = artists.get(j).asJsonObject
                            if (artist.has("name") && !artist.get("name").isJsonNull) {
                                artistNames.add(artist.get("name").asString)
                            }
                            if (artist.has("id")) {
                                artistIds.add("neartist_${artist.get("id").asLong}")
                            }
                        }
                    }
                    val artistName = formatArtistNames(artistNames)
                    val artistIdVal = if (artists != null && artists.size() > 0) artists.get(0).asJsonObject.get("id").asLong else 0L
                    val albumObj = when {
                        song.has("al") && song.get("al").isJsonObject -> song.getAsJsonObject("al")
                        song.has("album") && song.get("album").isJsonObject -> song.getAsJsonObject("album")
                        else -> null
                    }
                    
                    tracks.add(
                        Track(
                            id = "netrack_${song.get("id").asLong}",
                            title = song.get("name").asString,
                            artist = artistName,
                            artistId = "neartist_$artistIdVal",
                            album = albumObj?.get("name")?.asString ?: "",
                            albumId = "nealbum_${albumObj?.get("id")?.asLong ?: 0}",
                            sourceUrl = "https://music.163.com/#/song?id=${song.get("id").asLong}",
                            imgUrl = albumObj?.get("picUrl")?.asString ?: "",
                            duration = if (song.has("dt")) song.get("dt").asLong else if (song.has("duration")) song.get("duration").asLong else 0L,
                            isVip = if (song.has("fee")) song.get("fee").asInt == 1 else false,
                            artistNames = artistNames,
                            artistIds = artistIds
                        )
                    )
                } catch (e: Exception) {
                    Log.e("NeteaseApi", "getAlbumDetail - parse song error: ${e.message}")
                }
            }
            
            Result.success(Pair(albumInfo, tracks))
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getAlbumDetail error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseAlbumArtist(album: JsonObject): Pair<String, Long> {
        // 尝试解析 artist 字段（单个歌手）
        if (album.has("artist") && !album.get("artist").isJsonNull) {
            val artistObj = album.getAsJsonObject("artist")
            if (artistObj.has("name") && !artistObj.get("name").isJsonNull) {
                val name = artistObj.get("name").asString
                val id = if (artistObj.has("id")) artistObj.get("id").asLong else 0L
                return Pair(name, id)
            }
        }
        // 尝试解析 artists 字段（多个歌手）
        if (album.has("artists") && album.get("artists").isJsonArray) {
            val artists = album.getAsJsonArray("artists")
            val names = mutableListOf<String>()
            var firstId = 0L
            for (i in 0 until artists.size()) {
                val artist = artists.get(i).asJsonObject
                if (artist.has("name") && !artist.get("name").isJsonNull) {
                    names.add(artist.get("name").asString)
                }
                if (i == 0 && artist.has("id")) {
                    firstId = artist.get("id").asLong
                }
            }
            if (names.isNotEmpty()) {
                return Pair(names.joinToString(" / "), firstId)
            }
        }
        return Pair("", 0L)
    }

    suspend fun getSongComments(
        songId: String,
        offset: Int = 0,
        limit: Int = 20,
        sortType: Int = 2 // 1: 推荐, 2: 热度, 3: 最新
    ): Result<CommentResult> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val url = "https://music.163.com/api/v1/resource/comments/R_SO_4_$id"
            val reqData = """{"rid":"R_SO_4_$id","offset":$offset,"total":true,"limit":$limit,"sortType":$sortType}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val formBody = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            Log.d("NeteaseApi", "getSongComments - url: $url, sortType: $sortType, offset: $offset, limit: $limit")

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

            val total = if (json.has("total")) json.get("total").asInt else 0
            // hasMore 基于已加载数量和总数比较
            val currentCount = offset + limit
            val hasMore = currentCount < total

            Log.d("NeteaseApi", "getSongComments - total: $total, hasMore: $hasMore, offset: $offset")

            val comments = mutableListOf<Comment>()
            // 解析热门评论
            if (sortType == 2 && json.has("hotComments") && json.get("hotComments").isJsonArray) {
                val hotCommentsArray = json.getAsJsonArray("hotComments")
                for (i in 0 until hotCommentsArray.size()) {
                    try {
                        val commentJson = hotCommentsArray.get(i).asJsonObject
                        val comment = parseComment(commentJson)
                        comments.add(comment)
                    } catch (e: Exception) {
                        Log.e("NeteaseApi", "parseHotComment error: ${e.message}")
                    }
                }
            }
            // 解析普通评论
            if (json.has("comments") && json.get("comments").isJsonArray) {
                val commentsArray = json.getAsJsonArray("comments")
                for (i in 0 until commentsArray.size()) {
                    try {
                        val commentJson = commentsArray.get(i).asJsonObject
                        val comment = parseComment(commentJson)
                        comments.add(comment)
                    } catch (e: Exception) {
                        Log.e("NeteaseApi", "parseComment error: ${e.message}")
                    }
                }
            }

            Log.d("NeteaseApi", "getSongComments - loaded ${comments.size} comments")

            Result.success(CommentResult(comments = comments, total = total, hasMore = hasMore))
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getSongComments error: ${e.message}")
            Result.failure(e)
        }
    }

    suspend fun getCommentReplies(
        commentId: Long,
        songId: String,
        offset: Int = 0,
        limit: Int = 20
    ): Result<List<Comment>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val url = "https://music.163.com/api/v1/resource/comments/R_SO_4_$id/reply"
            val reqData = """{"commentId":$commentId,"rid":"R_SO_4_$id","offset":$offset,"limit":$limit}"""
            val encrypted = NeteaseCrypto.weapi(reqData)

            val formBody = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()

            val request = Request.Builder()
                .url(url)
                .post(formBody)
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

            val replies = mutableListOf<Comment>()
            if (json.has("comments") && json.get("comments").isJsonArray) {
                val commentsArray = json.getAsJsonArray("comments")
                for (i in 0 until commentsArray.size()) {
                    try {
                        val commentJson = commentsArray.get(i).asJsonObject
                        val comment = parseComment(commentJson)
                        replies.add(comment)
                    } catch (e: Exception) {
                        Log.e("NeteaseApi", "parseReply error: ${e.message}")
                    }
                }
            }

            Result.success(replies)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getCommentReplies error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun parseComment(json: JsonObject): Comment {
        val commentId = if (json.has("commentId")) json.get("commentId").asLong else 0L
        val content = if (json.has("content") && !json.get("content").isJsonNull) json.get("content").asString else ""
        val time = if (json.has("time")) json.get("time").asLong else 0L
        val likedCount = if (json.has("likedCount")) json.get("likedCount").asInt else 0

        var userId = 0L
        var nickname = ""
        var avatarUrl = ""
        if (json.has("user") && json.get("user").isJsonObject) {
            val user = json.getAsJsonObject("user")
            userId = if (user.has("userId")) user.get("userId").asLong else 0L
            nickname = if (user.has("nickname") && !user.get("nickname").isJsonNull) user.get("nickname").asString else ""
            avatarUrl = if (user.has("avatarUrl") && !user.get("avatarUrl").isJsonNull) user.get("avatarUrl").asString else ""
        }

        val replies = mutableListOf<Comment>()
        if (json.has("beReplied") && json.get("beReplied").isJsonArray) {
            // 这里是被回复的评论，不是回复列表
        }

        return Comment(
            commentId = commentId,
            userId = userId,
            nickname = nickname,
            avatarUrl = avatarUrl,
            content = content,
            time = time,
            likedCount = likedCount,
            replies = replies
        )
    }
}
