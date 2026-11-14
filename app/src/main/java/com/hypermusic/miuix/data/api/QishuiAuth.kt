package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.util.Base64
import java.util.concurrent.atomic.AtomicLong

internal class QishuiAuth(context: Context, private val client: QishuiClient) {
    data class Qr internal constructor(internal val token: String, val image: ByteArray, internal val generation: Long, val expiresAt: Long)
    enum class State { WAITING, SCANNED, CONFIRMED, EXPIRED, REFUSED }
    private val store = QishuiSessionStore(context.applicationContext)
    private val accounts = AdditionalAccounts(context.applicationContext, MusicPlatform.QISHUI)
    @Volatile private var currentQr: Qr? = null

    suspend fun createQr(): Qr = withContext(Dispatchers.IO) {
        accounts.accounts()
        val version = generation.incrementAndGet()
        currentQr = null
        val data = client.oauthQr()
        val token = data.text("token")
        val image = Base64.getDecoder().decode(data.text("qrcode").substringAfterLast(','))
        check(token.isNotBlank() && image.size >= 8 && image.take(8) == listOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(Int::toByte)) { "汽水音乐二维码无效" }
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        check(version == generation.get()) { "登录已取消" }
        Qr(token, image, version, android.os.SystemClock.elapsedRealtime() + 300_000).also { currentQr = it }
    }

    suspend fun check(qr: Qr): State = withContext(Dispatchers.IO) {
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        val status = client.oauthQr(qr.token)
        if (!isCurrent(qr)) return@withContext State.EXPIRED
        when (status.text("status")) {
            "new" -> State.WAITING
            "scanned" -> State.SCANNED
            "expired" -> { cancel(qr); State.EXPIRED }
            "refused" -> { cancel(qr); State.REFUSED }
            "confirmed" -> {
                val code = status.text("code").ifBlank { status.text("auth_code") }
                check(code.isNotBlank()) { "汽水音乐未返回授权码" }
                val access = client.exchangeAccessCode(code)
                val accessCode = access.text("access_code")
                val openId = access.text("open_id").ifBlank { access.text("open_uid") }
                check(accessCode.isNotBlank()) { "汽水音乐未返回访问凭证" }
                val guard = QishuiGuard.create()
                val ticket = client.exchangeTicket(accessCode, openId, guard.csr)
                val profile = client.authenticated("/me/info/", JsonObject(), guard.privateKey, ticket).getAsJsonObject("my_info")
                    ?: error("汽水音乐未返回账号资料")
                val uid = profile.text("id")
                check(uid.isNotBlank() && uid.length <= 512 && uid.none { it.isWhitespace() || it.isISOControl() }) { "汽水音乐账号标识无效" }
                val avatar = listOf("medium_avatar_url", "avatar_url", "thumb_avatar_url").firstNotNullOfOrNull { name ->
                    profile.get(name)?.let { if (it.isJsonObject) QishuiCatalog.image(it.asJsonObject) else if (it.isJsonPrimitive) it.asString else "" }?.takeIf { it.startsWith("https://") }
                }.orEmpty()
                val now = System.currentTimeMillis()
                val expiry = accessExpiry(access, now)
                check(expiry > now) { "汽水音乐登录凭证已过期" }
                val coroutineContext = kotlinx.coroutines.currentCoroutineContext()
                synchronized(sessionLock) {
                    coroutineContext.ensureActive()
                    if (!isCurrent(qr)) return@withContext State.EXPIRED
                    store.save(QishuiSessionStore.Session(client.deviceId, uid, profile.text("nickname"), avatar, guard.privateKey,
                        ticket.ticket, ticket.certificate, ticket.timestampSignature, accessCode, openId, expiry))
                    accounts.captureLogin()
                    currentQr = null
                    generation.incrementAndGet()
                }
                State.CONFIRMED
            }
            else -> error("汽水音乐扫码状态无效")
        }
    }

    fun cancel(qr: Qr) = synchronized(sessionLock) { if (currentQr === qr) { currentQr = null; generation.incrementAndGet() }; Unit }
    fun logout() = synchronized(sessionLock) { store.read()?.let { accounts.remove(it.userId) }; currentQr = null; generation.incrementAndGet() }
    fun account(playback: Boolean = false): QishuiSessionStore.Session? = if (playback) accounts.qishuiPlaybackSession() else store.read()
    /** Called on an IO thread. A failed refresh leaves the existing session intact. */
    internal fun validSession(playback: Boolean = false): QishuiSessionStore.Session? = synchronized(refreshLock) {
        val version = generation.get()
        val old = account(playback) ?: return@synchronized null
        if (old.expiresAt >= System.currentTimeMillis() + 3_600_000) return@synchronized old
        check(old.deviceId == client.deviceId) { "汽水音乐设备信息已变化，请重新登录" }
        val access = client.refreshAccessCode(old.accessCode)
        val code = access.text("access_code")
        check(code.isNotBlank()) { "汽水音乐凭证续期失败，请重试" }
        val openId = access.text("open_id").ifBlank { access.text("open_uid") }.ifBlank { old.openId }
        val expiry = accessExpiry(access, System.currentTimeMillis())
        check(expiry > System.currentTimeMillis()) { "汽水音乐凭证续期失败，请重新登录" }
        val ticket = client.exchangeTicket(code, openId, old.certificate, certificate = true)
        val profile = client.authenticated("/me/info/", JsonObject(), old.privateKey, ticket).getAsJsonObject("my_info")
            ?: error("汽水音乐续期后未返回账号资料")
        check(profile.text("id") == old.userId) { "汽水音乐账号信息不一致，请重新登录" }
        val updated = old.copy(ticket = ticket.ticket, certificate = ticket.certificate, timestampSignature = ticket.timestampSignature,
            accessCode = code, openId = openId, expiresAt = expiry)
        synchronized(sessionLock) {
            check(generation.get() == version && account(playback) == old) { "汽水音乐账号已切换，请重试" }
            accounts.updateQishui(updated)
        }
        updated
    }
    internal fun sessionGeneration(): Long = generation.get()
    private fun isCurrent(qr: Qr) = currentQr === qr && qr.generation == generation.get() && android.os.SystemClock.elapsedRealtime() < qr.expiresAt
    private fun JsonObject.text(key: String) = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    companion object {
        internal fun invalidateAccounts() { generation.incrementAndGet() }
        private val generation = AtomicLong()
        private val sessionLock = Any()
        private val refreshLock = Any()
        internal fun accessExpiry(access: JsonObject, now: Long): Long {
            val raw = access.get("expires_time")?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: 0L
            return when { raw > 1_000_000_000_000L -> raw; raw > 1_000_000_000L -> raw * 1000; raw > 0 -> now + raw * 1000; else -> now + 21_600_000 }
        }
    }
}
