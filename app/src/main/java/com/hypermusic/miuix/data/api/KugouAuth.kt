package com.hypermusic.miuix.data.api

import android.content.Context
import android.os.SystemClock
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.atomic.AtomicLong

internal class KugouAuth(context: Context, private val client: KugouClient) {
    data class Qr internal constructor(internal val key: String, val url: String, internal val generation: Long, val deadline: Long)
    data class Account(val userId: String, val token: String, val nickname: String = "", val avatar: String = "")
    enum class State { WAITING, SCANNED, EXPIRED, CONFIRMED }
    private val prefs = context.applicationContext.getSharedPreferences("kugou_prefs", Context.MODE_PRIVATE)
    private val accounts = AdditionalAccounts(context.applicationContext, MusicPlatform.KUGOU)
    @Volatile private var active: Qr? = null

    suspend fun createQr(): Qr = withContext(Dispatchers.IO) {
        accounts.accounts()
        val version = generation.incrementAndGet()
        active = null
        val response = client.qrRequest("/v2/qrcode", mapOf("appid" to "1001", "type" to "1", "plat" to "4",
            "qrcode_txt" to "https://h5.kugou.com/apps/loginQRCode/html/index.html?appid=1005&", "srcappid" to "2919"))
        val coroutineContext = currentCoroutineContext()
        val data = response.obj("data") ?: error("酷狗音乐二维码响应缺少数据")
        val key = data.text("qrcode")
        check(key.isNotBlank() && key.length <= 512) { "酷狗音乐二维码无效" }
        synchronized(lock) {
            coroutineContext.ensureActive()
            check(version == generation.get()) { "登录已取消" }
            val url = "https://h5.kugou.com/apps/loginQRCode/html/index.html".toHttpUrl().newBuilder().addQueryParameter("qrcode", key).build()
            Qr(key, url.toString(), version, SystemClock.elapsedRealtime() + 300_000L).also { active = it }
        }
    }

    suspend fun check(qr: Qr): State = withContext(Dispatchers.IO) {
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        val data = client.qrRequest("/v2/get_userinfo_qrcode", mapOf("plat" to "4", "appid" to "1005", "srcappid" to "2919", "qrcode" to qr.key))
            .obj("data") ?: error("酷狗音乐扫码状态缺少数据")
        val coroutineContext = currentCoroutineContext()
        coroutineContext.ensureActive()
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        when (data.text("status")) {
            "0" -> { cancel(qr); State.EXPIRED }
            "1" -> State.WAITING
            "2" -> State.SCANNED
            "4" -> synchronized(lock) {
                coroutineContext.ensureActive()
                if (!isCurrent(qr)) return@withContext State.EXPIRED
                val id = data.text("userid")
                val token = data.text("token")
                check(id.matches(Regex("[1-9][0-9]{0,19}")) && token.isNotBlank() && token.length <= 4096) { "酷狗音乐登录信息无效" }
                val nickname = data.text("nickname").ifBlank { data.text("nick_name") }
                val avatar = data.text("pic").ifBlank { data.text("avatar") }.ifBlank { data.text("headimgurl") }
                check(prefs.edit().putString("userid", id).putString("token", token)
                    .putString("nickname", nickname).putString("avatar", avatar).commit()) { "保存酷狗音乐账号失败" }
                accounts.captureLogin()
                active = null
                generation.incrementAndGet()
                State.CONFIRMED
            }
            else -> error("酷狗音乐扫码状态无效")
        }
    }

    fun account(playback: Boolean = false): Account? {
        if (playback) return accounts.playbackAccount()?.let { Account(it.id, it.values["token"].orEmpty(), it.nickname, it.avatar) }
        val id = prefs.getString("userid", null).orEmpty()
        val token = prefs.getString("token", null).orEmpty()
        return if (id.isNotBlank() && token.isNotBlank()) Account(id, token,
            prefs.getString("nickname", "").orEmpty(), prefs.getString("avatar", "").orEmpty()) else null
    }
    internal fun sessionGeneration(): Long = generation.get()
    fun cancel(qr: Qr) = synchronized(lock) { if (active === qr) { active = null; generation.incrementAndGet() }; Unit }
    fun logout() = synchronized(lock) {
        account()?.let { accounts.remove(it.userId) }
        active = null; generation.incrementAndGet()
    }
    private fun isCurrent(qr: Qr) = active === qr && qr.generation == generation.get() && SystemClock.elapsedRealtime() < qr.deadline
    private fun JsonObject.obj(key: String) = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    companion object {
        internal fun invalidateAccounts() { generation.incrementAndGet() }
        private val lock = Any(); private val generation = AtomicLong()
    }
}
