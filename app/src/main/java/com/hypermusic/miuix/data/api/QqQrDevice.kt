package com.hypermusic.miuix.data.api

import android.content.Context
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.math.BigInteger
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.RSAPublicKeySpec
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Per-install QQ Music Android protocol identity; never contains account credentials. */
internal class QqQrDevice private constructor(
    val androidId: String, val openUdid: String, private val imei: String,
    val fingerprint: String, private val kernel: String,
    var qimei: String = "", var qimei36: String = "", var savedAt: Long = 0,
    var uid: String = "", var sid: String = ""
) {
    private val gson = Gson()
    private val random = SecureRandom()
    private fun digits(count: Int) = (1..count).joinToString("") { random.nextInt(10).toString() }
    private fun hex(count: Int) = ByteArray((count + 1) / 2).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }.take(count)
    private fun md5(value: String) = MessageDigest.getInstance("MD5").digest(value.toByteArray()).joinToString("") { "%02x".format(it.toInt() and 255) }

    fun comm(overrides: Map<String, Any> = emptyMap()): Map<String, Any> = buildMap {
        putAll(mapOf("ct" to 11, "cv" to 14090008, "v" to 14090008, "chid" to "10003505", "tmeAppID" to "qqmusic",
            "QIMEI" to qimei, "QIMEI36" to qimei36, "OpenUDID" to openUdid, "udid" to openUdid,
            "OpenUDID2" to openUdid, "aid" to androidId, "os_ver" to "10", "phonetype" to "MI 6",
            "devicelevel" to "29", "newdevicelevel" to "29", "rom" to fingerprint))
        if (uid.isNotBlank()) put("uid", uid)
        if (sid.isNotBlank()) put("sid", sid)
        putAll(overrides)
    }

    fun requestQimei(client: OkHttpClient) {
        if (qimei.isNotBlank() && qimei36.isNotBlank() && System.currentTimeMillis() - savedAt < 86_400_000) return
        val now = System.currentTimeMillis()
        val timestamp = now / 1000
        val date = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val month = date.format(Date(now)).substring(0, 7) + "-01"
        val first = digits(6); val second = digits(9)
        val dated = setOf(1, 2, 13, 14, 17, 18, 21, 22, 25, 26, 29, 30, 33, 34, 37, 38)
        val beacon = (1..40).joinToString(";", postfix = ";") { n -> "k$n:" + when {
            n in dated -> "$month$first.$second"
            n == 3 -> "0000000000000000"
            n == 4 -> hex(16).replace('0', '1')
            else -> random.nextInt(10000).toString()
        } }
        val reserved = mapOf("harmony" to "0", "clone" to "0", "containe" to "", "oz" to "UhYmelwouA+V2nPWbOvLTgN2/m8jwGB+yUB5v9tysQg=",
            "oo" to "Xecjt+9S1+f8Pz2VLSxgpw==", "kelong" to "0", "uptimes" to date.format(Date(now - random.nextInt(14401) * 1000L)),
            "multiUser" to "0", "bod" to "Xiaomi", "dv" to "sagit", "firstLevel" to "", "manufact" to "Xiaomi", "name" to "MI 6", "host" to "se.infra", "kernel" to kernel)
        val payload = mapOf("androidId" to androidId, "platformId" to 1, "appKey" to "0AND0HD6FE4HY80F", "appVersion" to "14.9.0.8",
            "beaconIdSrc" to beacon, "brand" to "Xiaomi", "channelId" to "10003505", "cid" to "", "imei" to imei, "imsi" to "", "mac" to "",
            "model" to "MI 6", "networkType" to "unknown", "oaid" to "", "osVersion" to "Android 10,level 29", "qimei" to "", "qimei36" to "",
            "sdkVersion" to "1.2.13.6", "targetSdkVersion" to "33", "audit" to "", "userId" to "{}", "packageId" to "com.tencent.qqmusic",
            "deviceType" to "Phone", "sdkName" to "", "reserved" to gson.toJson(reserved))
        val key = hex(16).toByteArray()
        val modulus = BigInteger("c4231830a2eb5fc2827170641e79d80fec51bda9a22e4b4ab37d1f205a4ae44d928cda25879f66a3429051663312a127faf8a246bdaaf63918417e90d7c95b5908aa6a2d0f852e4a6770294a548ac1c2fe8f1f252fb826f4ac86ab9a00e7ce47d002a56e7c4b51eb889acc60ca6adbc9f72e81f4d31b1dd7464805264530ab1d", 16)
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA").generatePublic(RSAPublicKeySpec(modulus, BigInteger.valueOf(65537)))) }
        val aes = Cipher.getInstance("AES/CBC/PKCS5Padding").apply { init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key)) }
        val encodedKey = Base64.getEncoder().encodeToString(rsa.doFinal(key))
        val params = Base64.getEncoder().encodeToString(aes.doFinal(gson.toJson(payload).toByteArray()))
        val nonce = hex(16); val extra = "{\"appKey\":\"0AND0HD6FE4HY80F\"}"
        val body = mapOf("app" to 0, "os" to 1, "qimeiParams" to mapOf("key" to encodedKey, "params" to params, "time" to timestamp.toString(), "nonce" to nonce,
            "sign" to md5(encodedKey + params + (timestamp * 1000) + nonce + "ZdJqM15EeO2zWc08" + extra), "extra" to extra))
        val request = Request.Builder().url("https://api.tencentmusic.com/tme/trpc/proxy")
            .header("method", "GetQimei").header("service", "trpc.tme_datasvr.qimeiproxy.QimeiProxy")
            .header("appid", "qimei_qq_android").header("sign", md5("qimei_qq_androidpzAuCmaFAaFaHrdakPjLIEqKrGnSOOvH$timestamp"))
            .header("User-Agent", "QQMusic").header("timestamp", timestamp.toString())
            .post(gson.toJson(body).toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "QIMEI HTTP ${response.code}" }
            val outer = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val innerElement = outer.get("data")
            val inner = if (innerElement.isJsonPrimitive) JsonParser.parseString(innerElement.asString).asJsonObject else innerElement.asJsonObject
            val data = inner.getAsJsonObject("data")
            qimei = data.get("q16")?.asString.orEmpty(); qimei36 = data.get("q36")?.asString.orEmpty()
            check(qimei.isNotBlank() && qimei36.isNotBlank()) { "QIMEI 设备初始化失败" }
            savedAt = now
        }
    }

    fun musicu(client: OkHttpClient, module: String, method: String, params: Map<String, Any>, overrides: Map<String, Any> = emptyMap()): JsonObject {
        val body = gson.toJson(mapOf("comm" to comm(overrides), "req_0" to mapOf("module" to module, "method" to method, "param" to params)))
        val request = Request.Builder().url("https://u.y.qq.com/cgi-bin/musicu.fcg")
            .header("User-Agent", "QQMusic 14090008(android 10)")
            .post(body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "QQ 音乐登录请求失败（HTTP ${response.code}）" }
            val root = JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject
            val result = root.getAsJsonObject("req_0") ?: error("QQ 音乐登录响应缺少数据")
            val global = root.get("code")?.asInt ?: 0
            val code = result.get("code")?.asInt ?: 0
            check(global == 0 && code == 0) { "QQ 音乐登录请求失败（$global/$code）" }
            return result.getAsJsonObject("data") ?: error("QQ 音乐登录响应缺少内容")
        }
    }

    fun refreshSession(client: OkHttpClient) {
        val result = musicu(client, "music.getSession.session", "GetSession", mapOf("uid" to uid, "vkey" to 0, "caller" to 0))
        val session = result.getAsJsonObject("session") ?: error("QQ 音乐设备会话缺失")
        uid = session.get("uid")?.asString.orEmpty()
        sid = session.get("sid")?.asString.orEmpty()
        check(uid.isNotBlank() && sid.isNotBlank()) { "QQ 音乐设备会话无效" }
    }

    fun createQr(client: OkHttpClient): Pair<String, ByteArray> {
        val data = musicu(client, "music.login.LoginServer", "CreateQRCode", mapOf("tmeAppID" to "qqmusic", "ct" to 11, "cv" to 14090008), mapOf("ct" to 23, "cv" to 0))
        val id = data.get("qrcodeID")?.asString.orEmpty()
        val image = Base64.getDecoder().decode(data.get("qrcode")?.asString.orEmpty().substringAfterLast(','))
        check(id.isNotBlank() && image.size >= 8 && image.take(8) == listOf(0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a).map(Int::toByte)) { "QQ 音乐二维码响应无效" }
        return id to image
    }

    private data class Stored(val androidId: String, val openUdid: String, val imei: String, val fingerprint: String,
        val kernel: String, val qimei: String, val qimei36: String, val savedAt: Long, val uid: String, val sid: String)
    fun save(context: Context) {
        val stored = Stored(androidId, openUdid, imei, fingerprint, kernel, qimei, qimei36, savedAt, uid, sid)
        context.getSharedPreferences("qq_qr_device", Context.MODE_PRIVATE).edit().putString("identity", gson.toJson(stored)).apply()
    }

    companion object {
        fun load(context: Context): QqQrDevice {
            val gson = Gson()
            val prefs = context.getSharedPreferences("qq_qr_device", Context.MODE_PRIVATE)
            val existing = runCatching { gson.fromJson(prefs.getString("identity", null), Stored::class.java) }.getOrNull()
            if (existing != null && !existing.androidId.isNullOrBlank() && !existing.openUdid.isNullOrBlank() &&
                !existing.imei.isNullOrBlank() && !existing.fingerprint.isNullOrBlank() && !existing.kernel.isNullOrBlank()) {
                return QqQrDevice(existing.androidId, existing.openUdid, existing.imei, existing.fingerprint, existing.kernel,
                    existing.qimei.orEmpty(), existing.qimei36.orEmpty(), existing.savedAt, existing.uid.orEmpty(), existing.sid.orEmpty())
            }
            val random = SecureRandom()
            fun hex(n: Int) = ByteArray(n / 2).also(random::nextBytes).joinToString("") { "%02x".format(it.toInt() and 255) }
            val digits = (1..14).map { random.nextInt(10) }
            val sum = digits.mapIndexed { i, n -> if (i % 2 == 1) (n * 2).let { if (it > 9) it - 9 else it } else n }.sum()
            return QqQrDevice(hex(16), hex(32), digits.joinToString("") + ((10 - sum % 10) % 10),
                "xiaomi/iarim/sagit:10/eomam.200122.001/${random.nextInt(9000000) + 1000000}:user/release-keys",
                "Linux 5.4.0-54-generic-${hex(8)} (android-build@google.com)").also { it.save(context) }
        }
    }
}
