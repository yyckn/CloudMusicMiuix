package com.hypermusic.miuix.data.audio

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.URI
import java.nio.charset.CodingErrorAction
import java.util.Base64
import kotlin.math.roundToInt

/** Compact, versioned and strictly validated QR payload. No private data is embedded. */
object SoundPresetLink {
    data class Payload(val name: String, val effects: SoundEffects)
    fun encode(name: String, effects: SoundEffects): String {
        val clean = name.trim()
        val nameBytes = clean.toByteArray(Charsets.UTF_8)
        require(clean.isNotBlank() && clean.length <= 80 && nameBytes.size <= 240 && effects.valid()) { "音效预设无效" }
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { data ->
            data.writeByte(1)
            data.writeByte(nameBytes.size)
            data.write(nameBytes)
            effects.bands.forEach { data.writeShort((it * 100).roundToInt()) }
            data.writeShort(effects.highpass.roundToInt())
            data.writeShort(effects.lowpass.roundToInt())
            listOf(effects.drive, effects.crush, effects.wow, effects.noise, effects.width, effects.space, effects.punch)
                .forEach { data.writeShort((it * 10000).roundToInt()) }
        }
        return "hypermusic://sound?data=" + Base64.getUrlEncoder().withoutPadding().encodeToString(out.toByteArray())
    }
    fun decode(value: String): Payload {
        require(value.length <= 600) { "音效二维码内容过长" }
        val uri = URI(value)
        require(uri.scheme == "hypermusic" && uri.host == "sound" && uri.userInfo == null && uri.port == -1 &&
            uri.fragment == null && uri.rawPath.isNullOrEmpty()) { "不是有效的音效二维码" }
        val query = uri.rawQuery ?: error("缺少音效数据")
        require(query.startsWith("data=") && query.count { it == '=' } == 1 && '&' !in query) { "音效二维码参数无效" }
        val encoded = query.removePrefix("data=")
        require(encoded.isNotEmpty() && encoded.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "音效二维码编码无效" }
        val bytes = Base64.getUrlDecoder().decode(encoded)
        require(bytes.size in 40..278) { "音效数据长度无效" }
        return DataInputStream(ByteArrayInputStream(bytes)).use { data ->
            require(data.readUnsignedByte() == 1) { "不支持的音效版本" }
            val length = data.readUnsignedByte()
            require(length in 1..240 && bytes.size == 40 + length) { "音效数据长度无效" }
            val nameBytes = ByteArray(length).also { data.readFully(it) }
            val name = Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(nameBytes)).toString()
            require(name.isNotBlank() && name.length <= 80 && name.none { it.isISOControl() }) { "音效名称无效" }
            val bands = List(10) { data.readShort() / 100f }
            val highpass = data.readUnsignedShort().toFloat()
            val lowpass = data.readUnsignedShort().toFloat()
            val values = List(7) { data.readUnsignedShort() / 10000f }
            val effects = SoundEffects(bands, highpass, lowpass, values[0], values[1], values[2], values[3], values[4], values[5], values[6])
            require(effects.valid() && data.available() == 0) { "音效参数超出范围" }
            Payload(name, effects)
        }
    }
}
