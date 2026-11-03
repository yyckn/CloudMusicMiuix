package com.hypermusic.miuix.data.api

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.data.model.RecommendPlaylist
import com.hypermusic.miuix.data.model.Playlist
import com.hypermusic.miuix.data.model.PlaylistDetail
import com.hypermusic.miuix.data.model.ArtistInfo
import com.hypermusic.miuix.data.model.AlbumInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** QQ Music requests are kept separate from the NetEase account and cookies. */
object QqMusicApi {
    private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).build()
    private val audioProbeClient = client.newBuilder().callTimeout(4, TimeUnit.SECONDS).build()
    private var preferences: android.content.SharedPreferences? = null
    data class Credential(val musicId: String, val musicKey: String, val encryptUin: String, val loginType: Int)
    data class Account(val credential: Credential, val name: String, val avatarUrl: String = "")
    private val gson = com.google.gson.Gson()
    private var credential: Credential? = null
    var accounts by androidx.compose.runtime.mutableStateOf<List<Account>>(emptyList())
        private set
    var dataAccountId by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var playbackAccountId by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set
    var accountName by androidx.compose.runtime.mutableStateOf<String?>(null)
        private set

    @Synchronized
    fun init(context: android.content.Context) {
        preferences = context.applicationContext.getSharedPreferences("qqmusic_prefs", android.content.Context.MODE_PRIVATE)
        val prefs = preferences ?: return
        accounts = runCatching {
            val type = object : com.google.gson.reflect.TypeToken<List<Account>>() {}.type
            gson.fromJson<List<Account>>(prefs.getString("accounts", "[]"), type)
                .orEmpty().filter { it.credential.musicId.isNotBlank() }
                .distinctBy { it.credential.musicId }
        }.getOrDefault(emptyList())
        // Migrate the original single-account installation without logging the user out.
        val legacy = runCatching { gson.fromJson(prefs.getString("credential", null), Credential::class.java) }.getOrNull()
        if (legacy != null && legacy.musicId.isNotBlank() && accounts.none { it.credential.musicId == legacy.musicId }) {
            accounts = accounts + Account(legacy, prefs.getString("account_name", null) ?: legacy.musicId)
        }
        dataAccountId = prefs.getString("data_account_id", null)?.takeIf { id -> accounts.any { it.credential.musicId == id } }
            ?: legacy?.musicId?.takeIf { id -> accounts.any { it.credential.musicId == id } }
            ?: accounts.firstOrNull()?.credential?.musicId
        playbackAccountId = prefs.getString("playback_account_id", null)?.takeIf { id -> accounts.any { it.credential.musicId == id } }
            ?: dataAccountId
        updateActiveAccount()
        persistAccounts()
    }

    private fun updateActiveAccount() {
        val active = accounts.find { it.credential.musicId == dataAccountId }
        credential = active?.credential
        accountName = active?.name
    }

    @Synchronized
    private fun persistAccounts() {
        preferences?.edit()?.putString("accounts", gson.toJson(accounts))
            ?.putString("data_account_id", dataAccountId)?.putString("playback_account_id", playbackAccountId)
            ?.remove("credential")?.remove("account_name")?.apply()
    }

    @Synchronized
    private fun saveAccount(auth: Credential, name: String, avatarUrl: String = "", replace: Boolean = false, enableMulti: Boolean = false) {
        val previous = accounts.find { it.credential.musicId == auth.musicId }
        val account = Account(auth, name, avatarUrl.ifBlank { previous?.avatarUrl.orEmpty() })
        val updated = AccountImportPolicy.merge(accounts, account, replace) { it.credential.musicId }
        val playbackId = if (replace || playbackAccountId == null || previous == null) auth.musicId else playbackAccountId
        val edit = requireNotNull(preferences).edit().putString("accounts", gson.toJson(updated))
            .putString("data_account_id", auth.musicId).putString("playback_account_id", playbackId)
            .remove("credential").remove("account_name")
        if (enableMulti) edit.putBoolean("multi_account_enabled", true)
        check(edit.commit()) { "保存账号失败" }
        accounts = updated
        dataAccountId = auth.musicId
        playbackAccountId = playbackId
        updateActiveAccount()
    }

    @Synchronized
    fun switchDataAccount(id: String): Boolean {
        if (accounts.none { it.credential.musicId == id }) return false
        dataAccountId = id
        updateActiveAccount()
        persistAccounts()
        return true
    }

    @Synchronized
    fun switchPlaybackAccount(id: String): Boolean {
        if (accounts.none { it.credential.musicId == id }) return false
        playbackAccountId = id
        persistAccounts()
        return true
    }

    fun isLoggedIn(): Boolean = credential != null
    @Synchronized
    fun logout() {
        val removed = dataAccountId
        accounts = accounts.filterNot { it.credential.musicId == removed }
        dataAccountId = accounts.firstOrNull()?.credential?.musicId
        if (playbackAccountId == removed) playbackAccountId = dataAccountId
        updateActiveAccount()
        persistAccounts()
    }

    data class UserProfile(
        val userId: String,
        val name: String,
        val avatarUrl: String,
        val isVip: Boolean?,
        val vipName: String?,
        val vipLevel: Int?,
        val vipExpires: String?,
        val signature: String?
    )

    fun currentAccount(): Account? = accounts.find { it.credential.musicId == dataAccountId }

    fun exportCredential(): String? = credential?.let { auth ->
        JsonObject().apply {
            addProperty("musicid", auth.musicId)
            addProperty("musickey", auth.musicKey)
            addProperty("encryptUin", auth.encryptUin)
            addProperty("loginType", auth.loginType)
        }.toString()
    }

    @Synchronized
    private fun updateAccountProfile(auth: Credential, name: String, avatar: String) {
        val current = accounts.find { it.credential.musicId == auth.musicId } ?: return
        if (current.credential != auth) return
        accounts = accounts.map {
            if (it.credential.musicId == auth.musicId) it.copy(name = name, avatarUrl = avatar.ifBlank { it.avatarUrl }) else it
        }
        if (credential == auth) accountName = name
        persistAccounts()
    }

    suspend fun getProfile(): Result<UserProfile> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = credential ?: error("请先登录QQ音乐")
            check(auth.encryptUin.isNotBlank()) { "QQ音乐登录凭据缺少用户标识，请重新登录" }
            val info = request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader", JsonObject().apply {
                addProperty("uin", auth.encryptUin)
                addProperty("IsQueryTabDetail", 1)
            }, auth)
            val base = info.getAsJsonObject("Info")?.getAsJsonObject("BaseInfo") ?: error("QQ音乐未返回账号资料")
            val name = base.text("Name").ifBlank { auth.musicId }
            val avatar = base.text("Avatar").ifBlank { base.text("HeadUrl") }.replace("http://", "https://")
            updateAccountProfile(auth, name, avatar)
            val vip = runCatching { request("VipLogin.VipLoginInter", "vip_login_base", JsonObject(), auth) }.getOrNull()
            val identity = vip?.getAsJsonObject("identity")
            val userinfo = vip?.getAsJsonObject("userinfo")
            fun JsonObject.flag(key: String): Boolean = text(key).let { (it.toIntOrNull() ?: 0) > 0 || it.equals("true", true) }
            val known = identity != null && listOf("vip", "svip", "HugeVip").any { identity.has(it) }
            val huge = identity?.flag("HugeVip") == true || identity?.flag("svip") == true || vip?.flag("svip") == true
            val legacyVip = (userinfo?.text("music_level")?.toIntOrNull() ?: 0) > 0
            val isVip = when {
                known -> huge || identity?.flag("vip") == true
                huge || legacyVip -> true
                else -> null
            }
            val level = identity?.text("level")?.toIntOrNull()?.takeIf { it > 0 }
                ?: userinfo?.text("music_level")?.toIntOrNull()?.takeIf { it > 0 }
            val expires = identity?.text("HugeVipEnd")?.takeIf { huge && it.isNotBlank() && it != "0" }
                ?: identity?.text("overdate")?.takeIf { it.isNotBlank() && it != "0" }
                ?: userinfo?.text("expire")?.takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
            val signature = runCatching {
                request("music.recommend.UserProfileSettingSvr", "GetProfileReport", JsonObject().apply {
                    addProperty("VisitAccount", auth.encryptUin)
                }, auth).getAsJsonObject("UserInfoCard")?.text("Signature")?.takeIf { it.isNotBlank() }
            }.getOrNull()
            UserProfile(auth.musicId, name, avatar.ifBlank { accounts.find { it.credential.musicId == auth.musicId }?.avatarUrl.orEmpty() },
                isVip, if (isVip == true) if (huge) "豪华绿钻" else "绿钻会员" else null, level, expires, signature)
        }
    }

    suspend fun refreshProfile(): Result<String> = getProfile().map { it.name }

    suspend fun importCredential(raw: String, replace: Boolean = false, enableMulti: Boolean = false): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val data = JsonParser.parseString(raw).asJsonObject
            val id = data.text("musicid").ifBlank { data.text("str_musicid") }
            val key = data.text("musickey")
            val encrypted = data.text("encryptUin").ifBlank { data.text("encrypt_uin") }
            require(id.toLongOrNull()?.let { it > 0 } == true && key.isNotBlank() && encrypted.isNotBlank()) {
                "需要 musicid、musickey 和 encryptUin"
            }
            val candidate = Credential(id, key, encrypted,
                data.text("loginType").ifBlank { data.text("login_type") }.toIntOrNull() ?: if (key.startsWith("W_X")) 1 else 2)
            val validation = request("music.UserInfo.userInfoServer", "GetLoginUserInfo", JsonObject(), candidate)
            check(validation.text("code").let { it.isBlank() || it == "0" }) { "QQ音乐登录凭据已失效" }
            val info = request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader", JsonObject().apply {
                addProperty("uin", encrypted); addProperty("IsQueryTabDetail", 1)
            }, candidate)
            val base = info.getAsJsonObject("Info")?.getAsJsonObject("BaseInfo") ?: error("QQ音乐未返回账号资料，请检查登录凭据")
            val name = base.text("Name").ifBlank { id }
            withContext(Dispatchers.Main) { saveAccount(candidate, name, base.text("Avatar").ifBlank { base.text("HeadUrl") }, replace, enableMulti) }
            name
        }
    }

    data class QrCode(val image: ByteArray, val signature: String, val loginType: Int = 2)
    private val loginClient = client.newBuilder().followRedirects(false).followSslRedirects(false).build()

    suspend fun createQrCode(): Result<QrCode> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://ssl.ptlogin2.qq.com/ptqrshow".toHttpUrl().newBuilder().apply {
                mapOf("appid" to "716027609", "e" to "2", "l" to "M", "s" to "3", "d" to "72", "v" to "4",
                    "t" to Math.random().toString(), "daid" to "383", "pt_3rd_aid" to "100497308")
                    .forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            loginClient.newCall(Request.Builder().url(url).header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().use { response ->
                check(response.isSuccessful) { "获取QQ二维码失败 (${response.code})" }
                val sig = response.headers.values("Set-Cookie").mapNotNull { it.substringBefore(';').split('=', limit = 2).takeIf { parts -> parts.size == 2 && parts[0] == "qrsig" }?.get(1) }.firstOrNull()
                    ?: error("二维码响应缺少标识")
                QrCode(response.body?.bytes() ?: error("二维码为空"), sig)
            }
        }
    }

    suspend fun createWechatQrCode(): Result<QrCode> = withContext(Dispatchers.IO) {
        runCatching {
            val url = "https://open.weixin.qq.com/connect/qrconnect".toHttpUrl().newBuilder().apply {
                mapOf("appid" to "wx48db31d50e334801",
                    "redirect_uri" to "https://y.qq.com/portal/wx_redirect.html?login_type=2&surl=https://y.qq.com/",
                    "response_type" to "code", "scope" to "snsapi_login", "state" to "STATE",
                    "href" to "https://y.qq.com/mediastyle/music_v17/src/css/popup_wechat.css#wechat_redirect")
                    .forEach { (key, value) -> addQueryParameter(key, value) }
            }.build()
            val html = loginClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                check(response.isSuccessful) { "获取微信二维码失败 (${response.code})" }
                response.body?.string().orEmpty()
            }
            val uuid = Regex("uuid=([A-Za-z0-9_-]+)").find(html)?.groupValues?.get(1)
                ?: error("微信二维码响应缺少标识")
            val imageUrl = "https://open.weixin.qq.com/connect/qrcode/$uuid"
            val bytes = loginClient.newCall(Request.Builder().url(imageUrl)
                .header("Referer", "https://open.weixin.qq.com/connect/qrconnect").build()).execute().use { response ->
                check(response.isSuccessful) { "获取微信二维码图片失败 (${response.code})" }
                response.body?.bytes() ?: error("微信二维码图片为空")
            }
            QrCode(bytes, uuid, 1)
        }
    }

    private val wechatPollingClient = loginClient.newBuilder().readTimeout(35, TimeUnit.SECONDS).build()

    private suspend fun checkWechatQrCode(qr: QrCode): Int {
        val url = "https://lp.open.weixin.qq.com/connect/l/qrconnect".toHttpUrl().newBuilder()
            .addQueryParameter("uuid", qr.signature)
            .addQueryParameter("_", System.currentTimeMillis().toString()).build()
        val responseText = try {
            wechatPollingClient.newCall(Request.Builder().url(url).header("Referer", "https://open.weixin.qq.com/").build()).execute().use { response ->
                check(response.isSuccessful) { "检查微信扫码状态失败 (${response.code})" }
                response.body?.string().orEmpty()
            }
        } catch (_: java.net.SocketTimeoutException) { return 66 }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val status = Regex("window\\.wx_errcode\\s*=\\s*(\\d+)").find(responseText)?.groupValues?.get(1)?.toIntOrNull()
            ?: error("微信扫码状态格式异常")
        return when (status) {
            408 -> 66
            404 -> 67
            402 -> 65
            403 -> 68
            405 -> {
                val code = Regex("window\\.wx_code\\s*=\\s*['\"]([^'\"]+)['\"]").find(responseText)?.groupValues?.get(1)
                    ?: error("微信授权缺少登录码")
                val response = request("music.login.LoginServer", "Login", JsonObject().apply {
                    addProperty("code", code); addProperty("strAppid", "wx48db31d50e334801")
                }, null, loginTypeOverride = 1)
                val data = response.getAsJsonObject("data") ?: response
                val id = data.text("musicid").ifBlank { data.text("str_musicid") }
                val key = data.text("musickey")
                val encrypted = data.text("encryptUin").ifBlank { data.text("encrypt_uin") }
                check(id.toLongOrNull()?.let { it > 0 } == true && key.isNotBlank() && encrypted.isNotBlank()) {
                    "微信登录未返回完整的QQ音乐账号凭据"
                }
                val candidate = Credential(id, key, encrypted, 1)
                val base = runCatching { request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader",
                    JsonObject().apply { addProperty("uin", encrypted); addProperty("IsQueryTabDetail", 1) }, candidate)
                    .getAsJsonObject("Info")?.getAsJsonObject("BaseInfo") }.getOrNull()
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                saveAccount(candidate, base?.text("Name")?.takeIf { it.isNotBlank() } ?: id, base?.text("Avatar").orEmpty())
                0
            }
            else -> error("微信扫码状态异常 ($status)")
        }
    }

    private fun hash33(value: String, initial: Long = 0): Long {
        var hash = initial
        value.forEach { hash += (hash shl 5) + it.code; hash = hash and 0x7fffffff }
        return hash and 0x7fffffff
    }

    suspend fun checkQrCode(qr: QrCode): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            if (qr.loginType == 1) return@runCatching checkWechatQrCode(qr)
            val url = "https://ssl.ptlogin2.qq.com/ptqrlogin".toHttpUrl().newBuilder().apply {
                mapOf("u1" to "https://graph.qq.com/oauth2.0/login_jump", "ptqrtoken" to hash33(qr.signature).toString(),
                    "ptredirect" to "0", "h" to "1", "t" to "1", "g" to "1", "from_ui" to "1", "ptlang" to "2052",
                    "action" to "0-0-${System.currentTimeMillis()}", "js_ver" to "20102616", "js_type" to "1", "pt_uistyle" to "40",
                    "aid" to "716027609", "daid" to "383", "pt_3rd_aid" to "100497308", "has_onekey" to "1")
                    .forEach { (k, v) -> addQueryParameter(k, v) }
            }.build()
            val text = loginClient.newCall(Request.Builder().url(url).header("Cookie", "qrsig=${qr.signature}")
                .header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().use { response ->
                check(response.isSuccessful) { "获取扫码状态失败" }; response.body?.string().orEmpty()
            }
            val args = Regex("'([^']*)'").findAll(text).map { it.groupValues[1] }.toList()
            val status = args.firstOrNull()?.toIntOrNull() ?: error("扫码状态格式异常")
            if (status == 0) {
                kotlinx.coroutines.currentCoroutineContext().ensureActive()
                val redirect = args.getOrNull(2)?.toHttpUrl() ?: error("扫码授权缺少跳转信息")
                val uin = redirect.queryParameter("uin") ?: error("扫码授权缺少账号")
                val sigx = redirect.queryParameter("ptsigx") ?: error("扫码授权缺少签名")
                authorizeQr(uin, sigx)
            }
            status
        }
    }

    private suspend fun authorizeQr(uin: String, sigx: String) {
        val url = "https://ssl.ptlogin2.graph.qq.com/check_sig".toHttpUrl().newBuilder().apply {
            mapOf("uin" to uin, "pttype" to "1", "service" to "ptqrlogin", "nodirect" to "0", "ptsigx" to sigx,
                "s_url" to "https://graph.qq.com/oauth2.0/login_jump", "ptlang" to "2052", "ptredirect" to "100",
                "aid" to "716027609", "daid" to "383", "j_later" to "0", "low_login_hour" to "0", "regmaster" to "0",
                "pt_login_type" to "3", "pt_aid" to "0", "pt_aaid" to "16", "pt_light" to "0", "pt_3rd_aid" to "100497308")
                .forEach { (k, v) -> addQueryParameter(k, v) }
        }.build()
        val cookies = loginClient.newCall(Request.Builder().url(url).header("Referer", "https://xui.ptlogin2.qq.com/").build()).execute().use { response ->
            response.headers.values("Set-Cookie").map { it.substringBefore(';') }.filter { it.contains('=') }
        }
        val pSkey = cookies.firstOrNull { it.startsWith("p_skey=") }?.substringAfter('=') ?: error("QQ授权失败")
        val form = FormBody.Builder().apply {
            mapOf("response_type" to "code", "client_id" to "100497308", "redirect_uri" to "https://y.qq.com/portal/wx_redirect.html?login_type=1&surl=https://y.qq.com/",
                "scope" to "get_user_info,get_app_friends", "state" to "state", "switch" to "", "from_ptlogin" to "1", "src" to "1",
                "update_auth" to "1", "openapi" to "1010_1030", "g_tk" to hash33(pSkey, 5381).toString(),
                "auth_time" to System.currentTimeMillis().toString(), "ui" to java.util.UUID.randomUUID().toString())
                .forEach { (k, v) -> add(k, v) }
        }.build()
        val location = loginClient.newCall(Request.Builder().url("https://graph.qq.com/oauth2.0/authorize")
            .header("Cookie", cookies.joinToString("; ")).post(form).build()).execute().use { it.header("Location").orEmpty() }
        val code = location.toHttpUrl().queryParameter("code") ?: error("QQ授权没有返回code")
        val result = request("QQConnectLogin.LoginServer", "QQLogin", JsonObject().apply { addProperty("code", code) }, null)
        val data = result.getAsJsonObject("data") ?: result
        val id = data.text("musicid").ifBlank { data.text("str_musicid") }
        val key = data.text("musickey")
        val encrypted = data.text("encryptUin").ifBlank { data.text("encrypt_uin") }
        check(id.isNotBlank() && key.isNotBlank()) { "QQ音乐登录未返回完整凭据" }
        val candidate = Credential(id, key, encrypted, 2)
        val base = if (encrypted.isNotBlank()) runCatching { request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader",
            JsonObject().apply { addProperty("uin", encrypted); addProperty("IsQueryTabDetail", 1) }, candidate)
            .getAsJsonObject("Info")?.getAsJsonObject("BaseInfo") }.getOrNull() else null
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        saveAccount(candidate, base?.text("Name")?.takeIf { it.isNotBlank() } ?: id,
            base?.text("Avatar").orEmpty())
    }

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    private val searchMutex = kotlinx.coroutines.sync.Mutex()
    private val searchCache = LinkedHashMap<Pair<String, Int>, Pair<Long, JsonObject>>()
    private var lastSearchAt = 0L
    private var searchCooldownUntil = 0L

    private class SearchUnavailable(val code: Int) : Exception("QQ音乐搜索暂不可用（$code），请稍后重试")

    /** 两种搜索均为游客请求。仅在上游返回 2001 时尝试一次移动端；HTTP 429 则遵守冷却时间。 */
    private suspend fun searchBody(keyword: String, type: Int): Result<JsonObject> = withContext(Dispatchers.IO) {
        searchMutex.lock()
        try {
            val query = keyword.trim()
            require(query.isNotEmpty()) { "请输入搜索内容" }
            val key = query to type
            val now = android.os.SystemClock.elapsedRealtime()
            searchCache[key]?.let { (time, result) ->
                if (now - time < 60_000) return@withContext Result.success(result)
            }
            if (now < searchCooldownUntil) return@withContext Result.failure(IllegalStateException("QQ音乐搜索请求过于频繁，请稍后再试"))
            suspend fun requestSearch(method: String): JsonObject {
                val wait = 1000L - (android.os.SystemClock.elapsedRealtime() - lastSearchAt)
                if (wait > 0) kotlinx.coroutines.delay(wait)
                lastSearchAt = android.os.SystemClock.elapsedRealtime()
                val body = JsonObject().apply {
                    add("comm", JsonObject().apply { addProperty("ct", 19); addProperty("cv", 0); addProperty("format", "json") })
                    add("request", JsonObject().apply {
                        addProperty("module", "music.search.SearchCgiService")
                        addProperty("method", method)
                        add("param", JsonObject().apply {
                            addProperty("query", query); addProperty("page_num", 1)
                            addProperty("num_per_page", 30); addProperty("search_type", type)
                        })
                    })
                }
                val request = Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
                    .header("User-Agent", "Mozilla/5.0").header("Referer", "https://y.qq.com/")
                    .post(body.toString().toRequestBody(jsonType)).build()
                val root = client.newCall(request).execute().use { response ->
                    if (response.code == 429) {
                        searchCooldownUntil = android.os.SystemClock.elapsedRealtime() + 15_000
                        error("QQ音乐搜索请求过于频繁，请稍后再试")
                    }
                    check(response.isSuccessful) { "QQ音乐请求失败 (${response.code})" }
                    JsonParser.parseString(response.body?.string() ?: error("QQ音乐返回空响应")).asJsonObject
                }
                val envelope = root.getAsJsonObject("request") ?: error("QQ音乐搜索响应缺失")
                val rootCode = root.text("code").toIntOrNull() ?: error("QQ音乐搜索响应格式异常")
                val code = envelope.text("code").toIntOrNull() ?: error("QQ音乐搜索响应格式异常")
                if (rootCode != 0) throw SearchUnavailable(rootCode)
                if (code != 0) throw SearchUnavailable(code)
                return envelope.getAsJsonObject("data")?.getAsJsonObject("body") ?: error("QQ音乐搜索结果格式异常")
            }
            val result = try {
                requestSearch("DoSearchForQQMusicDesktop")
            } catch (e: SearchUnavailable) {
                if (e.code != 2001) throw e
                SearchEntityParser.normalizeQqMobile(requestSearch("DoSearchForQQMusicMobile"), type)
            }
            searchCache[key] = android.os.SystemClock.elapsedRealtime() to result
            while (searchCache.size > 120) searchCache.remove(searchCache.keys.first())
            Result.success(result)
        } catch (e: Exception) {
            kotlinx.coroutines.currentCoroutineContext().ensureActive()
            Result.failure(e)
        } finally {
            searchMutex.unlock()
        }
    }

    suspend fun searchSongs(keyword: String): Result<List<Track>> = searchBody(keyword, 0).mapCatching { result ->
        val songs = result.getAsJsonObject("song")?.getAsJsonArray("list") ?: error("QQ音乐搜索结果格式异常")
        songs.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.toTrack() }
    }

    suspend fun searchEntities(keyword: String, category: com.hypermusic.miuix.data.model.SearchCategory): Result<List<com.hypermusic.miuix.data.model.SearchEntity>> {
        require(category != com.hypermusic.miuix.data.model.SearchCategory.SONG)
        return searchBody(keyword, category.qqType).mapCatching { SearchEntityParser.parseQq(it, category) }
    }

    private fun JsonObject.text(key: String): String = get(key)?.takeUnless { it.isJsonNull }?.asString.orEmpty()

    internal fun JsonObject.toTrack(): Track? {
        val mid = text("mid")
        if (mid.isBlank()) return null
        val album = getAsJsonObject("album")
        val singers = getAsJsonArray("singer")?.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject }.orEmpty()
        val albumMid = album?.text("mid").orEmpty()
        return Track(id = mid, title = text("title").ifBlank { text("name") },
            artist = singers.joinToString(" / ") { it.text("name") },
            artistId = singers.firstOrNull()?.text("mid")?.let { "qqartist_$it" }.orEmpty(), album = album?.text("name").orEmpty(),
            albumId = album?.text("id").orEmpty(), source = "qqmusic", sourceUrl = "https://y.qq.com/n/ryqq/songDetail/$mid",
            imgUrl = if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R1500x1500M000$albumMid.jpg",
            duration = (text("interval").toLongOrNull() ?: 0L) * 1000,
            isVip = getAsJsonObject("pay")?.let { it.text("pay_month") == "1" && it.text("pay_play") == "1" } == true,
            artistNames = singers.map { it.text("name") }, artistIds = singers.map { "qqartist_${it.text("mid")}" },
            qqMediaMid = getAsJsonObject("file")?.text("media_mid").orEmpty(),
            qqSongId = text("id").toLongOrNull() ?: 0L)
    }

    private fun request(module: String, method: String, params: JsonObject, auth: Credential? = credential, loginTypeOverride: Int? = null, web: Boolean = false): JsonObject {
        val body = JsonObject().apply {
            add("comm", JsonObject().apply {
                addProperty("ct", if (web) 24 else 11); addProperty("cv", if (web) 0 else 14090008); addProperty("tmeAppID", "qqmusic")
                addProperty("uin", auth?.musicId ?: "0")
                if (auth != null) { addProperty("qq", auth.musicId); addProperty("authst", auth.musicKey); addProperty("tmeLoginType", auth.loginType) }
                else if (loginTypeOverride != null) addProperty("tmeLoginType", loginTypeOverride)
                else if (module == "QQConnectLogin.LoginServer") addProperty("tmeLoginType", 2)
            })
            add("request", JsonObject().apply { addProperty("module", module); addProperty("method", method); add("param", params) })
        }
        val req = Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", "QQMusic 14090008(android 15)").header("Referer", "https://y.qq.com/")
            .post(body.toString().toRequestBody(jsonType)).build()
        val root = client.newCall(req).execute().use { response ->
            check(response.isSuccessful) { "QQ音乐请求失败 (${response.code})" }
            JsonParser.parseString(response.body?.string() ?: error("QQ音乐返回空响应")).asJsonObject
        }
        val result = root.getAsJsonObject("request") ?: error("QQ音乐响应缺失")
        val code = result.get("code")?.asInt
        check(code == 0) { if (code == 2001) "QQ音乐请求过于频繁，请稍后再试" else "QQ音乐请求失败 ($code)" }
        return result.getAsJsonObject("data") ?: error("QQ音乐响应数据缺失")
    }

    data class TopChart(val id: Int, val title: String, val cover: String = "", val updateDate: String = "")

    val featuredTopCharts = listOf(
        TopChart(62, "飙升榜"), TopChart(26, "热歌榜"), TopChart(27, "新歌榜"),
        TopChart(5, "内地榜"), TopChart(59, "港区榜"), TopChart(61, "台区榜"),
        TopChart(3, "欧美榜"), TopChart(16, "韩国榜"), TopChart(17, "日本榜")
    )

    suspend fun getTopCharts(): Result<List<TopChart>> = withContext(Dispatchers.IO) {
        runCatching {
            val data = request("musicToplist.ToplistInfoServer", "GetAll", JsonObject(), null, web = true)
            val entries = data.getAsJsonArray("group")?.flatMap { group ->
                group.asJsonObject.getAsJsonArray("toplist")?.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject } ?: emptyList()
            }.orEmpty().associateBy { it.text("topId").toIntOrNull() }
            featuredTopCharts.map { chart ->
                val entry = entries[chart.id]
                chart.copy(cover = entry?.text("headPicUrl").orEmpty().replace("http://", "https://"),
                    updateDate = entry?.text("updateTime").orEmpty())
            }
        }
    }

    suspend fun getTopChartDetail(chartId: Int): Result<PlaylistDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val chart = featuredTopCharts.firstOrNull { it.id == chartId } ?: error("不支持的榜单")
            val tracks = mutableListOf<Track>()
            var info: JsonObject? = null
            var offset = 0
            var period = ""
            while (true) {
                val response = request("musicToplist.ToplistInfoServer", "GetDetail", JsonObject().apply {
                    addProperty("topId", chartId); addProperty("offset", offset); addProperty("num", 100); addProperty("period", period)
                }, null)
                val metadata = response.getAsJsonObject("data") ?: error("QQ音乐榜单数据缺失")
                info = metadata
                period = metadata.text("period")
                val songs = response.getAsJsonArray("songInfoList") ?: error("QQ音乐榜单歌曲缺失")
                tracks.addAll(songs.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.toTrack() })
                offset += songs.size()
                val total = metadata.text("totalNum").toIntOrNull() ?: offset
                if (songs.size() == 0 || offset >= total) break
            }
            val metadata = requireNotNull(info)
            PlaylistDetail(Playlist(id = "qqtop:$chartId", title = chart.title,
                coverImgUrl = metadata.text("headPicUrl").replace("http://", "https://"),
                sourceUrl = "https://y.qq.com/n/ryqq/toplist/$chartId", trackCount = tracks.size,
                description = metadata.text("updateTime")), tracks)
        }
    }

    enum class HomeFeature(val title: String) {
        RADAR("雷达推荐"), GUESS("猜你喜欢"), DAILY("每日30首"), NEW_SONGS("推荐新歌")
    }

    suspend fun getHomeFeature(feature: HomeFeature, excludedSongIds: List<Long> = emptyList()): Result<PlaylistDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = credential
            val tracks = when (feature) {
                HomeFeature.RADAR -> {
                    val all = mutableListOf<Track>()
                    for (page in 1..4) {
                        val data = request("music.recommend.TrackRelationServer", "GetRadarSong", JsonObject().apply {
                            addProperty("Page", page); addProperty("ReqType", 0)
                            add("FavSongs", com.google.gson.JsonArray()); add("EntranceSongs", com.google.gson.JsonArray())
                        }, auth)
                        val songs = data.getAsJsonArray("VecSongs") ?: error("雷达推荐格式异常")
                        all.addAll(songs.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.getAsJsonObject("Track")?.toTrack() })
                        if (data.text("HasMore") !in listOf("1", "true") || all.size >= 30 || songs.size() == 0) break
                    }
                    all.distinctBy { it.id }
                }
                HomeFeature.GUESS -> {
                    val data = request("music.radioProxy.MbTrackRadioSvr", "get_radio_track", JsonObject().apply {
                        addProperty("id", 99); addProperty("num", 20); addProperty("from", 0); addProperty("scene", 0)
                        add("song_ids", com.google.gson.JsonArray().apply { excludedSongIds.filter { it > 0 }.takeLast(100).forEach { add(it) } })
                    }, auth)
                    val songs = data.getAsJsonArray("tracks") ?: error("猜你喜欢暂不可用")
                    songs.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.toTrack() }
                }
                HomeFeature.NEW_SONGS -> {
                    val data = request("newsong.NewSongServer", "get_new_song_info", JsonObject().apply { addProperty("type", 5) }, auth)
                    val songs = data.getAsJsonArray("songlist") ?: error("推荐新歌格式异常")
                    songs.mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject?.toTrack() }
                }
                HomeFeature.DAILY -> {
                    check(auth != null) { "登录QQ音乐后查看每日30首" }
                    val feed = request("music.recommend.RecommendFeed", "get_recommend_feed", JsonObject().apply {
                        addProperty("direction", 0); addProperty("page", 1); addProperty("s_num", 0)
                        add("v_cache", com.google.gson.JsonArray())
                    }, auth)
                    val cards = feed.getAsJsonArray("v_shelf")?.toList().orEmpty().flatMap { shelf ->
                        shelf.asJsonObject.getAsJsonArray("v_niche")?.toList().orEmpty().flatMap { niche ->
                            niche.asJsonObject.getAsJsonArray("v_card")?.toList().orEmpty().mapNotNull { it.takeIf { e -> e.isJsonObject }?.asJsonObject }
                        }
                    }.filter { it.text("subtype") == "510" }
                    val card = cards.singleOrNull() ?: error("暂未获取到每日30首，请稍后刷新")
                    val id = card.text("id").takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true }
                        ?: card.getAsJsonObject("miscellany")?.text("dirid")?.takeIf { it.toIntOrNull()?.let { n -> n > 0 } == true }?.let { "qqdir:$it" }
                        ?: error("每日30首缺少歌单标识")
                    getPlaylistDetail(id).getOrThrow().tracks
                }
            }
            check(tracks.isNotEmpty()) { "${feature.title}暂无歌曲，请稍后刷新" }
            PlaylistDetail(Playlist(id = "qqfeature:${feature.name}", title = feature.title,
                coverImgUrl = tracks.first().imgUrl, sourceUrl = "", trackCount = tracks.size), tracks)
        }
    }

    suspend fun getRecommendPlaylists(): Result<List<RecommendPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val data = request("music.playlist.PlaylistSquare", "GetRecommendFeed", JsonObject().apply {
                addProperty("From", 0); addProperty("Size", 30)
            })
            val list = data.getAsJsonArray("List") ?: error("QQ音乐推荐格式异常")
            list.mapNotNull { entry ->
                val basic = entry.asJsonObject.getAsJsonObject("Playlist")?.getAsJsonObject("basic") ?: return@mapNotNull null
                val id = basic.text("tid")
                if (id.isBlank()) return@mapNotNull null
                RecommendPlaylist(id = id, title = basic.text("title"),
                    coverImgUrl = basic.getAsJsonObject("cover")?.let { cover ->
                        cover.text("big_url").ifBlank { cover.text("medium_url") }.ifBlank { cover.text("default_url") }
                    }.orEmpty().replace("http://", "https://"),
                    playCount = basic.text("play_cnt").toLongOrNull() ?: 0,
                    trackCount = basic.text("song_cnt").toIntOrNull() ?: 0)
            }
        }
    }

    suspend fun getPlaylistDetail(id: String): Result<PlaylistDetail> = withContext(Dispatchers.IO) {
        runCatching {
            val isDirectory = id.startsWith("qqdir:")
            val playlistId = if (isDirectory) 0L else id.toLongOrNull() ?: error("无效的QQ音乐歌单")
            val dirId = if (isDirectory) id.removePrefix("qqdir:").toIntOrNull() ?: error("无效的QQ音乐目录") else 0
            val owner = if (isDirectory) credential ?: error("请先登录QQ音乐") else null
            val tracks = mutableListOf<Track>()
            var info: JsonObject? = null
            var offset = 0
            var hasMore: Boolean
            do {
                val data = request("music.srfDissInfo.DissInfo", "CgiGetDiss", JsonObject().apply {
                    addProperty("disstid", playlistId); addProperty("dirid", dirId); addProperty("tag", 1)
                    if (owner != null) addProperty("enc_host_uin", owner.encryptUin)
                    addProperty("song_begin", offset); addProperty("song_num", 100)
                    addProperty("userinfo", 1); addProperty("orderlist", 1); addProperty("onlysonglist", 0)
                }, owner ?: credential)
                check(data.text("code").let { it.isBlank() || it == "0" }) { data.text("msg").ifBlank { "无法获取QQ音乐歌单" } }
                info = data.getAsJsonObject("dirinfo") ?: info
                val songs = data.getAsJsonArray("songlist") ?: error("QQ音乐歌单格式异常")
                tracks.addAll(songs.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject?.toTrack() })
                offset += songs.size()
                hasMore = data.text("hasmore") == "1" && songs.size() > 0
            } while (hasMore)
            val metadata = info ?: error("QQ音乐歌单资料缺失")
            PlaylistDetail(Playlist(id = id, title = metadata.text("title"),
                coverImgUrl = metadata.text("picurl").replace("http://", "https://"),
                sourceUrl = "https://y.qq.com/n/ryqq/playlist/$id",
                trackCount = metadata.text("songnum").toIntOrNull() ?: tracks.size,
                description = metadata.text("desc")), tracks)
        }
    }

    suspend fun getUserPlaylists(): Result<List<RecommendPlaylist>> = withContext(Dispatchers.IO) {
        runCatching {
            val auth = credential ?: error("请先登录QQ音乐")
            val created = request("music.musicasset.PlaylistBaseRead", "GetPlaylistByUin", JsonObject().apply { addProperty("uin", auth.musicId) }, auth)
            val all = created.getAsJsonArray("v_playlist")?.toMutableList() ?: mutableListOf()
            var offset = 0
            do {
                val fav = request("music.musicasset.PlaylistFavRead", "CgiGetPlaylistFavInfo", JsonObject().apply {
                    addProperty("uin", auth.encryptUin); addProperty("offset", offset); addProperty("size", 100)
                }, auth)
                val page = fav.getAsJsonArray("v_list") ?: error("QQ音乐收藏歌单格式异常")
                all.addAll(page)
                offset += page.size()
                val more = fav.text("hasmore") == "1" && page.size() > 0
            } while (more)
            all.mapNotNull { value ->
                val obj = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                if (obj.text("invalid") == "true" || obj.text("invalid") == "1") return@mapNotNull null
                val tid = obj.text("tid").ifBlank { obj.text("id") }.ifBlank { obj.text("dissid") }
                val dir = obj.text("dirid").ifBlank { obj.text("dirId") }
                val id = if (tid.isNotBlank() && tid != "0") tid else if (dir.isNotBlank() && dir != "0") "qqdir:$dir" else return@mapNotNull null
                RecommendPlaylist(id = id, title = obj.text("title").ifBlank { obj.text("dirName") }.ifBlank { obj.text("name") },
                    coverImgUrl = obj.text("picurl").ifBlank { obj.text("picUrl") }.replace("http://", "https://"),
                    trackCount = obj.text("songNum").ifBlank { obj.text("songnum") }.toIntOrNull() ?: 0)
            }.let { list ->
                if (list.any { it.id == "qqdir:201" || it.title.contains("我喜欢") }) list
                else listOf(RecommendPlaylist(id = "qqdir:201", title = "我喜欢", coverImgUrl = "")) + list
            }.distinctBy { it.id }
        }
    }

    suspend fun getComments(songId: Long): Result<List<com.hypermusic.miuix.data.model.Comment>> = withContext(Dispatchers.IO) {
        runCatching {
            require(songId > 0) { "QQ音乐歌曲标识缺失" }
            val data = request("music.globalComment.CommentRead", "GetHotCommentList", JsonObject().apply {
                addProperty("BizType", 1); addProperty("BizId", songId.toString()); addProperty("PageNum", 0)
                addProperty("PageSize", 20); addProperty("LastCommentSeqNo", ""); addProperty("HotType", 1)
                addProperty("WithAirborne", 0); addProperty("PicEnable", 1)
            })
            val list = data.getAsJsonObject("CommentList")?.getAsJsonArray("Comments") ?: error("QQ音乐评论格式异常")
            list.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject }.map { item ->
                com.hypermusic.miuix.data.model.Comment(nickname = item.text("Nick"), avatarUrl = item.text("Avatar"),
                    content = item.text("Content"), time = (item.text("PubTime").toLongOrNull() ?: 0L) * 1000,
                    likedCount = item.text("PraiseNum").toIntOrNull() ?: 0)
            }
        }
    }

    suspend fun getArtistInfo(mid: String): Result<ArtistInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid.isNotBlank()) { "歌手标识为空" }
            val data = request("music.UnifiedHomepage.UnifiedHomepageSrv", "GetHomepageHeader", JsonObject().apply {
                addProperty("SingerMid", mid)
                addProperty("IsQueryTabDetail", 1)
            })
            val info = data.getAsJsonObject("Info") ?: error("QQ音乐歌手资料缺失")
            val base = info.getAsJsonObject("BaseInfo") ?: error("QQ音乐歌手资料缺失")
            val singer = info.getAsJsonObject("Singer")
            val tabs = data.getAsJsonObject("TabDetail")?.getAsJsonArray("TabList")
            fun count(id: String): Int = tabs?.firstOrNull { it.isJsonObject && it.asJsonObject.text("TabID") == id }
                ?.asJsonObject?.text("Count")?.toIntOrNull() ?: 0
            val description = runCatching {
                val detail = request("music.web_singer_info_svr", "get_singer_detail_info", JsonObject().apply {
                    addProperty("singermid", mid); addProperty("sort", 5)
                    addProperty("sin", 0); addProperty("num", 1)
                })
                detail.text("singer_brief")
            }.getOrDefault("")
            ArtistInfo(
                id = singer?.text("SingerID")?.toLongOrNull() ?: 0L,
                name = base.text("Name").ifBlank { singer?.text("Name").orEmpty() },
                picUrl = base.text("Avatar").ifBlank { singer?.text("SingerPic").orEmpty() }
                    .ifBlank { "https://y.gtimg.cn/music/photo_new/T001R300x300M000$mid.jpg" }
                    .replace("http://", "https://"),
                albumSize = count("album"), musicSize = count("song_sing"), mvSize = count("video"),
                briefDesc = description
            )
        }
    }

    suspend fun getArtistSongs(mid: String, offset: Int = 0, limit: Int = 30): Result<List<Track>> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid.isNotBlank()) { "歌手标识为空" }
            val data = request("musichall.song_list_server", "GetSingerSongList", JsonObject().apply {
                addProperty("singerMid", mid); addProperty("order", 1)
                addProperty("number", limit.coerceIn(1, 30)); addProperty("begin", offset)
            })
            val songs = data.getAsJsonArray("songList") ?: error("QQ音乐歌手歌曲格式异常")
            songs.mapNotNull { it.takeIf { value -> value.isJsonObject }?.asJsonObject?.getAsJsonObject("songInfo")?.toTrack() }
        }
    }

    suspend fun getArtistAlbums(mid: String, offset: Int = 0, limit: Int = 50): Result<List<AlbumInfo>> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid.isNotBlank()) { "歌手标识为空" }
            val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_singer_album.fcg".toHttpUrl().newBuilder()
                .addQueryParameter("singermid", mid).addQueryParameter("begin", offset.toString())
                .addQueryParameter("num", limit.coerceIn(1, 50).toString()).addQueryParameter("format", "json")
                .addQueryParameter("inCharset", "utf-8").addQueryParameter("outCharset", "utf-8").build()
            val req = Request.Builder().url(url).header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0").build()
            val root = client.newCall(req).execute().use { response ->
                check(response.isSuccessful) { "QQ音乐歌手专辑请求失败 (${response.code})" }
                JsonParser.parseString(response.body?.string() ?: error("QQ音乐返回空响应")).asJsonObject
            }
            check(root.text("code") == "0") { "QQ音乐歌手专辑获取失败" }
            val list = root.getAsJsonObject("data")?.getAsJsonArray("list") ?: error("QQ音乐歌手专辑格式异常")
            list.mapNotNull { value ->
                val album = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val id = album.text("albumID").toLongOrNull() ?: return@mapNotNull null
                val albumMid = album.text("albumMID")
                AlbumInfo(id = id, name = album.text("albumName"),
                    picUrl = if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R1500x1500M000$albumMid.jpg",
                    size = album.getAsJsonObject("latest_song")?.text("song_count")?.toIntOrNull() ?: 0,
                    artist = album.text("singerName"), artistId = album.text("singerID").toLongOrNull() ?: 0L)
            }
        }
    }

    suspend fun getAlbumSongs(id: Long): Result<List<Track>> = getAlbumDetail(id).map { it.second }

    suspend fun getAlbumDetail(id: Long): Result<Pair<AlbumInfo, List<Track>>> = withContext(Dispatchers.IO) {
        runCatching {
            require(id > 0) { "专辑标识为空" }
            val url = "https://c.y.qq.com/v8/fcg-bin/fcg_v8_album_info_cp.fcg".toHttpUrl().newBuilder()
                .addQueryParameter("albumid", id.toString()).addQueryParameter("format", "json")
                .addQueryParameter("inCharset", "utf-8").addQueryParameter("outCharset", "utf-8").build()
            val req = Request.Builder().url(url).header("Referer", "https://y.qq.com/")
                .header("User-Agent", "Mozilla/5.0").build()
            val root = client.newCall(req).execute().use { response ->
                check(response.isSuccessful) { "QQ音乐专辑请求失败 (${response.code})" }
                JsonParser.parseString(response.body?.string() ?: error("QQ音乐返回空响应")).asJsonObject
            }
            check(root.text("code") == "0") { "QQ音乐专辑获取失败" }
            val data = root.getAsJsonObject("data") ?: error("QQ音乐专辑数据缺失")
            val list = data.getAsJsonArray("list") ?: error("QQ音乐专辑格式异常")
            val tracks = list.mapNotNull { value ->
                val song = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val mid = song.text("songmid")
                if (mid.isBlank()) return@mapNotNull null
                val singers = song.getAsJsonArray("singer")?.mapNotNull { it.takeIf { v -> v.isJsonObject }?.asJsonObject }.orEmpty()
                val albumMid = song.text("albummid")
                Track(id = mid, title = song.text("songname"), artist = singers.joinToString(" / ") { it.text("name") },
                    artistId = singers.firstOrNull()?.text("mid")?.let { "qqartist_$it" }.orEmpty(),
                    album = song.text("albumname"), albumId = song.text("albumid"), source = "qqmusic",
                    sourceUrl = "https://y.qq.com/n/ryqq/songDetail/$mid",
                    imgUrl = if (albumMid.isBlank()) "" else "https://y.gtimg.cn/music/photo_new/T002R1500x1500M000$albumMid.jpg",
                    duration = (song.text("interval").toLongOrNull() ?: 0L) * 1000,
                    artistNames = singers.map { it.text("name") }, artistIds = singers.map { "qqartist_${it.text("mid")}" },
                    qqMediaMid = song.text("strMediaMid"), qqSongId = song.text("songid").toLongOrNull() ?: 0,
                    isVip = song.text("payplay") == "1" || song.getAsJsonObject("pay")?.text("pay_play") == "1")
            }
            val first = tracks.firstOrNull()
            val albumMid = data.text("mid").ifBlank { data.text("album_mid") }.ifBlank { data.text("albummid") }
            val publishTime = runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).apply { isLenient = false }
                    .parse(data.text("aDate"))?.time ?: 0L
            }.getOrDefault(0L)
            val info = AlbumInfo(id = id, name = data.text("name").ifBlank { first?.album.orEmpty() },
                picUrl = if (albumMid.isNotBlank()) "https://y.gtimg.cn/music/photo_new/T002R1500x1500M000$albumMid.jpg" else first?.imgUrl.orEmpty(),
                artist = data.text("singername").ifBlank { first?.artist.orEmpty() }, size = tracks.size, publishTime = publishTime)
            info to tracks
        }
    }

    suspend fun isLiked(track: Track): Result<Boolean> {
        if (!isLoggedIn()) return Result.success(false)
        return getPlaylistDetail("qqdir:201").map { detail -> detail.tracks.any { it.id == track.id } }
    }

    suspend fun setLiked(track: Track, liked: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            check(isLoggedIn()) { "请先登录QQ音乐" }
            require(track.qqSongId > 0) { "歌曲标识缺失" }
            val data = request("music.musicasset.PlaylistDetailWrite", if (liked) "AddSonglist" else "DelSonglist", JsonObject().apply {
                addProperty("dirId", 201); addProperty("tid", 0); addProperty("bFmtUtf8", true)
                add("v_songInfo", com.google.gson.JsonArray().apply { add(JsonObject().apply {
                    // PlaylistDetailWrite uses 0 for ordinary songs; the search/track
                    // `type` field uses a different enum and is commonly 1.
                    addProperty("songId", track.qqSongId); addProperty("songType", 0)
                }) })
            })
            check(data.text("retCode") == "0") { "QQ音乐收藏操作失败 (${data.text("retCode")})" }
            liked
        }
    }

    data class AudioUrl(val url: String, val bitrate: Int, val quality: String, val extension: String)
    suspend fun getSongUrl(mid: String, mediaMid: String = ""): Result<String> =
        getSongAudio(mid, mediaMid, 0).map { it.url }

    suspend fun getSongAudio(mid: String, mediaMid: String = "", qualityIndex: Int = 0): Result<AudioUrl> = withContext(Dispatchers.IO) {
        runCatching {
            require(mid.isNotBlank()) { "歌曲标识为空" }
            val playbackAuth = accounts.find { it.credential.musicId == playbackAccountId }?.credential
            val guid = java.util.UUID.randomUUID().toString().replace("-", "").take(16)
            val candidates = com.hypermusic.miuix.data.model.QqAudioFormats.candidates(qualityIndex)
            val baseName = mediaMid.ifBlank { mid + mid }
            val params = JsonObject().apply {
                addProperty("uin", playbackAuth?.musicId ?: "0")
                addProperty("guid", guid)
                addProperty("ctx", 0)
                add("songmid", com.google.gson.JsonArray().apply { candidates.forEach { _ -> add(mid) } })
                add("songtype", com.google.gson.JsonArray().apply { candidates.forEach { _ -> add(0) } })
                add("filename", com.google.gson.JsonArray().apply { candidates.forEach { format -> add("${format.prefix}$baseName.${format.extension}") } })
            }
            val data = request("music.vkey.GetVkey", "UrlGetVkey", params, playbackAuth)
            val items = data.getAsJsonArray("midurlinfo") ?: error("QQ音乐没有返回播放地址")
            val base = data.getAsJsonArray("sip")?.firstOrNull()?.asString
                ?.takeIf { it.startsWith("https://") } ?: "https://isure.stream.qqmusic.qq.com/"
            val returned = items.mapNotNull { value ->
                val item = value.takeIf { it.isJsonObject }?.asJsonObject ?: return@mapNotNull null
                val path = item.text("purl")
                if (path.isBlank()) null else {
                    val filename = item.text("filename").ifBlank { path.substringBefore('?').substringAfterLast('/') }
                    val format = com.hypermusic.miuix.data.model.QqAudioFormats.match(candidates, filename)
                        ?: com.hypermusic.miuix.data.model.QqAudioFormats.match(candidates, path)
                    format?.let { AudioUrl(if (path.startsWith("https://")) path else base + path, it.bitrate, it.quality, it.extension) }
                }
            }
            candidates.asSequence().mapNotNull { format -> returned.firstOrNull { it.quality == format.quality } }
                .firstOrNull { audio ->
                    runCatching {
                        val probe = Request.Builder().url(audio.url).header("Referer", "https://y.qq.com/")
                            .header("Range", "bytes=0-0").build()
                        audioProbeClient.newCall(probe).execute().use { response ->
                            val contentType = response.header("Content-Type").orEmpty()
                            response.isSuccessful && !contentType.startsWith("text/", ignoreCase = true)
                        }
                    }.getOrDefault(false)
                } ?: error("QQ音乐未提供可访问的播放地址")
        }
    }

}
