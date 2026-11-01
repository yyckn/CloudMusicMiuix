package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import java.net.URI
import java.util.Base64

/** A credential-bearing URI; never log or send it to a remote service. */
object UserCredentialLink {
    data class Payload(val platform: String, val credential: String)
    private const val MAX_PAYLOAD_BYTES = 2048

    fun encode(platform: String, credential: String): String {
        validate(platform, credential)
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(credential.toByteArray(Charsets.UTF_8))
        return "hypermusic://user/$platform?data=$encoded"
    }

    fun decode(value: String): Payload {
        require(value.length <= 4096) { "二维码内容过长" }
        val uri = URI(value)
        require(uri.scheme == "hypermusic" && uri.host == "user" && uri.userInfo == null && uri.port == -1 && uri.fragment == null) { "不是有效的用户数据二维码" }
        val platform = uri.rawPath?.removePrefix("/") ?: ""
        require(uri.rawPath == "/$platform" && platform in setOf("wy", "qq")) { "不支持的音乐平台" }
        val query = uri.rawQuery ?: error("二维码缺少用户凭证")
        require(query.startsWith("data=") && query.count { it == '=' } == 1 && '&' !in query) { "二维码参数无效" }
        val encoded = query.removePrefix("data=")
        require(encoded.isNotEmpty() && encoded.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "二维码凭证编码无效" }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        require(bytes.size in 1..MAX_PAYLOAD_BYTES) { "二维码凭证长度无效" }
        val credential = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        validate(platform, credential)
        return Payload(platform, credential)
    }

    private fun validate(platform: String, credential: String) {
        require(platform in setOf("wy", "qq") && credential.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "用户凭证无效" }
        val data = JsonParser.parseString(credential).asJsonObject
        fun text(key: String) = data.get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString.orEmpty()
        if (platform == "wy") {
            require(data.keySet().all { it in setOf("MUSIC_A_T", "MUSIC_R_T", "MUSIC_SNS", "MUSIC_U", "NMTID", "__csrf") } && text("MUSIC_U").isNotBlank()) { "网易云凭证无效" }
            require(data.entrySet().all { it.value.isJsonPrimitive && it.value.asJsonPrimitive.isString && !it.value.asString.contains(';') && !it.value.asString.contains('\n') && !it.value.asString.contains('\r') }) { "网易云凭证格式无效" }
        } else {
            require(data.keySet().all { it in setOf("musicid", "musickey", "encryptUin", "loginType") }) { "QQ音乐凭证包含不支持的字段" }
            require(text("musicid").toLongOrNull()?.let { it > 0 } == true && text("musickey").isNotBlank() && text("encryptUin").isNotBlank()) { "QQ音乐凭证无效" }
            require(listOf(text("musickey"), text("encryptUin")).all { value -> value.none { it == '\n' || it == '\r' || it == '\u0000' } }) { "QQ音乐凭证格式无效" }
            if (data.has("loginType")) require(runCatching { data.get("loginType").asInt in 1..2 }.getOrDefault(false)) { "QQ音乐登录方式无效" }
        }
    }
}
