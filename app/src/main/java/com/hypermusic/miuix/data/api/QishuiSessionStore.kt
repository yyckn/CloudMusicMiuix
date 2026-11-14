package com.hypermusic.miuix.data.api

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.google.gson.Gson
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 汽水会话使用 Android Keystore 加密；绝不把密钥或 token 写入日志。 */
internal class QishuiSessionStore(context: Context) {
    data class Session(val deviceId: String, val userId: String, val nickname: String, val avatar: String,
        val privateKey: String, val ticket: String, val certificate: String, val timestampSignature: String,
        val accessCode: String, val openId: String, val expiresAt: Long)
    private val prefs = context.applicationContext.getSharedPreferences("qishui_secure_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val alias = "${context.applicationContext.packageName}.qishui.session"

    private fun key(): SecretKey = synchronized(keyLock) {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return@synchronized it }
        KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
    }

    @Synchronized fun save(session: Session) {
        check(prefs.edit().putString("session", encode(session)).commit()) { "保存汽水音乐账号失败" }
    }
    internal fun encode(session: Session): String {
        require(session.userId.isNotBlank() && session.ticket.isNotBlank() && session.privateKey.isNotBlank())
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val ciphertext = cipher.doFinal(gson.toJson(session).toByteArray(Charsets.UTF_8))
        val encoded = Base64.getEncoder().encodeToString(cipher.iv + ciphertext)
        return encoded
    }

    @Synchronized fun read(): Session? {
        val encoded = prefs.getString("session", null) ?: return null
        return decode(encoded)
    }
    internal fun decode(encoded: String): Session {
        try {
            val bytes = Base64.getDecoder().decode(encoded)
            check(bytes.size > 28)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes.copyOfRange(0, 12))) }
            val data = String(cipher.doFinal(bytes.copyOfRange(12, bytes.size)), Charsets.UTF_8)
            val session = gson.fromJson(data, Session::class.java) ?: error("汽水音乐账号数据无效")
            check(session.userId.isNotBlank() && session.ticket.isNotBlank() && session.privateKey.isNotBlank())
            return session
        } catch (error: Exception) {
            // Preserve unreadable data for diagnosis rather than silently signing the user out.
            throw IllegalStateException("无法读取汽水音乐登录信息", error)
        }
    }

    @Synchronized fun clear() { check(prefs.edit().remove("session").commit()) { "清除汽水音乐账号失败" } }
    companion object { private val keyLock = Any() }
}
