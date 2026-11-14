package com.hypermusic.miuix.data.api

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PlatformAuthMockTest {
    @Test fun bodianInvalidSongRowsAreNotReportedAsAnEmptyResult() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "bodian_bad_rows_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"code":200,"data":{"total":1,"resultList":[{"unexpected":"row"}]}}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val result = runCatching { BodianCatalog(BodianClient(isolated, http)).searchSongs("歌曲") }
            assertTrue(result.isFailure)
            assertEquals("波点音乐歌曲列表格式暂不支持", result.exceptionOrNull()?.message)
        } finally { target.deleteSharedPreferences(prefix + "bodian_prefs") }
    }

    @Test fun qishuiExpiredSessionRenewsAndRetainsAccountIdentity() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "qishui_refresh_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        val paths = mutableListOf<String>()
        val holdRefresh = java.util.concurrent.atomic.AtomicBoolean(false)
        val rejectRefresh = java.util.concurrent.atomic.AtomicBoolean(false)
        val entered = java.util.concurrent.CountDownLatch(1)
        val release = java.util.concurrent.CountDownLatch(1)
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val path = request.url.encodedPath
            paths += path
            val response = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            val body = when (path) {
                "/api/luna/v1/platform/access/code/refresh/" -> {
                    if (holdRefresh.get()) { entered.countDown(); release.await() }
                    if (rejectRefresh.get()) """{"err_no":500,"data":{}}"""
                    else """{"err_no":0,"data":{"access_code":"new-access","expires_time":7200}}"""
                }
                "/passport/open/get_access_ticket/" -> {
                    assertEquals(if (holdRefresh.get()) "new-cert" else "old-cert", request.header("bd-ticket-guard-client-cert"))
                    val ticket = """{"ticket":"new-ticket","client_cert":"new-cert","ts_sign":"new-signature"}"""
                    response.header("bd-ticket-guard-server-data", java.util.Base64.getEncoder().encodeToString(ticket.toByteArray()))
                    "{}"
                }
                "/api/luna/v1/platform/me/info/" -> """{"err_no":0,"data":{"my_info":{"id":"123"}}}"""
                else -> error("unexpected mock path")
            }
            response.body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val client = QishuiClient(isolated, http)
            val guard = QishuiGuard.create()
            QishuiSessionStore(isolated).save(QishuiSessionStore.Session(client.deviceId, "123", "测试", "", guard.privateKey,
                "old-ticket", "old-cert", "old-signature", "old-access", "open", 1L))
            val auth = QishuiAuth(isolated, client)
            val renewed = requireNotNull(auth.validSession())
            assertEquals("123", renewed.userId)
            assertEquals("new-ticket", renewed.ticket)
            assertEquals("new-access", renewed.accessCode)
            assertEquals(renewed, auth.account())
            assertEquals(3, paths.size)
            auth.validSession()
            assertEquals(3, paths.size)
            auth.logout()
            val expired = renewed.copy(expiresAt = 1L)
            QishuiSessionStore(isolated).save(expired)
            rejectRefresh.set(true)
            assertTrue(runCatching { auth.validSession() }.isFailure)
            assertEquals(expired, auth.account())
            rejectRefresh.set(false)
            holdRefresh.set(true)
            val pending = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).async { runCatching { auth.validSession() } }
            try {
                assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                auth.logout()
            } finally { release.countDown() }
            assertTrue(pending.await().isFailure)
            assertNull(auth.account())
        } finally {
            target.deleteSharedPreferences(prefix + "qishui_prefs")
            target.deleteSharedPreferences(prefix + "qishui_secure_session")
        }
    }

    @Test fun qishuiSongCollectionUsesCurrentGuardedSession() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "qishui_mock_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        val writes = mutableListOf<Pair<String, com.google.gson.JsonObject>>()
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("ticket-test", request.header("access-token"))
            assertNotNull(request.header("bd-ticket-guard-client-data"))
            val buffer = okio.Buffer()
            requireNotNull(request.body).writeTo(buffer)
            val body = com.google.gson.JsonParser.parseString(buffer.readUtf8()).asJsonObject
            val path = request.url.encodedPath
            writes += path to body
            val reply = when (path) {
                "/api/luna/v1/platform/me/playlist/" -> """{"err_no":0,"data":{"playlists":[{"base_info":{"id":"11","owner_id":"123","type":1}}],"has_more":false}}"""
                "/api/luna/v1/platform/me/collection/mixed/" -> if (body["cursor"].asString.isEmpty())
                    """{"err_no":0,"data":{"mixed_collections":[{"playlist":{"base_info":{"id":"22","owner_id":"321","type":0}}}],"has_more":true,"next_cursor":"next"}}"""
                    else """{"err_no":0,"data":{"mixed_collections":[{"playlist":{"base_info":{"id":"33","owner_id":"654","type":0}}}],"has_more":false}}"""
                else -> """{"err_no":0,"data":{}}"""
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(reply.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val client = QishuiClient(isolated, http)
            val guard = QishuiGuard.create()
            QishuiSessionStore(isolated).save(QishuiSessionStore.Session(client.deviceId, "123", "测试", "", guard.privateKey,
                "ticket-test", "cert-test", "signature-test", "access-test", "open-test", Long.MAX_VALUE))
            val auth = QishuiAuth(isolated, client)
            val library = QishuiLibrary(client, auth)
            val owned = library.playlists()
            assertEquals("11", owned.items.single().id)
            assertTrue(owned.items.single().owned)
            assertTrue(owned.items.single().liked)
            val collected = library.collectedPlaylists()
            assertEquals("22", collected.items.single().id)
            assertFalse(collected.items.single().owned)
            assertEquals("next", collected.nextCursor)
            assertEquals("33", library.collectedPlaylists(requireNotNull(collected.nextCursor)).items.single().id)
            writes.clear()
            assertTrue(runCatching { library.setSongLiked("456", true, "other-account") }.isFailure)
            assertTrue(writes.isEmpty())
            library.setSongLiked("456", true)
            library.setSongLiked("456", false)
            assertEquals(listOf("/api/luna/v1/platform/media/collect/", "/api/luna/v1/platform/media/cancel/collect/"), writes.map { it.first })
            writes.forEach { (_, body) ->
                assertEquals("123", body.getAsJsonObject("common_params")["user_id"].asString)
                assertEquals("456", body.getAsJsonArray("media").single().asJsonObject["id"].asString)
            }
            auth.logout()
            assertNull(auth.account())
        } finally {
            target.deleteSharedPreferences(prefix + "qishui_prefs")
            target.deleteSharedPreferences(prefix + "qishui_secure_session")
        }
    }

    @Test fun cancelledPollingCannotStoreLateCredentials() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "auth_cancel_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        try {
            for (platform in listOf("bodian", "kugou")) {
                val entered = java.util.concurrent.CountDownLatch(1)
                val release = java.util.concurrent.CountDownLatch(1)
                val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
                    val request = chain.request()
                    val json = when (request.url.encodedPath) {
                        "/api/ucenter/login/qrCode" -> """{"code":200,"data":{"qrCode":"key"}}"""
                        "/api/ucenter/login/qrCodeStatus" -> { entered.countDown(); release.await(); """{"code":200,"data":{"status":3}}""" }
                        "/api/ucenter/users/login" -> """{"code":200,"data":{"id":"123","token":"test-token"}}"""
                        "/v2/qrcode" -> """{"data":{"qrcode":"key"}}"""
                        "/v2/get_userinfo_qrcode" -> { entered.countDown(); release.await(); """{"data":{"status":4,"userid":"456","token":"test-token"}}""" }
                        else -> error("unexpected mock path")
                    }
                    Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                        .body(json.toResponseBody("application/json".toMediaType())).build()
                }.build()
                val bodian = if (platform == "bodian") BodianAuth(isolated, BodianClient(isolated, http)) else null
                val kugou = if (platform == "kugou") KugouAuth(isolated, KugouClient(isolated, http)) else null
                val b = bodian?.createQr()
                val k = kugou?.createQr()
                val job = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    if (b != null) bodian!!.check(b) else kugou!!.check(k!!)
                }
                try {
                    assertTrue(entered.await(10, java.util.concurrent.TimeUnit.SECONDS))
                    job.cancel()
                } finally { release.countDown() }
                job.join()
                assertNull(bodian?.currentAccount())
                assertNull(kugou?.account())
                b?.let { bodian?.cancel(it) }
                k?.let { kugou?.cancel(it) }
            }
        } finally {
            target.deleteSharedPreferences(prefix + "bodian_prefs")
            target.deleteSharedPreferences(prefix + "kugou_prefs")
        }
    }

    @Test fun confirmedQrPersistsAndLogoutClearsEachAccount() = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "auth_mock_${UUID.randomUUID()}_"
        val isolated = object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
        val mutations = mutableListOf<Pair<String, com.google.gson.JsonObject>>()
        var kugouLiked = false
        val http = okhttp3.OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val json = when (request.url.encodedPath) {
                "/api/ucenter/login/qrCode" -> """{"code":200,"data":{"qrCode":"bodian-key"}}"""
                "/api/ucenter/login/qrCodeStatus" -> """{"code":200,"data":{"status":3}}"""
                "/api/ucenter/users/login" -> """{"code":200,"data":{"id":"123","token":"test-token","user":{"id":"123","nickname":"测试"}}}"""
                "/api/service/playlist/fond" -> """{"code":200,"data":{"id":"999","name":"我喜欢"}}"""
                "/api/service/playlist/userCreate" -> """{"code":200,"data":{"playLists":[{"id":"4321","name":"自建歌单","sourceType":5}]}}"""
                "/api/service/collect/4/list" -> """{"code":200,"data":{"playLists":[{"id":"1234","name":"歌单","sourceType":4},{"albumId":"5678","name":"专辑","sourceType":6}],"total":2}}"""
                "/api/service/playlist/music", "/api/service/playlist/music/delete", "/api/service/collect" -> {
                    assertEquals("123", request.url.queryParameter("uid"))
                    val buffer = okio.Buffer()
                    requireNotNull(request.body).writeTo(buffer)
                    mutations += request.url.encodedPath to com.google.gson.JsonParser.parseString(buffer.readUtf8()).asJsonObject
                    """{"code":200,"data":{}}"""
                }
                "/v2/qrcode" -> """{"data":{"qrcode":"kugou-key"}}"""
                "/v2/get_userinfo_qrcode" -> """{"data":{"status":4,"userid":"456","token":"test-token","nickname":"测试"}}"""
                "/v7/get_all_list" -> """{"status":1,"data":{"total":1,"lists":[{"listid":2,"global_collection_id":"collection_1","name":"我喜欢","type":0}]}}"""
                "/pubsongs/v2/get_other_list_file_nofilt" -> if (kugouLiked)
                    """{"status":1,"data":{"count":1,"songs":[{"hash":"16C8AB298231370293D16BCF9E5FF9B6","fileid":77}]}}"""
                    else """{"status":1,"data":{"count":0,"songs":[]}}"""
                "/cloudlist.service/v6/add_song", "/v4/delete_songs" -> {
                    assertEquals("456", request.url.queryParameter("userid"))
                    val buffer = okio.Buffer()
                    requireNotNull(request.body).writeTo(buffer)
                    mutations += request.url.encodedPath to com.google.gson.JsonParser.parseString(buffer.readUtf8()).asJsonObject
                    kugouLiked = request.url.encodedPath.endsWith("add_song")
                    """{"status":1}"""
                }
                else -> error("unexpected mock path")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(json.toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val bodianClient = BodianClient(isolated, http)
            val bodian = BodianAuth(isolated, bodianClient)
            val b = bodian.createQr()
            assertEquals(BodianAuth.State.CONFIRMED, bodian.check(b))
            assertEquals("123", bodian.currentAccount()?.uid)
            val library = BodianLibrary(bodianClient, bodian, BodianCatalog(bodianClient))
            assertTrue(runCatching { library.setSongLiked("bodian_1234", true, "other-account") }.isFailure)
            assertTrue(mutations.isEmpty())
            library.setSongLiked("bodian_1234", true)
            library.setSongLiked("bodian_1234", false)
            assertEquals(listOf("/api/service/playlist/music", "/api/service/playlist/music/delete"), mutations.map { it.first })
            mutations.forEach { (_, body) ->
                assertEquals(999L, body["playListId"].asLong)
                assertEquals(1234L, body.getAsJsonArray("musicIdList").single().asLong)
            }
            assertTrue(library.isCollected("1234", album = false))
            assertTrue(library.isCollected("4321", album = false))
            assertTrue(library.isCollected("999", album = false))
            assertTrue(library.isCollected("5678", album = true))
            val revision = bodian.sessionRevision()
            val beforeCollection = mutations.size
            assertTrue(runCatching { library.collect("1234", 4, revision - 1) }.isFailure)
            assertEquals(beforeCollection, mutations.size)
            library.collect("1234", 4, revision)
            library.collect("5678", 6, revision)
            assertEquals(listOf(4, 6), mutations.takeLast(2).map { it.second["source"].asInt })
            assertEquals(listOf(1234L, 5678L), mutations.takeLast(2).map { it.second.getAsJsonArray("sourceId").single().asLong })
            assertTrue(mutations.takeLast(2).all { it.second["op"].asInt == 1 && it.second["uid"].asLong == 123L })
            bodian.logout()
            assertNull(bodian.currentAccount())
            assertEquals(BodianAuth.State.EXPIRED, bodian.check(b))

            val kugouClient = KugouClient(isolated, http)
            val hashOnly = com.hypermusic.miuix.data.model.Track("kugou_16c8ab298231370293d16bcf9e5ff9b6", "", "", "", "", "", "kugou", "", "")
            assertEquals("16C8AB298231370293D16BCF9E5FF9B6", kugouClient.trackHash(hashOnly))
            val kugou = KugouAuth(isolated, kugouClient)
            val k = kugou.createQr()
            assertEquals(KugouAuth.State.CONFIRMED, kugou.check(k))
            assertEquals("456", kugou.account()?.userId)
            val song = KugouCatalog.parseTrack(com.google.gson.JsonParser.parseString("""{"hash":"16C8AB298231370293D16BCF9E5FF9B6","name":"歌曲"}""").asJsonObject)
            val kugouLibrary = KugouLibrary(kugouClient, kugou)
            val mutationsBeforeMismatch = mutations.size
            assertTrue(runCatching { kugouLibrary.setLiked(song, true, "other-account") }.isFailure)
            val album = com.hypermusic.miuix.data.model.AlbumInfo(id = 10, name = "测试专辑", artistId = 20)
            val playlist = com.hypermusic.miuix.data.model.Playlist(id = "10", title = "测试歌单", coverImgUrl = "", sourceUrl = "")
            assertTrue(runCatching { kugouLibrary.collectAlbum(album, expectedUserId = "other-account") }.isFailure)
            assertTrue(runCatching { kugouLibrary.collectPlaylist(playlist, expectedGeneration = kugou.sessionGeneration() - 1) }.isFailure)
            assertEquals(mutationsBeforeMismatch, mutations.size)
            assertFalse(kugouLibrary.isLiked(song))
            kugouLibrary.setLiked(song, true)
            assertTrue(kugouLibrary.isLiked(song))
            kugouLibrary.setLiked(song, false)
            val kugouWrites = mutations.takeLast(2)
            assertEquals(listOf("/cloudlist.service/v6/add_song", "/v4/delete_songs"), kugouWrites.map { it.first })
            assertEquals("2", kugouWrites[0].second["listid"].asString)
            assertEquals(77L, kugouWrites[1].second.getAsJsonArray("data").single().asJsonObject["fileid"].asLong)
            kugou.logout()
            assertNull(kugou.account())
            assertEquals(KugouAuth.State.EXPIRED, kugou.check(k))
        } finally {
            target.deleteSharedPreferences(prefix + "bodian_prefs")
            target.deleteSharedPreferences(prefix + "kugou_prefs")
        }
    }
}
