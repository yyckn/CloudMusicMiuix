package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request

/** 收藏写操作必须使用同一份会话的 Cookie 和 CSRF，不能在请求中途读取另一个账号。 */
internal object NeteaseCollectionRequest {
    fun build(kind: String, id: Long, favorite: Boolean, cookie: String,
        encrypt: (String) -> Map<String, String> = NeteaseCrypto::weapi): Request {
        require(kind == "album" || kind == "playlist")
        require(id > 0)
        val csrf = cookie.split(';').map { it.trim() }.firstOrNull { it.startsWith("__csrf=") }
            ?.substringAfter('=').orEmpty()
        val action = if (kind == "playlist") {
            if (favorite) "subscribe" else "unsubscribe"
        } else if (favorite) "sub" else "unsub"
        val encrypted = encrypt(JsonObject().apply {
            addProperty("id", id)
            addProperty("csrf_token", csrf)
        }.toString())
        val url = "https://music.163.com/weapi/$kind/$action".toHttpUrl().newBuilder()
            .addQueryParameter("csrf_token", csrf).build()
        return Request.Builder().url(url)
            .post(FormBody.Builder().add("params", encrypted.getValue("params"))
                .add("encSecKey", encrypted.getValue("encSecKey")).build())
            .header("Cookie", cookie)
            .header("Origin", "https://music.163.com")
            .header("Referer", "https://music.163.com/st/webplayer")
            .header("x-music-web-os", "web3")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36")
            .build()
    }

    /** 网页端明确拒绝写请求时，使用 PC 端同名接口；不对超时或未知结果自动重试。 */
    fun buildPcPlaylist(id: Long, favorite: Boolean, cookie: String): Request {
        require(id > 0)
        val action = if (favorite) "subscribe" else "unsubscribe"
        val path = "/api/playlist/$action"
        val data = JsonObject().apply { addProperty("id", id); addProperty("e_r", false) }
        val body = FormBody.Builder().add("params", NeteaseCrypto.eapi(path, data.toString()).getValue("params")).build()
        return Request.Builder().url("https://interface3.music.163.com/eapi/playlist/$action")
            .header("Cookie", "os=pc; $cookie")
            .header("Referer", "https://music.163.com")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Safari/537.36 Chrome/91.0.4472.164 NeteaseMusicDesktop/3.1.29.205117")
            .post(body).build()
    }
}
