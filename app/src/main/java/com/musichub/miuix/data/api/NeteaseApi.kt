package com.musichub.miuix.data.api

import com.musichub.miuix.data.model.AlbumInfo
import com.musichub.miuix.data.model.ArtistInfo
import com.musichub.miuix.data.model.Comment
import com.musichub.miuix.data.model.CommentResult
import com.musichub.miuix.data.model.LyricLine
import com.musichub.miuix.data.model.Playlist
import com.musichub.miuix.data.model.PlaylistDetail
import com.musichub.miuix.data.model.RecommendPlaylist
import com.musichub.miuix.data.model.SongLyrics
import com.musichub.miuix.data.model.Track
import com.musichub.miuix.data.model.UserProfile
import com.musichub.miuix.data.model.displayPlaylistTitle
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
    // cookies 与 userId 的同步标记：换 cookie 时 cookieGeneration 自增，userId 更新到位后 userIdGeneration 跟上
    private var cookieGeneration: Int = 0
    private var userIdGeneration: Int = 0
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
            val initGeneration = cookieGeneration
            kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
                val resolvedUserId = getUserId()
                // 期间若已切换账号，以切换结果为准，避免旧 cookie 的 userId 覆盖
                if (initGeneration != cookieGeneration) return@launch
                userId = resolvedUserId
                android.util.Log.d("NeteaseApi", "Init - User ID: $userId")
                // 如果已登录但账号列表中没有当前账号，自动保存
                if (resolvedUserId != null && cookies.contains("MUSIC_U")) {
                    val currentUserId = resolvedUserId.toString()
                    if (!accounts.any { it.userId == currentUserId }) {
                        saveCurrentAccount()
                    }
                }
            }
        }
    }
    
    @Synchronized
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
    
    @Synchronized
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
            // 同步写入，确保删除/新增账号在进程被杀前已落盘
            prefs.edit().putString("accounts", jsonArray.toString()).commit()
        }
    }
    
    @Synchronized
    fun saveCurrentAccount() {
        // userId 还未与当前 cookies 同步时跳过，避免用旧账号 ID 覆盖/新增错误账号
        if (userIdGeneration != cookieGeneration) {
            android.util.Log.d("NeteaseApi", "Skip saveCurrentAccount: userId not synced with cookies")
            return
        }
        // 保存当前账号到列�?
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
            // cookies 与 userId 同步设置，标记为一致
            cookieGeneration++
            userIdGeneration = cookieGeneration
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
        // 标记 cookies 已变化，userId 尚未同步
        cookieGeneration++
        // Save to SharedPreferences
        context?.let { ctx ->
            val prefs = ctx.getSharedPreferences("netease_prefs", android.content.Context.MODE_PRIVATE)
            prefs.edit().putString("cookies", cookies).apply()
        }
        android.util.Log.d("NeteaseApi", "Cookie set and saved: $cookies")
        // 获取用户ID
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val gen = cookieGeneration
            val oldUserId = userId
            val resolvedUserId = getUserId()
            // 期间 cookie 又变化了，丢弃本次结果
            if (gen != cookieGeneration) return@launch
            userId = resolvedUserId
            // userId 已与本次 cookies 同步
            userIdGeneration = gen
            android.util.Log.d("NeteaseApi", "User ID: $userId")
            // 如果用户ID变化，清除旧的用户资料缓存并通知UI
            if (oldUserId != null && oldUserId != userId) {
                android.util.Log.d("NeteaseApi", "User changed from $oldUserId to $userId, clearing profile cache")
                clearUserProfileCache()
            }
            // 保存当前账号到账号列表（首次登录也要保存）
            if (userId != null && cookies.contains("MUSIC_U")) {
                saveCurrentAccount()
            }
            // 账号保存完成后再通知 UI，确保能读取到新账号的 VIP / 用户信息
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onProfileChanged?.invoke()
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

    /** 从 cookie 串里取 __csrf（weapi 请求体需要，对齐 SPlayer-Next） */
    private fun csrfTokenFromCookies(): String {
        cookies.split(";").forEach { raw ->
            val part = raw.trim()
            val idx = part.indexOf('=')
            if (idx > 0 && part.substring(0, idx) == "__csrf") {
                return part.substring(idx + 1)
            }
        }
        return ""
    }

    /** 构造 weapi 表单（params + encSecKey） */
    private fun weapiForm(reqData: String): FormBody {
        val encrypted = NeteaseCrypto.weapi(reqData)
        return FormBody.Builder()
            .add("params", encrypted["params"]!!)
            .add("encSecKey", encrypted["encSecKey"]!!)
            .build()
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
                    val name = obj.get("name").asString
                    // 过滤「私人雷达」等雷达类歌单
                    if (name.contains("雷达")) continue
                    playlists.add(
                        RecommendPlaylist(
                            id = "neplaylist_${obj.get("id").asLong}",
                            title = name,
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

    /** 专属歌单（已登录）：推荐歌单 / 每日推荐专属歌单 */
    suspend fun getRecommendResource(): Result<List<RecommendPlaylist>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/v1/discovery/recommend/resource"
            val reqData = "{}"
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response body"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Parsed JSON is null"))

            val array = when {
                json.has("recommend") -> json.getAsJsonArray("recommend")
                json.has("result") -> json.getAsJsonArray("result")
                else -> return@withContext Result.failure(Exception("No recommend field, code: ${if (json.has("code")) json.get("code").asInt else -1}"))
            }

            val playlists = mutableListOf<RecommendPlaylist>()
            for (i in 0 until array.size()) {
                try {
                    val obj = array.get(i).asJsonObject
                    val name = obj.get("name").asString
                    // 过滤「私人雷达」「华语私人雷达」等雷达类歌单
                    if (name.contains("雷达")) continue
                    playlists.add(
                        RecommendPlaylist(
                            id = "neplaylist_${obj.get("id").asLong}",
                            title = name,
                            coverImgUrl = obj.get("picUrl").asString,
                            playCount = when {
                                obj.has("playcount") -> obj.get("playcount").asLong
                                obj.has("playCount") -> obj.get("playCount").asLong
                                else -> 0L
                            },
                            description = if (obj.has("copywriter") && !obj.get("copywriter").isJsonNull) obj.get("copywriter").asString else ""
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            Result.success(playlists)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getRecommendResource exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 歌单简要信息（仅取名称/封面，不拉取歌曲） */
    suspend fun getPlaylistSummary(playlistId: String): Result<RecommendPlaylist> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = playlistId.removePrefix("neplaylist_")
            val url = "https://music.163.com/weapi/v3/playlist/detail"
            val reqData = """{"id":$id,"offset":0,"total":false,"limit":0,"n":0,"csrf_token":""}"""
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
                .header("Content-Type", "application/x-www-form-urlencoded")
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
                ?: return@withContext Result.failure(Exception("Parsed JSON is null"))
            if (!json.has("playlist")) {
                return@withContext Result.failure(Exception("No playlist field"))
            }
            val playlist = json.getAsJsonObject("playlist")
            Result.success(
                RecommendPlaylist(
                    id = "neplaylist_$id",
                    title = if (playlist.has("name") && !playlist.get("name").isJsonNull) playlist.get("name").asString else "",
                    coverImgUrl = if (playlist.has("coverImgUrl") && !playlist.get("coverImgUrl").isJsonNull) playlist.get("coverImgUrl").asString else "",
                    playCount = if (playlist.has("playCount")) playlist.get("playCount").asLong else 0L,
                    trackCount = if (playlist.has("trackCount")) playlist.get("trackCount").asInt else 0
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getPlaylistSummary exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 热门歌手 */
    suspend fun getTopArtists(limit: Int = 12): Result<List<ArtistInfo>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/artist/top"
            val reqData = """{"limit":$limit,"offset":0,"total":true}"""
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response body"))
            }
            android.util.Log.d("NeteaseApi", "getTopArtists response: $responseBody")
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Parsed JSON is null"))

            val array = when {
                json.has("artists") && json.get("artists").isJsonArray -> json.getAsJsonArray("artists")
                json.has("list") && json.get("list").isJsonObject &&
                    json.getAsJsonObject("list").has("artists") -> json.getAsJsonObject("list").getAsJsonArray("artists")
                else -> return@withContext Result.failure(Exception("No artists field"))
            }

            val artists = mutableListOf<ArtistInfo>()
            for (i in 0 until array.size()) {
                try {
                    val obj = array.get(i).asJsonObject
                    val pic = when {
                        obj.has("picUrl") && !obj.get("picUrl").isJsonNull -> obj.get("picUrl").asString
                        obj.has("img1v1Url") && !obj.get("img1v1Url").isJsonNull -> obj.get("img1v1Url").asString
                        else -> ""
                    }
                    artists.add(
                        ArtistInfo(
                            id = if (obj.has("id")) obj.get("id").asLong else 0,
                            name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else "",
                            picUrl = pic,
                            albumSize = if (obj.has("albumSize")) obj.get("albumSize").asInt else 0,
                            mvSize = if (obj.has("mvSize")) obj.get("mvSize").asInt else 0,
                            musicSize = if (obj.has("musicSize")) obj.get("musicSize").asInt else 0
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            Result.success(artists)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getTopArtists exception: ${e.message}", e)
            Result.failure(e)
        }
    }

    /** 新碟上架 */
    suspend fun getNewAlbums(limit: Int = 12): Result<List<AlbumInfo>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/album/new"
            val reqData = """{"limit":$limit,"offset":0,"total":true,"area":"ALL"}"""
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

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                return@withContext Result.failure(Exception("Empty response body"))
            }
            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Parsed JSON is null"))

            val array = if (json.has("albums")) json.getAsJsonArray("albums")
                else return@withContext Result.failure(Exception("No albums field"))

            val albums = mutableListOf<AlbumInfo>()
            for (i in 0 until array.size()) {
                try {
                    val obj = array.get(i).asJsonObject
                    val artistObj = if (obj.has("artist") && obj.get("artist").isJsonObject) obj.getAsJsonObject("artist") else null
                    albums.add(
                        AlbumInfo(
                            id = if (obj.has("id")) obj.get("id").asLong else 0,
                            name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else "",
                            picUrl = if (obj.has("picUrl") && !obj.get("picUrl").isJsonNull) obj.get("picUrl").asString else "",
                            size = if (obj.has("size")) obj.get("size").asInt else 0,
                            publishTime = if (obj.has("publishTime")) obj.get("publishTime").asLong else 0,
                            artist = artistObj?.let { if (it.has("name") && !it.get("name").isJsonNull) it.get("name").asString else "" } ?: "",
                            artistId = artistObj?.let { if (it.has("id")) it.get("id").asLong else 0 } ?: 0
                        )
                    )
                } catch (e: Exception) {
                    // Skip invalid items
                }
            }
            Result.success(albums)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getNewAlbums exception: ${e.message}", e)
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
                title = displayPlaylistTitle(playlist.get("name").asString),
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
            // 部分响应歌曲对象不带 fee，用 privileges 里的 fee 作为兜底
            val privilegeFee = mutableMapOf<Long, Int>()
            if (json.has("privileges") && json.get("privileges").isJsonArray) {
                json.getAsJsonArray("privileges").forEach { p ->
                    val po = p.asJsonObject
                    if (po.has("id") && po.has("fee") && !po.get("fee").isJsonNull) {
                        privilegeFee[po.get("id").asLong] = po.get("fee").asInt
                    }
                }
            }
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
                val songIdLong = song.get("id").asLong
                val songFee = when {
                    song.has("fee") && !song.get("fee").isJsonNull -> song.get("fee").asInt
                    privilegeFee.containsKey(songIdLong) -> privilegeFee.getValue(songIdLong)
                    else -> 0
                }

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
                        isVip = songFee == 1,
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

    suspend fun getSongUrl(
        songId: String,
        bitrate: Int = 999000,
        accountCookie: String? = null
    ): Result<Triple<String, Int, String>> = withContext(Dispatchers.IO) {
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

            val cookieStr = "os=pc; ${accountCookie ?: cookies}"

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

    @Synchronized
    fun logoutCurrentAccount(): Boolean {
        // 只移除当前账号，其他已保存账号保留
        val currentId = userId?.toString()
            ?: accounts.firstOrNull { it.cookies == cookies }?.userId
        if (currentId != null) {
            val remaining = accounts.filterNot { it.userId == currentId }
            if (remaining.size != accounts.size) {
                accounts = remaining.toMutableList()
            }
        }

        // 清除当前会话与资料缓存
        cookies = ""
        userId = null
        cookieGeneration++
        userIdGeneration = cookieGeneration
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

        // 立即持久化账号列表（同步写入，避免重启后已删除账号又出现）
        saveAccounts()

        android.util.Log.d("NeteaseApi", "Logged out current account: $currentId, remaining: ${accounts.size}")

        // 若还有其他账号，自动切换到其中一个并保持登录
        val next = accounts.firstOrNull() ?: return false
        return switchToAccount(next.userId)
    }

    /** 兼容旧调用：退出当前账号。 */
    fun logout() {
        logoutCurrentAccount()
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

    /** 收藏的专辑列表（album/sublist） */
    suspend fun getUserAlbums(limit: Int = 100, offset: Int = 0): Result<List<AlbumInfo>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/weapi/album/sublist"
            val csrf = csrfTokenFromCookies()
            val reqData = """{"limit":$limit,"offset":$offset,"csrf_token":"$csrf","e_r":false}"""
            val body = weapiForm(reqData)

            val request = Request.Builder()
                .url(url)
                .post(body)
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
            if (json.has("code") && !json.get("code").isJsonNull && json.get("code").asInt != 200) {
                val msg = when {
                    json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                    json.has("msg") && !json.get("msg").isJsonNull -> json.get("msg").asString
                    else -> "code=${json.get("code").asInt}"
                }
                return@withContext Result.failure(Exception("收藏专辑接口错误: $msg"))
            }
            val data = if (json.has("data") && json.get("data").isJsonArray) json.getAsJsonArray("data") else null
            if (data == null) {
                return@withContext Result.success(emptyList())
            }
            val albums = mutableListOf<AlbumInfo>()
            for (i in 0 until data.size()) {
                try {
                    val obj = data.get(i).asJsonObject
                    val id = if (obj.has("id") && !obj.get("id").isJsonNull) obj.get("id").asLong else 0L
                    val name = if (obj.has("name") && !obj.get("name").isJsonNull) obj.get("name").asString else ""
                    val picUrl = if (obj.has("picUrl") && !obj.get("picUrl").isJsonNull) obj.get("picUrl").asString else ""
                    val size = if (obj.has("size") && !obj.get("size").isJsonNull) obj.get("size").asInt else 0
                    val publishTime = if (obj.has("publishTime") && !obj.get("publishTime").isJsonNull) obj.get("publishTime").asLong else 0L
                    var artist = ""
                    var artistId = 0L
                    when {
                        obj.has("artists") && obj.get("artists").isJsonArray && obj.getAsJsonArray("artists").size() > 0 -> {
                            val a = obj.getAsJsonArray("artists").get(0).asJsonObject
                            if (a.has("name") && !a.get("name").isJsonNull) artist = a.get("name").asString
                            if (a.has("id") && !a.get("id").isJsonNull) artistId = a.get("id").asLong
                        }
                        obj.has("artist") && obj.get("artist").isJsonObject -> {
                            val a = obj.getAsJsonObject("artist")
                            if (a.has("name") && !a.get("name").isJsonNull) artist = a.get("name").asString
                            if (a.has("id") && !a.get("id").isJsonNull) artistId = a.get("id").asLong
                        }
                    }
                    if (id != 0L) {
                        albums.add(
                            AlbumInfo(
                                id = id,
                                name = name,
                                picUrl = picUrl,
                                size = size,
                                publishTime = publishTime,
                                artist = artist,
                                artistId = artistId
                            )
                        )
                    }
                } catch (e: Exception) {
                    Log.e("NeteaseApi", "getUserAlbums parse item error: ${e.message}")
                }
            }
            Log.d("NeteaseApi", "getUserAlbums - loaded ${albums.size} albums")
            Result.success(albums)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getUserAlbums error: ${e.message}")
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
        val hasMore: Boolean,
        /** 云盘歌曲总数（服务端 count 字段） */
        val count: Int = 0,
        /** 本页服务端返回的原始条目数（用于推进 offset，即使部分解析失败） */
        val rawFetched: Int = 0
    )

    /**
     * 拉取一页云盘歌曲。
     * 与 SPlayer-Next 对齐：POST https://music.163.com/weapi/v1/cloud/get
     * （weapi 会把 /api/ 改写为 /weapi/，并在明文 JSON 里注入 csrf_token）
     */
    suspend fun getCloudDiskSongs(offset: Int = 0, limit: Int = 30): Result<CloudDiskResult> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val csrf = csrfTokenFromCookies()
            val reqData = """{"offset":$offset,"limit":$limit,"csrf_token":"$csrf","e_r":false}"""
            // 优先 SPlayer 同款 weapi 路径；失败再回退 /api/
            val urls = listOf(
                "https://music.163.com/weapi/v1/cloud/get",
                "https://music.163.com/api/v1/cloud/get"
            )
            var lastError: Exception? = Exception("cloud get: no url tried")
            for (url in urls) {
                val page = fetchCloudDiskPage(url, reqData, offset, limit)
                if (page.isSuccess) return@withContext page
                lastError = page.exceptionOrNull() as? Exception ?: lastError
                android.util.Log.w("NeteaseApi", "getCloudDiskSongs - $url failed: ${page.exceptionOrNull()?.message}, try next")
            }
            return@withContext Result.failure(lastError ?: Exception("云盘拉取失败"))
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getCloudDiskSongs error: ${e.message}")
            Result.failure(e)
        }
    }

    private fun fetchCloudDiskPage(
        url: String,
        reqData: String,
        offset: Int,
        limit: Int
    ): Result<CloudDiskResult> {
        val body = weapiForm(reqData)
        val request = Request.Builder()
            .url(url)
            .post(body)
            .header("Referer", "https://music.163.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Cookie", cookies)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .build()

        android.util.Log.d("NeteaseApi", "getCloudDiskSongs - $url offset: $offset, limit: $limit")

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            return Result.failure(Exception("HTTP ${response.code}"))
        }
        val responseBody = response.body?.string()
        if (responseBody.isNullOrEmpty()) {
            return Result.failure(Exception("Empty response"))
        }
        val json = gson.fromJson(responseBody, JsonObject::class.java)
            ?: return Result.failure(Exception("Failed to parse JSON"))

        if (json.has("code") && !json.get("code").isJsonNull && json.get("code").asInt != 200) {
            val msg = when {
                json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                json.has("msg") && !json.get("msg").isJsonNull -> json.get("msg").asString
                else -> "code=${json.get("code").asInt}"
            }
            return Result.failure(Exception("云盘接口错误: $msg"))
        }

        val count = when {
            json.has("count") && !json.get("count").isJsonNull && json.get("count").isJsonPrimitive ->
                json.get("count").asInt
            else -> 0
        }
        val data = when {
            json.has("data") && json.get("data").isJsonArray -> json.getAsJsonArray("data")
            json.has("data") && json.get("data").isJsonObject -> {
                val dataObj = json.getAsJsonObject("data")
                when {
                    dataObj.has("cloudList") && dataObj.get("cloudList").isJsonArray -> dataObj.getAsJsonArray("cloudList")
                    dataObj.has("list") && dataObj.get("list").isJsonArray -> dataObj.getAsJsonArray("list")
                    else -> null
                }
            }
            else -> null
        }
        if (data == null) {
            android.util.Log.e("NeteaseApi", "getCloudDiskSongs - unexpected body: $responseBody")
            return Result.failure(Exception("No data field in response"))
        }
        val rawFetched = data.size()
        val serverHasMore = when {
            json.has("hasMore") && !json.get("hasMore").isJsonNull -> {
                val el = json.get("hasMore")
                when {
                    el.isJsonPrimitive && el.asJsonPrimitive.isBoolean -> el.asBoolean
                    el.isJsonPrimitive && el.asJsonPrimitive.isNumber -> el.asInt != 0
                    else -> false
                }
            }
            else -> false
        }
        val hasMore = serverHasMore ||
            (count > 0 && offset + rawFetched < count) ||
            (rawFetched >= limit)
        android.util.Log.d(
            "NeteaseApi",
            "getCloudDiskSongs - serverHasMore: $serverHasMore, hasMore: $hasMore, count: $count, offset: $offset, limit: $limit, rawFetched: $rawFetched"
        )
        android.util.Log.d("NeteaseApi", "getCloudDiskSongs - received ${data.size()} items")

        val tracks = mutableListOf<Track>()
        for (i in 0 until data.size()) {
            try {
                val item = data.get(i).asJsonObject
                val song = if (item.has("simpleSong") && item.get("simpleSong").isJsonObject) item.getAsJsonObject("simpleSong") else item

                val songId = if (song.has("id") && !song.get("id").isJsonNull) song.get("id").asLong else 0L
                val songName = if (song.has("name") && !song.get("name").isJsonNull) song.get("name").asString else ""

                var artistName = "Unknown"
                val artistNames = mutableListOf<String>()
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
                val albumEl = when {
                    song.has("al") && song.get("al").isJsonObject -> song.getAsJsonObject("al")
                    song.has("album") && song.get("album").isJsonObject -> song.getAsJsonObject("album")
                    else -> null
                }
                if (albumEl != null) {
                    if (albumEl.has("name") && !albumEl.get("name").isJsonNull) {
                        albumName = albumEl.get("name").asString
                    }
                    if (albumEl.has("id") && !albumEl.get("id").isJsonNull) {
                        albumId = albumEl.get("id").asLong
                    }
                    if (albumEl.has("picUrl") && !albumEl.get("picUrl").isJsonNull) {
                        picUrl = albumEl.get("picUrl").asString
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
        android.util.Log.d("NeteaseApi", "getCloudDiskSongs - loaded ${tracks.size} tracks, hasMore: $hasMore, count: $count")
        return Result.success(CloudDiskResult(tracks, hasMore, count, rawFetched))
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

    suspend fun getSongLyric(songId: String, songTitle: String = "", songArtist: String = "", songAlbum: String = "", songDuration: Long = 0, artistNames: List<String> = emptyList()): Result<SongLyrics> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val json = fetchLyricJson(id)
                ?: return@withContext Result.failure(Exception("Empty response"))

            val lrc = json.lyricField("lrc")
            val yrc = json.lyricField("yrc")
            val tlyric = json.lyricField("tlyric")
            val ytlrc = json.lyricField("ytlrc")

            // 逐字歌词优先使用配套的 ytlrc（时间戳与 yrc 行边界对齐）
            val translation = if (ytlrc.isNotEmpty()) ytlrc else tlyric

            android.util.Log.d(
                "NeteaseApi",
                "getSongLyric - lrc=${lrc.length}, yrc=${yrc.length}, tlyric=${tlyric.length}, ytlrc=${ytlrc.length}"
            )

            // 统一交给 LyricParser 处理（各平台通用）
            val result = com.musichub.miuix.data.lyrics.LyricParser.parse(
                lrc = lrc,
                tlyric = translation,
                yrc = yrc,
                songTitle = songTitle,
                songArtist = songArtist,
                songAlbum = songAlbum,
                artistNames = artistNames
            )

            android.util.Log.d(
                "NeteaseApi",
                "getSongLyric - parsed ${result.lines.size} lines, " +
                    "wordLines=${result.lines.count { !it.words.isNullOrEmpty() }}, isPureMusic=${result.isPureMusic}"
            )
            Result.success(result)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getSongLyric error: ${e.message}")
            Result.failure(e)
        }
    }

    /** 未经解析的原始歌词字段（开发者选项使用）。 */
    data class RawLyrics(
        val lrc: String = "",
        val yrc: String = "",
        val tlyric: String = "",
        val ytlrc: String = ""
    )

    /** 获取网易云原始歌词（不经过任何处理）。 */
    suspend fun getRawLyric(songId: String): Result<RawLyrics> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_")
            val json = fetchLyricJson(id)
                ?: return@withContext Result.failure(Exception("Empty response"))
            Result.success(
                RawLyrics(
                    lrc = json.lyricField("lrc"),
                    yrc = json.lyricField("yrc"),
                    tlyric = json.lyricField("tlyric"),
                    ytlrc = json.lyricField("ytlrc")
                )
            )
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "getRawLyric error: ${e.message}")
            Result.failure(e)
        }
    }

    /** 读取 { "lrc": { "lyric": "..." } } 形式的歌词字段。 */
    private fun JsonObject.lyricField(name: String): String {
        if (!has(name) || get(name).isJsonNull) return ""
        val obj = getAsJsonObject(name)
        if (!obj.has("lyric") || obj.get("lyric").isJsonNull) return ""
        return obj.get("lyric").asString
    }

    /**
     * 获取歌词 JSON（优先新版接口，含逐字歌词）：
     * 1) /api/song/lyric/v1（eapi）：yrc 逐字歌词、ytlrc 逐字翻译
     * 2) 失败时回退 /weapi/song/lyric
     */
    private fun fetchLyricJson(id: String): JsonObject? {
        // 1) 客户端 API（与 SPlayer-Next 的 lyric_new 一致）
        try {
            val form = FormBody.Builder()
                .add("id", id)
                .add("cp", "false")
                .add("tv", "0")
                .add("lv", "0")
                .add("rv", "0")
                .add("kv", "0")
                .add("yv", "0")
                .add("ytv", "0")
                .add("yrv", "0")
                .build()
            val request = Request.Builder()
                .url("https://music.163.com/api/song/lyric/v1")
                .post(form)
                .header("Referer", "https://music.163.com")
                .header(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                        "Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.29.205117"
                )
                .header("Cookie", "os=pc; $cookies")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "api v1")?.let { return it }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - api v1 error: ${e.message}")
        }

        // 2) eapi（部分设备/网络环境走 eapi 更稳定）
        try {
            val eapiPath = "/api/song/lyric/v1"
            val reqData = """{"id":$id,"cp":false,"tv":0,"lv":0,"rv":0,"kv":0,"yv":0,"ytv":0,"yrv":0}"""
            val encrypted = NeteaseCrypto.eapi(eapiPath, reqData)
            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .build()
            val request = Request.Builder()
                .url("https://interface3.music.163.com/eapi/song/lyric/v1")
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", "os=pc; $cookies")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "eapi v1")?.let { return it }
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - eapi v1 error: ${e.message}")
        }

        // 3) 回退：旧版 weapi 接口
        return try {
            val reqData = """{"id":$id,"lv":-1,"tv":-1,"yv":-1,"ytv":-1,"yrv":-1,"csrf_token":""}"""
            val encrypted = NeteaseCrypto.weapi(reqData)
            val body = FormBody.Builder()
                .add("params", encrypted["params"]!!)
                .add("encSecKey", encrypted["encSecKey"]!!)
                .build()
            val request = Request.Builder()
                .url("https://music.163.com/weapi/song/lyric?csrf_token=")
                .post(body)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Cookie", cookies)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()
            executeLyricRequest(request, "weapi", requireLyric = false)
        } catch (e: Exception) {
            android.util.Log.e("NeteaseApi", "fetchLyricJson - weapi error: ${e.message}")
            null
        }
    }

    /** 执行歌词请求；[requireLyric] 为 true 时要求响应中至少含 lrc 或 yrc。 */
    private fun executeLyricRequest(
        request: Request,
        source: String,
        requireLyric: Boolean = true
    ): JsonObject? {
        val response = client.newCall(request).execute()
        val responseBody = if (response.isSuccessful) response.body?.string() else null
        if (responseBody.isNullOrEmpty()) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source empty, code=${response.code}")
            return null
        }
        val json = runCatching { gson.fromJson(responseBody, JsonObject::class.java) }.getOrNull()
        if (json == null) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source invalid JSON")
            return null
        }
        if (requireLyric &&
            json.lyricField("yrc").isEmpty() &&
            json.lyricField("lrc").isEmpty()
        ) {
            android.util.Log.d("NeteaseApi", "fetchLyricJson - $source no lyric content")
            return null
        }
        android.util.Log.d("NeteaseApi", "fetchLyricJson - $source success, length=${responseBody.length}")
        return json
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

    suspend fun getPersonalFM(): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val url = "https://music.163.com/api/v1/radio/get"
            val reqData = "{}"
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

            Log.d("NeteaseApi", "getPersonalFM - url: $url")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("NeteaseApi", "getPersonalFM - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("NeteaseApi", "getPersonalFM - Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }
            
            Log.d("NeteaseApi", "getPersonalFM - response: $responseBody")

            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            val tracks = mutableListOf<Track>()
            if (json.has("data") && json.get("data").isJsonArray) {
                val dataArray = json.getAsJsonArray("data")
                Log.d("NeteaseApi", "getPersonalFM - found ${dataArray.size()} songs")
                for (i in 0 until dataArray.size()) {
                    try {
                        val song = dataArray.get(i).asJsonObject
                        val songId = if (song.has("id")) song.get("id").asLong else 0L
                        val songName = if (song.has("name") && !song.get("name").isJsonNull) song.get("name").asString else ""

                        var artistName = ""
                        val artistNames = mutableListOf<String>()
                        val artistIds = mutableListOf<String>()
                        if (song.has("artists") && song.get("artists").isJsonArray) {
                            val artists = song.getAsJsonArray("artists")
                            for (j in 0 until artists.size()) {
                                val artist = artists.get(j).asJsonObject
                                if (artist.has("name") && !artist.get("name").isJsonNull) {
                                    artistNames.add(artist.get("name").asString)
                                }
                                if (artist.has("id")) {
                                    artistIds.add("neartist_${artist.get("id").asLong}")
                                }
                            }
                            artistName = artistNames.joinToString(" / ")
                        }

                        var albumName = ""
                        var albumId = 0L
                        var picUrl = ""
                        if (song.has("album") && song.get("album").isJsonObject) {
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

                        val duration = if (song.has("duration")) song.get("duration").asLong else 0L

                        tracks.add(
                            Track(
                                id = "netrack_$songId",
                                title = songName,
                                artist = artistName,
                                artistId = artistIds.firstOrNull() ?: "",
                                album = albumName,
                                albumId = "nealbum_$albumId",
                                sourceUrl = "https://music.163.com/#/song?id=$songId",
                                imgUrl = picUrl,
                                duration = duration,
                                isVip = false,
                                artistNames = artistNames,
                                artistIds = artistIds
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("NeteaseApi", "parseFMSong error: ${e.message}")
                    }
                }
            }

            Result.success(tracks)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getPersonalFM error: ${e.message}")
            Result.failure(e)
        }
    }

    /**
     * 心动模式 / 智能播放列表
     * 与 SPlayer-Next 对齐：
     * - weapi 加密，URL 为 https://music.163.com/weapi/playmode/intelligence/list
     * - 明文 JSON 注入 csrf_token / e_r
     * - type 先试 fromPlayOne，空结果再试 fromPlaylist
     * count 服务端必传，缺省 1，否则可能 500
     */
    suspend fun getIntelligenceList(songId: String, playlistId: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            ensureCookie()
            val id = songId.removePrefix("netrack_").toLongOrNull()
                ?: return@withContext Result.failure(Exception("Invalid seed songId: $songId"))
            val pid = playlistId.removePrefix("neplaylist_").toLongOrNull()
                ?: return@withContext Result.failure(Exception("Invalid playlistId: $playlistId（心动模式需要「我喜欢的音乐」歌单 ID）"))

            // 先 fromPlayOne（SPlayer），空结果再 fromPlaylist
            val first = fetchIntelligenceList(id, pid, type = "fromPlayOne")
            if (first.isSuccess && first.getOrNull()?.isNotEmpty() == true) {
                return@withContext first
            }
            Log.d("NeteaseApi", "getIntelligenceList - fromPlayOne empty/failed, retry fromPlaylist")
            val second = fetchIntelligenceList(id, pid, type = "fromPlaylist")
            if (second.isSuccess && second.getOrNull()?.isNotEmpty() == true) {
                return@withContext second
            }
            // 都空则返回更详细的原因
            return@withContext if (first.isFailure) first
            else if (second.isFailure) second
            else Result.success(emptyList())
        } catch (e: Exception) {
            Log.e("NeteaseApi", "getIntelligenceList error: ${e.message}")
            Result.failure(e)
        }
    }

    private suspend fun fetchIntelligenceList(songId: Long, playlistId: Long, type: String): Result<List<Track>> = withContext(Dispatchers.IO) {
        try {
            // SPlayer-Next: request("/api/playmode/intelligence/list", ..., weapi)
            // → https://music.163.com/weapi/playmode/intelligence/list
            val url = "https://music.163.com/weapi/playmode/intelligence/list"
            val csrf = csrfTokenFromCookies()
            val reqData =
                """{"songId":$songId,"type":"$type","playlistId":$playlistId,"startMusicId":$songId,"count":1,"csrf_token":"$csrf","e_r":false}"""
            val formBody = weapiForm(reqData)

            val cookieStr = "os=pc; appver=3.1.29.205117; ${cookies}"

            val request = Request.Builder()
                .url(url)
                .post(formBody)
                .header("Referer", "https://music.163.com")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("Cookie", cookieStr)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .build()

            Log.d("NeteaseApi", "fetchIntelligenceList - type=$type, songId=$songId, playlistId=$playlistId")
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.e("NeteaseApi", "getIntelligenceList - HTTP error: ${response.code}")
                return@withContext Result.failure(Exception("HTTP ${response.code}"))
            }
            val responseBody = response.body?.string()
            if (responseBody.isNullOrEmpty()) {
                Log.e("NeteaseApi", "getIntelligenceList - Empty response")
                return@withContext Result.failure(Exception("Empty response"))
            }

            Log.d("NeteaseApi", "fetchIntelligenceList - response: $responseBody")

            val json = gson.fromJson(responseBody, JsonObject::class.java)
                ?: return@withContext Result.failure(Exception("Failed to parse JSON"))

            if (json.has("code") && !json.get("code").isJsonNull && json.get("code").asInt != 200) {
                val msg = when {
                    json.has("message") && !json.get("message").isJsonNull -> json.get("message").asString
                    json.has("msg") && !json.get("msg").isJsonNull -> json.get("msg").asString
                    else -> "code=${json.get("code").asInt}"
                }
                Log.e("NeteaseApi", "fetchIntelligenceList - API error: $msg body=$responseBody")
                return@withContext Result.failure(Exception("心动模式接口错误: $msg"))
            }

            val tracks = mutableListOf<Track>()
            // 正常结构 data: [{ songInfo, recommended }]；也兼容 data 直接是 song 数组
            val dataArray: com.google.gson.JsonArray? = when {
                json.has("data") && json.get("data").isJsonArray -> json.getAsJsonArray("data")
                json.has("data") && json.get("data").isJsonObject -> {
                    val dataObj = json.getAsJsonObject("data")
                    when {
                        dataObj.has("songInfoList") && dataObj.get("songInfoList").isJsonArray ->
                            dataObj.getAsJsonArray("songInfoList")
                        dataObj.has("list") && dataObj.get("list").isJsonArray ->
                            dataObj.getAsJsonArray("list")
                        else -> null
                    }
                }
                else -> null
            }
            if (dataArray != null) {
                Log.d("NeteaseApi", "fetchIntelligenceList - found ${dataArray.size()} songs")
                for (i in 0 until dataArray.size()) {
                    try {
                        val item = dataArray.get(i).asJsonObject
                        val songInfo = when {
                            item.has("songInfo") && item.get("songInfo").isJsonObject -> item.getAsJsonObject("songInfo")
                            else -> item
                        }

                        val songIdVal = if (songInfo.has("id") && !songInfo.get("id").isJsonNull) songInfo.get("id").asLong else 0L
                        if (songIdVal == 0L) continue
                        val songName = if (songInfo.has("name") && !songInfo.get("name").isJsonNull) songInfo.get("name").asString else ""

                        var artistName = ""
                        val artistNames = mutableListOf<String>()
                        val artistIds = mutableListOf<String>()
                        val artistsEl = when {
                            songInfo.has("ar") && songInfo.get("ar").isJsonArray -> songInfo.getAsJsonArray("ar")
                            songInfo.has("artists") && songInfo.get("artists").isJsonArray -> songInfo.getAsJsonArray("artists")
                            else -> null
                        }
                        if (artistsEl != null) {
                            for (j in 0 until artistsEl.size()) {
                                val artist = artistsEl.get(j).asJsonObject
                                if (artist.has("name") && !artist.get("name").isJsonNull) {
                                    artistNames.add(artist.get("name").asString)
                                }
                                if (artist.has("id")) {
                                    artistIds.add("neartist_${artist.get("id").asLong}")
                                }
                            }
                            artistName = artistNames.joinToString(" / ")
                        }

                        var albumName = ""
                        var albumId = 0L
                        var picUrl = ""
                        val albumEl = when {
                            songInfo.has("al") && songInfo.get("al").isJsonObject -> songInfo.getAsJsonObject("al")
                            songInfo.has("album") && songInfo.get("album").isJsonObject -> songInfo.getAsJsonObject("album")
                            else -> null
                        }
                        if (albumEl != null) {
                            if (albumEl.has("name") && !albumEl.get("name").isJsonNull) {
                                albumName = albumEl.get("name").asString
                            }
                            if (albumEl.has("id") && !albumEl.get("id").isJsonNull) {
                                albumId = albumEl.get("id").asLong
                            }
                            if (albumEl.has("picUrl") && !albumEl.get("picUrl").isJsonNull) {
                                picUrl = albumEl.get("picUrl").asString
                            }
                        }

                        val duration = if (songInfo.has("dt")) songInfo.get("dt").asLong
                        else if (songInfo.has("duration")) songInfo.get("duration").asLong else 0L

                        tracks.add(
                            Track(
                                id = "netrack_$songIdVal",
                                title = songName,
                                artist = artistName,
                                artistId = artistIds.firstOrNull() ?: "",
                                album = albumName,
                                albumId = "nealbum_$albumId",
                                sourceUrl = "https://music.163.com/#/song?id=$songIdVal",
                                imgUrl = picUrl,
                                duration = duration,
                                isVip = false,
                                artistNames = artistNames,
                                artistIds = artistIds
                            )
                        )
                    } catch (e: Exception) {
                        Log.e("NeteaseApi", "parseIntelligenceSong error: ${e.message}")
                    }
                }
            } else {
                Log.e("NeteaseApi", "fetchIntelligenceList - no data array, body=$responseBody")
            }

            Result.success(tracks)
        } catch (e: Exception) {
            Log.e("NeteaseApi", "fetchIntelligenceList error: ${e.message}")
            Result.failure(e)
        }
    }
}
