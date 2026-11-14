package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.FormBody
import java.math.BigInteger
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

/** 汽水车机 SDK 的公共目录请求；登录态请求会使用单独的 ticket guard。 */
internal class QishuiClient(context: Context, private val client: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(com.hypermusic.miuix.data.lyrics.LyricRequests)
    .connectTimeout(15, TimeUnit.SECONDS).readTimeout(25, TimeUnit.SECONDS).followRedirects(false).build(), private val playback: Boolean = false) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("qishui_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val random = SecureRandom()
    internal val deviceId = synchronized(deviceLock) {
        prefs.getString("device_id", null)?.takeIf { it.matches(Regex("[0-9]{19}")) }
            ?: BigInteger(64, random).mod(BigInteger("9000000000000000000")).add(BigInteger("1000000000000000000")).toString().also {
                check(prefs.edit().putString("device_id", it).commit()) { "无法保存汽水音乐设备标识" }
            }
        }
    @Volatile private var clientToken: String? = null

    private fun url(host: String, path: String): String {
        require(path.startsWith('/') && !path.contains(".."))
        val query = linkedMapOf("aid" to "765213", "app_id" to "765213", "app_name" to "byte_autoservice_lite",
            "version_code" to "100010", "version_name" to "1.0.10", "channel" to "bydSong", "sdk_aid" to "753999",
            "caller_name" to "luna_open_sdk", "sdk_version_code" to "100000100", "sdk_version_name" to "0.0.1",
            "sdk_channel" to "Test", "vehicle_type" to "BYD", "device_brand" to "BYD", "device_type" to "BYD",
            "device_platform" to "android", "resolution" to "1920x1080", "os_version" to "13", "ac" to "wifi",
            "device_id" to deviceId, "did" to deviceId, "iid" to deviceId, "vehicle_id" to "qishui-$deviceId",
            "_timestamp" to (System.currentTimeMillis() / 1000).toString(), "_nonce" to random.nextInt(65536).toString())
        return host.toHttpUrl().newBuilder().encodedPath("/api/luna/v1/platform$path")
            .apply { query.forEach { (k, v) -> addQueryParameter(k, v) } }.build().toString()
    }

    @Synchronized private fun token(): String {
        clientToken?.let { return it }
        val url = url("https://open.qishui.com", "/client/token/get/").toHttpUrl().newBuilder()
            .addQueryParameter("trigger_type", "cold_start").build()
        val json = execute(Request.Builder().url(url).header("User-Agent", "LunaOpenSDK/1.0.10").build())
        return json.get("client_token")?.asString?.takeIf { it.isNotBlank() }?.also { clientToken = it }
            ?: error("汽水音乐未返回设备令牌")
    }

    fun read(path: String, body: JsonObject): JsonObject {
        require(path in setOf("/search/", "/playlist/detail/", "/media/mget/", "/media/mcheck/", "/artist/detail/", "/artist/tracks/", "/feed/mode/", "/feed/song-tab/"))
        val auth = QishuiAuth(appContext, this)
        val generation = auth.sessionGeneration()
        val account = auth.validSession(playback)
        check(account == null || account.deviceId == deviceId) { "汽水音乐设备信息已变化，请重新登录" }
        check(generation == auth.sessionGeneration()) { "汽水音乐账号已切换，请重试" }
        repeat(2) { attempt ->
            val usedToken = token()
            val payload = readPayload(body, account?.userId)
            // Feed preferences use explicit null to clear a previously selected mode.
            // Gson's default object serialization omits those fields.
            val jsonBody = if (path == "/feed/song-tab/") payload.toString() else gson.toJson(payload)
            val request = Request.Builder().url(url("https://open.douyin.com", path)).header("User-Agent", "LunaOpenSDK/1.0.10")
                .header("access-token", usedToken).post(jsonBody.toRequestBody("application/json".toMediaType())).build()
            val response = execute(request, checkApi = false)
            check(generation == auth.sessionGeneration()) { "汽水音乐账号已切换，请重试" }
            val code = responseCode(response) ?: 0L
            if (code == 28001008L && attempt == 0) {
                synchronized(this) { if (clientToken == usedToken) clientToken = null }
            } else {
                check(code == 0L) { "汽水音乐请求失败（$code）" }
                return response.getAsJsonObject("data") ?: error("汽水音乐响应缺少数据")
            }
        }
        error("汽水音乐设备令牌已失效")
    }

    internal fun oauthQr(token: String? = null): JsonObject {
        val path = if (token == null) "/oauth/get_qrcode/" else "/oauth/check_qrcode/"
        val url = "https://open.douyin.com".toHttpUrl().newBuilder().encodedPath(path).apply {
            linkedMapOf("aid" to "1128", "client_key" to "aw4bbxfujo741d09",
                "scope" to "user_info,luna.openapi.platform.interaction,luna.openapi.platform.me_tab,luna.commerce.openapi_platform.vip_info",
                "state" to "", "next" to "", "jump_type" to "native", "optional_scope_check" to "", "optional_scope_uncheck" to "",
                "customize_params" to "{\"source\":\"pc_auth\",\"not_skip_confirm\":\"true\"}", "source_from" to "native",
                "device_platform" to "android", "app_identity" to "82a0737a3f8d93d9958c8a7938bbc365",
                "signature" to "cf9024ea72d9c71813238b13ea107a3d").forEach { (key, value) -> addQueryParameter(key, value) }
            if (token != null) { addQueryParameter("token", token); addQueryParameter("timestamp", System.currentTimeMillis().toString()) }
        }.build()
        return execute(Request.Builder().url(url).header("User-Agent", "LunaOpenSDK/1.0.10").build())
            .getAsJsonObject("data") ?: error("汽水音乐扫码响应缺少数据")
    }

    internal fun exchangeAccessCode(authCode: String): JsonObject {
        val address = url("https://open.qishui.com", "/access/code/get/").toHttpUrl().newBuilder()
            .addQueryParameter("auth_code", authCode).build()
        val result = execute(Request.Builder().url(address).header("User-Agent", "LunaOpenSDK/1.0.10").build())
        return result.getAsJsonObject("data") ?: result
    }

    internal fun refreshAccessCode(accessCode: String): JsonObject {
        require(accessCode.isNotBlank()) { "汽水音乐登录凭证缺失，请重新登录" }
        val address = url("https://open.qishui.com", "/access/code/refresh/").toHttpUrl().newBuilder()
            .addQueryParameter("access_code", accessCode).build()
        val result = execute(Request.Builder().url(address).header("User-Agent", "LunaOpenSDK/1.0.10").build())
        return result.getAsJsonObject("data") ?: result
    }

    internal fun exchangeTicket(accessCode: String, openId: String, csr: String, certificate: Boolean = false): QishuiGuard.Ticket {
        val address = "https://open-client.douyin.com/passport/open/get_access_ticket/".toHttpUrl().newBuilder()
            .addQueryParameter("aid", "1128").addQueryParameter("client_key", "aw4bbxfujo741d09")
            .addQueryParameter("device_platform", "android").build()
        val request = Request.Builder().url(address).header("User-Agent", "LunaOpenSDK/1.0.10")
            .header("bd-ticket-guard-version", "1").header(if (certificate) "bd-ticket-guard-client-cert" else "bd-ticket-guard-client-csr", csr)
            .post(FormBody.Builder().add("access_code", accessCode).add("open_id", openId).build()).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "汽水音乐票据请求失败（HTTP ${response.code}）" }
            return QishuiGuard.parseTicket(response.header("bd-ticket-guard-server-data").orEmpty())
        }
    }

    internal fun authenticated(path: String, body: JsonObject, privateKey: String, ticket: QishuiGuard.Ticket, userId: String? = null): JsonObject {
        require(path in setOf("/me/info/", "/me/playlist/", "/me/collection/mixed/", "/me/collect/", "/playlist/collect/", "/media/collect/",
            "/media/cancel/collect/", "/playlist/append/", "/playlist/delete/"))
        val payload = body.deepCopy().apply {
            add("common_params", JsonObject().apply { if (!userId.isNullOrBlank()) addProperty("user_id", userId) })
        }
        val request = Request.Builder().url(url("https://open.douyin.com", path)).header("User-Agent", "LunaOpenSDK/1.0.10")
            .apply { QishuiGuard.headers(privateKey, ticket, path).forEach { (name, value) -> header(name, value) } }
            .post(gson.toJson(payload).toRequestBody("application/json".toMediaType())).build()
        val result = execute(request)
        if (path in mutationPaths) checkMutationResponse(result)
        val data = result.get("data")
        if (data != null && data.isJsonObject) return data.asJsonObject
        // Account mutations may succeed with no data body. Reads must still
        // reject a missing object so an incomplete response is not treated as an empty library.
        if (path in mutationPaths) return JsonObject()
        error("汽水音乐账号响应缺少数据")
    }

    internal fun readPlayerInfo(url: String): JsonObject = execute(
        Request.Builder().url(QishuiPlayback.trustedUrl(url)).header("User-Agent", "LunaOpenSDK/1.0.10").build(),
        checkApi = false
    )

    private fun execute(request: Request, checkApi: Boolean = true): JsonObject = client.newCall(request).execute().use { response ->
        check(response.isSuccessful) { "汽水音乐请求失败（HTTP ${response.code}）" }
        val body = response.body ?: error("汽水音乐响应为空")
        check(body.contentLength() <= 8 * 1024 * 1024) { "汽水音乐响应过大" }
        val source = body.source()
        check(!source.request(8L * 1024 * 1024 + 1)) { "汽水音乐响应过大" }
        val bytes = source.readByteArray()
        val json = JsonParser.parseString(bytes.toString(Charsets.UTF_8)).asJsonObject
        if (checkApi) {
            val code = responseCode(json) ?: 0L
            check(code == 0L) { "汽水音乐请求失败（$code）" }
        }
        json
    }

    companion object {
        private val deviceLock = Any()
        private val mutationPaths = setOf("/playlist/collect/", "/media/collect/", "/media/cancel/collect/", "/playlist/append/", "/playlist/delete/")
        internal fun responseCode(result: JsonObject): Long? {
            val outer = result.get("err_no")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
            val inner = result.get("data")?.takeIf { it.isJsonObject }?.asJsonObject?.get("error_code")
                ?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull()
            return listOfNotNull(outer, inner).firstOrNull { it != 0L } ?: outer ?: inner
        }
        internal fun checkMutationResponse(result: JsonObject) {
            check(responseCode(result) == 0L) { "汽水音乐未确认操作成功" }
        }
        internal fun readPayload(body: JsonObject, userId: String?): JsonObject = body.deepCopy().apply {
            val common = get("common_params")?.takeIf { it.isJsonObject }?.asJsonObject ?: JsonObject().also { add("common_params", it) }
            if (!userId.isNullOrBlank()) common.addProperty("user_id", userId) else common.remove("user_id")
        }
    }
}
