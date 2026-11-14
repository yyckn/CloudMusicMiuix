package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/** 登录会话与其他平台分开保存；取消或退出后迟到的扫码结果不能写入账号。 */
internal class BodianAuth(context: Context, private val client: BodianClient) {
    data class Account(val uid: String, val nickname: String, val avatar: String)
    data class Qr(val key: String, val url: String, val deadline: Long, internal val revision: Long)
    enum class State { WAITING, SCANNED, EXPIRED, CONFIRMED }
    private val prefs = context.applicationContext.getSharedPreferences("bodian_prefs", Context.MODE_PRIVATE)
    private val accounts = AdditionalAccounts(context.applicationContext, MusicPlatform.BODIAN)
    private val active = ConcurrentHashMap<String, Qr>()

    suspend fun createQr(): Qr = withContext(Dispatchers.IO) {
        accounts.accounts()
        val currentRevision = revision.get()
        val data = client.call("/api/ucenter/login/qrCode", signed = true, anonymous = true).getAsJsonObject("data")
            ?: error("波点音乐二维码响应缺少数据")
        currentCoroutineContext().ensureActive()
        val key = data.value("qrCode")
        check(key.isNotBlank() && key.length <= 512) { "波点音乐二维码无效" }
        check(currentRevision == revision.get()) { "登录已取消" }
        val url = "https://bodian-oia.kuwo.cn/bodian/download.html".toHttpUrl().newBuilder()
            .addQueryParameter("pageName", "login_pc").addQueryParameter("pt", "3").addQueryParameter("id", key).build().toString()
        Qr(key, url, android.os.SystemClock.elapsedRealtime() + 300_000, currentRevision).also { active[key] = it }
    }

    suspend fun check(qr: Qr): State = withContext(Dispatchers.IO) {
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        val data = client.call("/api/ucenter/login/qrCodeStatus", mapOf("qrCode" to qr.key), signed = true, anonymous = true)
            .getAsJsonObject("data") ?: error("波点音乐扫码状态缺少数据")
        currentCoroutineContext().ensureActive()
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        when (data.value("status").toIntOrNull()) {
            1 -> State.WAITING
            2 -> { active.remove(qr.key, qr); State.EXPIRED }
            3 -> {
                val login = try {
                    client.call("/api/ucenter/users/login", signed = true, anonymous = true, method = "POST",
                        body = JsonObject().apply { addProperty("authType", 10); addProperty("qrCode", qr.key) }.toString())
                        .getAsJsonObject("data") ?: error("波点音乐登录响应缺少数据")
                } catch (error: BodianApiException) {
                    if (error.code == 11027) return@withContext State.SCANNED
                    throw error
                }
                val account = parseAccount(login)
                val token = login.value("token")
                check(token.isNotBlank()) { "波点音乐登录响应缺少凭证" }
                val coroutineContext = currentCoroutineContext()
                synchronized(sessionLock) {
                    coroutineContext.ensureActive()
                    if (!isCurrent(qr)) return@withContext State.EXPIRED
                    check(prefs.edit().putString("uid", account.uid).putString("token", token)
                        .putString("nickname", account.nickname).putString("avatar", account.avatar).commit()) { "保存波点音乐账号失败" }
                    accounts.captureLogin()
                    active.clear()
                    revision.incrementAndGet()
                }
                State.CONFIRMED
            }
            else -> error("波点音乐扫码状态无效")
        }
    }

    fun currentAccount(playback: Boolean = false): Account? {
        if (playback) return accounts.playbackAccount()?.let { Account(it.id, it.nickname, it.avatar) }
        val uid = prefs.getString("uid", null).orEmpty()
        val token = prefs.getString("token", null).orEmpty()
        return if (uid.isNotBlank() && token.isNotBlank()) Account(uid, prefs.getString("nickname", "").orEmpty(), prefs.getString("avatar", "").orEmpty()) else null
    }
    internal fun sessionRevision(): Long = revision.get()

    fun cancel(qr: Qr) = synchronized(sessionLock) { active.remove(qr.key, qr); Unit }
    fun logout() = synchronized(sessionLock) {
        currentAccount()?.let { accounts.remove(it.uid) }
        revision.incrementAndGet()
        active.clear()
    }
    private fun isCurrent(qr: Qr) = active[qr.key] === qr && qr.revision == revision.get() && android.os.SystemClock.elapsedRealtime() < qr.deadline

    companion object {
        internal fun invalidateAccounts() { revision.incrementAndGet() }
        // Different screens and workers create separate auth instances, but all
        // of them operate on the same saved account and must share its generation.
        private val sessionLock = Any()
        private val revision = AtomicLong()
        private fun JsonObject.value(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
        internal fun parseAccount(data: JsonObject): Account {
            val user = data.getAsJsonObject("userInfo") ?: data.getAsJsonObject("user") ?: JsonObject()
            val ids = listOf(data.value("id"), data.value("uid"), user.value("id"), user.value("uid")).filter { it.isNotBlank() }
            check(ids.isNotEmpty() && ids.first().matches(Regex("[1-9][0-9]{0,19}")) && ids.all { it == ids.first() }) { "波点音乐账号信息不一致" }
            return Account(ids.first(), user.value("nickname"), user.value("headImg").ifBlank { user.value("avatarUrl") })
        }
    }
}
