package com.hypermusic.miuix.data.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.compose.runtime.mutableIntStateOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Each platform keeps its own encrypted account list; the native preferences hold the selected data session. */
internal class AdditionalAccounts(private val context: Context, val platform: MusicPlatform) {
    @androidx.annotation.Keep
    data class Account(val id: String, val nickname: String, val avatar: String, val values: Map<String, String>)
    private val prefs = context.getSharedPreferences("additional_accounts_${platform.name}", Context.MODE_PRIVATE)
    private val nativeName = when (platform) {
        MusicPlatform.BODIAN -> "bodian_prefs"
        MusicPlatform.KUGOU -> "kugou_prefs"
        MusicPlatform.QISHUI -> "qishui_secure_session"
        else -> error("不支持的账号平台")
    }
    private val fields = when (platform) {
        MusicPlatform.BODIAN -> listOf("uid", "token", "nickname", "avatar")
        MusicPlatform.KUGOU -> listOf("userid", "token", "nickname", "avatar")
        else -> listOf("session")
    }
    var enabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = synchronized(lock) {
            check(value || accounts().size <= 1) { "当前账号数量大于1，需要删除部分账号才能关闭多账号功能。" }
            check(prefs.edit().putBoolean("enabled", value).commit())
            revision.intValue++
        }

    fun accounts(): List<Account> = synchronized(lock) {
        val saved = prefs.getString("accounts", null)
        if (saved != null) return@synchronized Gson().fromJson<List<Account>>(decrypt(saved), object : TypeToken<List<Account>>() {}.type).orEmpty()
        val current = currentNative() ?: return@synchronized emptyList()
        write(listOf(current), current.id)
        listOf(current)
    }

    val dataId: String? get() = prefs.getString("data_id", null) ?: accounts().firstOrNull()?.id
    val playbackId: String? get() = prefs.getString("playback_id", null) ?: dataId
    fun playbackAccount(): Account? = accounts().firstOrNull { it.id == playbackId }
    fun qishuiPlaybackSession(): QishuiSessionStore.Session? = playbackAccount()?.values?.get("session")?.let { QishuiSessionStore(context).decode(it) }
    fun updateQishui(session: QishuiSessionStore.Session) = synchronized(lock) {
        val list = accounts().map { if (it.id == session.userId) it.copy(nickname = session.nickname, avatar = session.avatar,
            values = mapOf("session" to QishuiSessionStore(context).encode(session))) else it }
        write(list, dataId)
        list.firstOrNull { it.id == dataId }?.let(::restore)
    }
    fun selectPlayback(id: String) = synchronized(lock) {
        check(accounts().any { it.id == id }) { "账号不存在" }
        check(prefs.edit().putString("playback_id", id).commit())
        invalidate()
    }

    fun captureLogin() = synchronized(lock) {
        val account = currentNative() ?: error("登录信息缺失")
        val existing = accounts()
        val list = if (enabled) existing.filterNot { it.id == account.id } + account else listOf(account)
        val selected = if (enabled) dataId?.takeIf { id -> list.any { it.id == id } } ?: account.id else account.id
        write(list, selected)
        if (!enabled || list.none { it.id == playbackId }) check(prefs.edit().putString("playback_id", selected).commit())
        restore(list.first { it.id == selected })
    }

    fun selectData(id: String) = synchronized(lock) {
        val account = accounts().firstOrNull { it.id == id } ?: error("账号不存在")
        restore(account)
        check(prefs.edit().putString("data_id", id).commit())
        invalidate()
    }

    fun remove(id: String) = synchronized(lock) {
        val list = accounts().filterNot { it.id == id }
        val selected = dataId?.takeIf { old -> list.any { it.id == old } } ?: list.firstOrNull()?.id
        write(list, selected)
        if (list.none { it.id == playbackId }) check(prefs.edit().putString("playback_id", selected).commit())
        if (selected != null) restore(list.first { it.id == selected }) else {
            val edit = context.getSharedPreferences(nativeName, Context.MODE_PRIVATE).edit()
            fields.forEach(edit::remove)
            check(edit.commit()) { "删除账号失败" }
        }
        invalidate()
    }

    private fun currentNative(): Account? {
        val native = context.getSharedPreferences(nativeName, Context.MODE_PRIVATE)
        val values = fields.mapNotNull { key -> native.getString(key, null)?.let { key to it } }.toMap()
        return when (platform) {
            MusicPlatform.BODIAN -> values["uid"]?.takeIf { !values["token"].isNullOrBlank() }?.let { Account(it, values["nickname"].orEmpty(), values["avatar"].orEmpty(), values) }
            MusicPlatform.KUGOU -> values["userid"]?.takeIf { !values["token"].isNullOrBlank() }?.let { Account(it, values["nickname"].orEmpty(), values["avatar"].orEmpty(), values) }
            else -> QishuiSessionStore(context).read()?.let { Account(it.userId, it.nickname, it.avatar, values) }
        }
    }

    private fun restore(account: Account) {
        val edit = context.getSharedPreferences(nativeName, Context.MODE_PRIVATE).edit()
        fields.forEach { key -> account.values[key]?.let { edit.putString(key, it) } ?: edit.remove(key) }
        check(edit.commit()) { "切换账号失败" }
    }
    private fun write(accounts: List<Account>, selected: String?) {
        check(prefs.edit().putString("accounts", encrypt(Gson().toJson(accounts))).putString("data_id", selected).commit()) { "保存多账号失败" }
        revision.intValue++
    }
    private fun invalidate() {
        when (platform) {
            MusicPlatform.BODIAN -> BodianAuth.invalidateAccounts()
            MusicPlatform.KUGOU -> KugouAuth.invalidateAccounts()
            MusicPlatform.QISHUI -> QishuiAuth.invalidateAccounts()
            else -> Unit
        }
        revision.intValue++
        AdditionalLibraryChanges.changed(platform)
    }
    private fun key(): SecretKey {
        val alias = "${context.packageName}.additional.accounts"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }
    private fun encrypt(text: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.getEncoder().encodeToString(cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8)))
    }
    private fun decrypt(text: String): String {
        val bytes = Base64.getDecoder().decode(text)
        require(bytes.size > 28) { "账号存储数据无效" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
        return String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
    }
    companion object {
        private val lock = Any()
        val revision = mutableIntStateOf(0)
    }
}
