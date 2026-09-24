package com.cloudmusic.miuix.data.api

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Apple Music API 工具类
 * 用于获取Apple Music的封面图片（包括动态封面）
 */
object AppleMusicApi {
    private const val TAG = "AppleMusicApi"
    
    // 站点 JS 包很大（index~*.js 约 3MB），超时需放宽，否则拿不到 JWT
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
    
    // 缓存JWT Token
    private var cachedJwt: String? = null
    private var jwtExpireAt: Long = 0
    
    // 更健壮的JS匹配：允许src=前后有空格/换行，匹配所有.js路径
    private val jsSrcRegex = Regex("""src\s*=\s*"([^"]+\.js)"""")
    
    // 匹配完整三段式JWT，不限制开头
    private val jwtRegex = Regex("""eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+""")
    
    private const val BROWSER_UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) " +
            "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
    
    /**
     * Apple Music封面结果
     */
    data class AppleMusicCover(
        val artworkUrl: String,  // 封面图片URL
        val isAnimated: Boolean = false // 是否是动态封面
    )
    
    /**
     * 通过歌曲名和歌手名搜索Apple Music封面
     */
    suspend fun searchCover(songName: String, artistName: String): AppleMusicCover? = withContext(Dispatchers.IO) {
        Log.d(TAG, "=== 开始搜索Apple Music封面 ===")
        Log.d(TAG, "歌曲名: $songName, 歌手名: $artistName")
        
        try {
            // 1. 使用iTunes Lookup API搜索歌曲
            Log.d(TAG, "步骤1: 搜索歌曲ID...")
            val trackId = lookupTrackId(songName, artistName)
            if (trackId == null) {
                Log.w(TAG, "步骤1失败: 未找到匹配的歌曲")
                return@withContext null
            }
            Log.d(TAG, "步骤1成功: 找到歌曲ID = $trackId")
            
            // 2. 获取专辑ID
            Log.d(TAG, "步骤2: 获取专辑ID...")
            val albumId = lookupAlbumId(trackId)
            if (albumId == null) {
                Log.w(TAG, "步骤2失败: 未找到专辑ID")
                return@withContext null
            }
            Log.d(TAG, "步骤2成功: 找到专辑ID = $albumId")
            
            // 3. 获取JWT Token
            Log.d(TAG, "步骤3: 获取JWT Token...")
            val jwtToken = getValidJwt()
            if (jwtToken == null) {
                Log.w(TAG, "步骤3失败: 无法获取JWT Token")
                return@withContext null
            }
            Log.d(TAG, "步骤3成功: 获取到有效JWT Token")
            
            // 4. 调用amp-api获取封面
            Log.d(TAG, "步骤4: 获取专辑封面...")
            val cover = getAlbumCover(albumId, jwtToken)
            if (cover == null) {
                Log.w(TAG, "步骤4失败: 未找到封面，尝试使用iTunes静态封面")
                // 降级：使用iTunes静态封面
                val staticCover = getItunesStaticCover(trackId)
                if (staticCover != null) {
                    Log.d(TAG, "降级成功: 使用iTunes静态封面")
                    return@withContext staticCover
                }
            } else {
                Log.d(TAG, "步骤4成功: 找到封面, isAnimated=${cover.isAnimated}")
            }
            
            Log.d(TAG, "=== 搜索Apple Music封面完成 ===")
            cover
        } catch (e: Exception) {
            Log.e(TAG, "搜索Apple Music封面异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 通过iTunes Lookup API搜索歌曲ID
     */
    private fun lookupTrackId(songName: String, artistName: String): String? {
        try {
            val query = java.net.URLEncoder.encode("$songName $artistName", "UTF-8")
            val url = "https://itunes.apple.com/search?term=$query&media=music&entity=song&limit=5&country=cn"
            Log.d(TAG, "iTunes搜索URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CloudMusicMiuix/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            Log.d(TAG, "iTunes搜索响应: HTTP ${response.code}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "iTunes搜索失败: HTTP ${response.code}")
                return null
            }
            
            val body = response.body?.string() ?: run {
                Log.e(TAG, "iTunes搜索响应体为空")
                return null
            }
            
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            
            if (!json.has("results")) {
                Log.w(TAG, "iTunes搜索结果中没有results字段")
                return null
            }
            
            val results = json.getAsJsonArray("results")
            Log.d(TAG, "iTunes搜索结果数量: ${results.size()}")
            
            // 第一轮：查找精确匹配的歌曲
            for (i in 0 until results.size()) {
                val track = results.get(i).asJsonObject
                val trackName = track.get("trackName")?.asString ?: continue
                val artistNameFromApi = track.get("artistName")?.asString ?: continue
                
                Log.d(TAG, "检查结果[$i]: trackName=$trackName, artistName=$artistNameFromApi")
                
                // 精确匹配歌名和歌手
                if (trackName.equals(songName, ignoreCase = true) && 
                    artistNameFromApi.equals(artistName, ignoreCase = true)) {
                    val trackId = track.get("trackId")?.asString
                    Log.d(TAG, "找到精确匹配歌曲: trackId=$trackId")
                    return trackId
                }
            }
            
            // 第二轮：查找模糊匹配的歌曲（检查所有结果）
            for (i in 0 until results.size()) {
                val track = results.get(i).asJsonObject
                val trackName = track.get("trackName")?.asString ?: continue
                val artistNameFromApi = track.get("artistName")?.asString ?: continue
                
                Log.d(TAG, "检查模糊匹配[$i]: trackName=$trackName, artistName=$artistNameFromApi")
                
                // 歌手必须匹配
                if (!artistNameFromApi.equals(artistName, ignoreCase = true)) {
                    continue
                }
                
                // 获取去除括号后的歌名
                val songNameNoBrackets = removeBrackets(songName)
                val trackNameNoBrackets = removeBrackets(trackName)
                
                Log.d(TAG, "去除括号后: 原歌名=$songNameNoBrackets, 搜索结果=$trackNameNoBrackets")
                
                // 模糊匹配规则：
                // 1. B包含A（例如 A="Shape of You", B="Shape of You (Remix)"）
                // 2. A包含B（例如 A="Shape of You (Live)", B="Shape of You"）
                // 3. AB非括号部分相同（例如 A="Shape of You", B="Shape of You (Remix)"）
                val isMatch = trackName.contains(songName, ignoreCase = true) ||
                              songName.contains(trackName, ignoreCase = true) ||
                              songNameNoBrackets.equals(trackNameNoBrackets, ignoreCase = true)
                
                if (isMatch) {
                    val trackId = track.get("trackId")?.asString
                    Log.d(TAG, "找到模糊匹配歌曲: trackId=$trackId, 匹配规则: 包含关系或非括号部分相同")
                    return trackId
                }
            }
            
            Log.w(TAG, "未找到匹配的歌曲")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "iTunes搜索异常: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 去除字符串中的括号及其内容
     * 支持英文括号()和中文括号（）
     */
    private fun removeBrackets(text: String): String {
        // 去除英文括号及其内容
        var result = text.replace(Regex("\\s*\\([^)]*\\)\\s*"), " ")
        // 去除中文括号及其内容
        result = result.replace(Regex("\\s*（[^）]*）\\s*"), " ")
        // 去除多余空格并trim
        return result.replace(Regex("\\s+"), " ").trim()
    }
    
    /**
     * 通过歌曲ID获取专辑ID
     */
    private fun lookupAlbumId(trackId: String): String? {
        try {
            val url = "https://itunes.apple.com/lookup?id=$trackId&entity=album&country=cn"
            Log.d(TAG, "iTunes专辑查询URL: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CloudMusicMiuix/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            Log.d(TAG, "iTunes专辑查询响应: HTTP ${response.code}")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "iTunes专辑查询失败: HTTP ${response.code}")
                return null
            }
            
            val body = response.body?.string() ?: run {
                Log.e(TAG, "iTunes专辑查询响应体为空")
                return null
            }
            
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            
            if (!json.has("results")) {
                Log.w(TAG, "iTunes专辑查询结果中没有results字段")
                return null
            }
            
            val results = json.getAsJsonArray("results")
            Log.d(TAG, "iTunes专辑查询结果数量: ${results.size()}")
            
            for (i in 0 until results.size()) {
                val item = results.get(i).asJsonObject
                val wrapperType = item.get("wrapperType")?.asString
                
                if (wrapperType == "collection") {
                    val albumId = item.get("collectionId")?.asString
                    val albumName = item.get("collectionName")?.asString
                    Log.d(TAG, "找到专辑: albumId=$albumId, albumName=$albumName")
                    return albumId
                }
            }
            
            Log.w(TAG, "未找到专辑信息")
            return null
        } catch (e: Exception) {
            Log.e(TAG, "iTunes专辑查询异常: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 获取有效的WebPlayKid JWT
     */
    private suspend fun getValidJwt(): String? = withContext(Dispatchers.IO) {
        // 缓存未过期直接返回
        if (cachedJwt != null && System.currentTimeMillis() < jwtExpireAt) {
            Log.d(TAG, "使用缓存的JWT Token")
            return@withContext cachedJwt
        }
        
        // 优先试beta美区，失败再试正式站
        Log.d(TAG, "尝试从beta站点获取Token...")
        val token = fetchTokenFromBeta()
        if (token != null) {
            Log.d(TAG, "从beta站点获取Token成功")
            cachedJwt = token
            jwtExpireAt = System.currentTimeMillis() + 25 * 60 * 1000L
            return@withContext token
        }
        
        Log.d(TAG, "beta站点失败，尝试正式站点...")
        val officialToken = fetchTokenFromOfficial()
        if (officialToken != null) {
            Log.d(TAG, "从正式站点获取Token成功")
            cachedJwt = officialToken
            jwtExpireAt = System.currentTimeMillis() + 25 * 60 * 1000L
            return@withContext officialToken
        }
        
        Log.e(TAG, "无法从任何站点获取Token")
        null
    }
    
    /**
     * 从beta站点获取Token
     */
    private fun fetchTokenFromBeta(): String? {
        return try {
            Log.d(TAG, "请求beta站点: https://beta.music.apple.com/us")
            val homeHtml = fetchString("https://beta.music.apple.com/us")
            Log.d(TAG, "beta站点HTML长度: ${homeHtml.length}")
            
            if (homeHtml.isEmpty()) {
                Log.w(TAG, "beta站点HTML为空")
                return null
            }
            
            val jsPaths = jsSrcRegex.findAll(homeHtml)
                .map { it.groupValues[1] }
                .filter { it.contains("/assets/") || it.contains("index") }
                .toList()

            Log.d(TAG, "beta站点找到 ${jsPaths.size} 个JS文件")

            // 先扫 HTML 本身（有时 token 会内联）
            findValidJwt(homeHtml)?.let { return it }

            for (jsPath in jsPaths) {
                Log.d(TAG, "检查JS文件: $jsPath")
                val fullUrl = if (jsPath.startsWith("http")) jsPath else "https://beta.music.apple.com$jsPath"
                val jsContent = fetchString(fullUrl)
                Log.d(TAG, "JS文件内容长度: ${jsContent.length}")

                val validJwt = findValidJwt(jsContent)
                if (validJwt != null) {
                    Log.d(TAG, "在JS文件中找到有效Token")
                    return validJwt
                }
            }

            Log.w(TAG, "在beta站点JS文件中未找到有效Token")
            null
        } catch (e: Exception) {
            Log.e(TAG, "从beta站点获取Token异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从正式站点获取Token
     */
    private fun fetchTokenFromOfficial(): String? {
        return try {
            Log.d(TAG, "请求正式站点: https://music.apple.com/cn")
            val homeHtml = fetchString("https://music.apple.com/cn")
            Log.d(TAG, "正式站点HTML长度: ${homeHtml.length}")
            
            if (homeHtml.isEmpty()) {
                Log.w(TAG, "正式站点HTML为空")
                return null
            }
            
            // 新版站点是 Vite 产物：/assets/index~xxxx.js（JWT/WebPlayKid 在这里）
            val jsPaths = jsSrcRegex.findAll(homeHtml)
                .map { it.groupValues[1] }
                .filter { it.endsWith(".js") && (it.contains("/assets/") || it.contains("index")) }
                .sortedByDescending { if (it.contains("index~") || it.contains("/assets/index")) 1 else 0 }
                .toList()

            Log.d(TAG, "正式站点找到 ${jsPaths.size} 个JS文件: $jsPaths")

            findValidJwt(homeHtml)?.let { return it }

            for (jsPath in jsPaths) {
                Log.d(TAG, "检查JS文件: $jsPath")
                val fullUrl = when {
                    jsPath.startsWith("http") -> jsPath
                    jsPath.startsWith("//") -> "https:$jsPath"
                    jsPath.startsWith("/") -> "https://music.apple.com$jsPath"
                    else -> "https://music.apple.com/$jsPath"
                }
                val jsContent = fetchString(fullUrl)
                Log.d(TAG, "JS文件内容长度: ${jsContent.length}")

                val validJwt = findValidJwt(jsContent)
                if (validJwt != null) {
                    Log.d(TAG, "在JS文件中找到有效Token, kid尝试通过")
                    return validJwt
                }
            }
            
            Log.w(TAG, "在正式站点JS文件中未找到有效Token")
            null
        } catch (e: Exception) {
            Log.e(TAG, "从正式站点获取Token异常: ${e.message}", e)
            null
        }
    }
    
    /**
     * 从JS中找出kid=WebPlayKid的有效JWT
     */
    private fun findValidJwt(jsContent: String): String? {
        val allJwts = jwtRegex.findAll(jsContent).map { it.value }.toList()
        Log.d(TAG, "找到 ${allJwts.size} 个JWT Token")

        // 优先 WebPlayKid
        for ((index, jwt) in allJwts.withIndex()) {
            Log.d(TAG, "检查JWT[$index]: ${jwt.take(50)}...")
            if (isWebPlayKidToken(jwt)) {
                Log.d(TAG, "找到有效的WebPlayKid Token!")
                return jwt
            }
        }

        // 站点改版后 kid 可能变化：退回任一可用三段式 JWT
        val fallback = allJwts.firstOrNull { it.split(".").size == 3 }
        if (fallback != null) {
            Log.d(TAG, "未找到 WebPlayKid，使用候选 Token: ${fallback.take(40)}...")
        }
        return fallback
    }
    
    /**
     * 解码JWT Header，验证kid是否为WebPlayKid
     */
    private fun isWebPlayKidToken(jwt: String): Boolean {
        return try {
            val headerPart = jwt.split(".")[0]
            val padded = headerPart + "=".repeat((4 - headerPart.length % 4) % 4)
            val decoded = String(Base64.decode(padded, Base64.URL_SAFE))
            val headerJson = JSONObject(decoded)
            val kid = headerJson.optString("kid")
            Log.d(TAG, "JWT Header kid: $kid")
            kid == "WebPlayKid" || kid.contains("WebPlay", ignoreCase = true) || kid.contains("PlayKid", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "解析JWT Header失败: ${e.message}")
            false
        }
    }
    
    /**
     * 获取专辑封面
     */
    private fun getAlbumCover(albumId: String, jwtToken: String): AppleMusicCover? {
        try {
            val url = "https://amp-api.music.apple.com/v1/catalog/cn/albums/$albumId?extend=editorialVideo"
            Log.d(TAG, "调用amp-api获取封面: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $jwtToken")
                .header("Origin", "https://music.apple.com")
                .header("Referer", "https://music.apple.com/cn")
                .header("User-Agent", BROWSER_UA)
                .build()
            
            Log.d(TAG, "请求头: Authorization=Bearer ${jwtToken.take(30)}...")
            
            val response = client.newCall(request).execute()
            Log.d(TAG, "amp-api响应: HTTP ${response.code}")
            
            // 401代表Token过期，强制刷新后重试一次
            if (response.code == 401) {
                Log.w(TAG, "Token已过期(401)，清除缓存并重试")
                clearCache()
                val newToken = runBlocking { getValidJwt() } ?: return null
                val newRequest = request.newBuilder()
                    .header("Authorization", "Bearer $newToken")
                    .build()
                val newResponse = client.newCall(newRequest).execute()
                Log.d(TAG, "重试响应: HTTP ${newResponse.code}")
                
                if (!newResponse.isSuccessful) {
                    Log.e(TAG, "重试仍然失败: HTTP ${newResponse.code}")
                    return null
                }
                
                return parseCoverResponse(newResponse.body?.string())
            }
            
            if (!response.isSuccessful) {
                Log.e(TAG, "amp-api请求失败: HTTP ${response.code}")
                val errorBody = response.body?.string()
                if (errorBody != null) {
                    Log.e(TAG, "错误响应: ${errorBody.take(500)}")
                }
                return null
            }
            
            return parseCoverResponse(response.body?.string())
        } catch (e: Exception) {
            Log.e(TAG, "获取专辑封面异常: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 解析封面响应
     */
    private fun parseCoverResponse(body: String?): AppleMusicCover? {
        if (body == null) {
            Log.e(TAG, "响应体为空")
            return null
        }
        
        Log.d(TAG, "响应体长度: ${body.length}")
        
        val json = JSONObject(body)
        
        if (!json.has("data")) {
            Log.w(TAG, "响应中没有data字段")
            return null
        }
        
        val data = json.getJSONArray("data")
        Log.d(TAG, "data数组大小: ${data.length()}")
        
        if (data.length() == 0) {
            Log.w(TAG, "data数组为空")
            return null
        }
        
        val album = data.getJSONObject(0)
        
        if (!album.has("attributes")) {
            Log.w(TAG, "专辑没有attributes字段")
            return null
        }
        
        val attributes = album.getJSONObject("attributes")
        Log.d(TAG, "attributes字段: ${attributes.keys().asSequence().toList()}")
        
        // 检查是否有动态封面（兼容多种 editorialVideo 字段名）
        if (attributes.has("editorialVideo")) {
            Log.d(TAG, "找到editorialVideo字段（有动态封面）")
            val editorialVideo = attributes.getJSONObject("editorialVideo")
            val motionKeys = listOf(
                "motionDetailSquare",
                "motionDetailSquare1x1",
                "motionDetailTall",
                "motionTall",
                "motionSquare",
                "motionArtist",
                "motionWide"
            )
            for (key in motionKeys) {
                if (!editorialVideo.has(key)) continue
                val motion = editorialVideo.getJSONObject(key)
                val videoUrl = sequenceOf("video", "hlsUrl", "url", "preview")
                    .mapNotNull { k -> if (motion.has(k) && !motion.isNull(k)) motion.optString(k) else null }
                    .firstOrNull { it.isNotEmpty() }
                if (videoUrl != null) {
                    Log.d(TAG, "找到动态封面视频URL($key): $videoUrl")
                    return AppleMusicCover(artworkUrl = videoUrl, isAnimated = true)
                }
            }
        } else {
            Log.d(TAG, "专辑没有editorialVideo字段（无动态封面）")
        }
        
        // 获取静态封面
        if (attributes.has("artwork")) {
            val artwork = attributes.getJSONObject("artwork")
            val urlTemplate = artwork.optString("url")
            if (urlTemplate.isNotEmpty()) {
                val imageUrl = urlTemplate
                    .replace("{w}", "1024")
                    .replace("{h}", "1024")
                    .replace("{f}", "jpg")
                Log.d(TAG, "找到静态封面URL: $imageUrl")
                return AppleMusicCover(artworkUrl = imageUrl, isAnimated = false)
            }
        }
        
        Log.w(TAG, "未找到任何封面信息")
        return null
    }
    
    /**
     * 获取iTunes静态封面（降级方案）
     */
    private fun getItunesStaticCover(trackId: String): AppleMusicCover? {
        try {
            val url = "https://itunes.apple.com/lookup?id=$trackId&country=cn"
            Log.d(TAG, "获取iTunes静态封面: $url")
            
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "CloudMusicMiuix/1.0")
                .build()
            
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null
            
            val body = response.body?.string() ?: return null
            val json = com.google.gson.JsonParser.parseString(body).asJsonObject
            
            if (!json.has("results")) return null
            val results = json.getAsJsonArray("results")
            
            for (i in 0 until results.size()) {
                val item = results.get(i).asJsonObject
                if (item.has("artworkUrl100")) {
                    val artworkUrl = item.get("artworkUrl100").asString
                    // 替换为高清版本
                    val hdUrl = artworkUrl.replace("100x100", "1024x1024")
                    Log.d(TAG, "iTunes静态封面URL: $hdUrl")
                    return AppleMusicCover(artworkUrl = hdUrl, isAnimated = false)
                }
            }
            
            return null
        } catch (e: Exception) {
            Log.e(TAG, "获取iTunes静态封面异常: ${e.message}", e)
            return null
        }
    }
    
    /**
     * 清除缓存的JWT Token
     */
    fun clearCache() {
        Log.d(TAG, "清除JWT Token缓存")
        cachedJwt = null
        jwtExpireAt = 0
    }
    
    /**
     * 获取网页内容
     */
    private fun fetchString(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", BROWSER_UA)
            .build()
        return client.newCall(request).execute().body?.string().orEmpty()
    }
    
    /**
     * 同步获取有效JWT（用于重试逻辑）
     */
    private fun runBlocking(block: suspend () -> String?): String? {
        return kotlinx.coroutines.runBlocking { block() }
    }
}
