package com.hypermusic.miuix.data.api

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AdditionalRecommendationsTest {
    private fun fixture(name: String): JsonObject = JsonParser.parseString(javaClass.getResource("/recommendations/$name.json")!!.readText()).asJsonObject

    @Test fun dailyRecommendationSecondsBecomeMillisecondsAndKeepCoverAndAlbum() {
        val track = AdditionalRecommendations.parseKugouRecommendations(fixture("kugou-daily")).single()
        assertEquals("唯一", track.title)
        assertEquals(253000L, track.duration)
        assertEquals("T.I.M.E.", track.album)
        assertTrue(track.imgUrl.startsWith("https://"))
        assertFalse(track.imgUrl.contains("{size}"))
        assertEquals("kugou", track.source)
        assertTrue(track.isVip)
    }

    @Test fun youthCardNestedAudioAndAlbumAreNotLost() {
        val track = AdditionalRecommendations.parseKugouRecommendations(fixture("kugou-card")).single()
        assertEquals(186514L, track.duration)
        assertEquals("พี่ชอบหนูที่สุดเลย", track.title)
        assertEquals("81505465", track.albumId)
        assertEquals("พี่ชอบหนูที่สุดเลย", track.album)
        assertEquals(listOf("8448663", "998017"), track.artistIds)
        assertTrue(track.imgUrl.startsWith("https://"))
        assertEquals(32, track.platform.kugouHash.length)
    }

    @Test fun personalFmSongsKeepTheirOwnTimingAndIdentity() {
        val track = AdditionalRecommendations.parseKugouRecommendations(fixture("kugou-fm")).single()
        assertEquals("一字一句", track.title)
        assertEquals(292000L, track.duration)
        assertEquals(listOf("胡66"), track.artistNames)
        assertTrue(track.id.startsWith("kugou_"))
    }

    @Test fun qishuiScenesUseServerIdsAndEveryCardIsARadio() {
        val scenes = AdditionalRecommendations.parseQishuiScenes(fixture("qishui-scenes"))
        assertEquals(listOf("专注模式", "摸鱼", "驾车", "通勤必听"), scenes.map { it.title })
        assertEquals("extra:qishui:scene:6", scenes.first().id)
        assertTrue(scenes.all { it.radio && it.cover.startsWith("https://") && !it.cover.endsWith("/img/") })
    }

    @Test fun qishuiRadioUnwrapsMediaInsteadOfParsingCardMetadataAsTracks() {
        val songs = AdditionalRecommendations.parseQishuiFeed(fixture("qishui-feed"))
        assertEquals("qishui_7559252187965376528", songs.single().id)
        assertEquals(138508L, songs.single().duration)
        assertEquals("禅境·茶乐", songs.single().title)
    }

    @Test fun bodianPlaylistSectionsRetainProviderSource() {
        val data = JsonParser.parseString("""{"lists":[{"title":"最热歌单","playLists":[{"id":123,"sourceType":4,"name":"歌单","pic":"http://img1.kuwo.cn/cover.jpg","playNum":456}]}]}""").asJsonObject
        val sections = AdditionalRecommendations.parseBodianSections(data)
        assertEquals("最热歌单", sections.single().title)
        assertEquals("bodian:4:123", sections.single().cards.single().id)
        assertEquals("https://img1.kuwo.cn/cover.jpg", sections.single().cards.single().cover)
        assertFalse(sections.single().cards.single().radio)
    }

    @Test fun missingFeedIsAnErrorRatherThanAnEmptySuccessfulRecommendation() {
        assertThrows(IllegalStateException::class.java) { AdditionalRecommendations.parseQishuiFeed(JsonObject()) }
        assertThrows(IllegalStateException::class.java) { AdditionalRecommendations.parseKugouRecommendations(JsonObject()) }
        assertThrows(IllegalStateException::class.java) { AdditionalRecommendations.parseBodianSections(JsonObject()) }
    }

    @Test fun bodianPlayCountsSwitchToTenThousandsAt10000() {
        assertEquals("9999次播放", AdditionalRecommendations.formatBodianPlayCount(9999))
        assertEquals("1万次播放", AdditionalRecommendations.formatBodianPlayCount(10000))
        assertEquals("1.2万次播放", AdditionalRecommendations.formatBodianPlayCount(12345))
        assertEquals("902.7万次播放", AdditionalRecommendations.formatBodianPlayCount(9027150))
        assertEquals("10000万次播放", AdditionalRecommendations.formatBodianPlayCount(100000000))
    }

    @Test fun successfulEmptyKugouCardDoesNotBecomeAHomePageError() {
        val response = JsonParser.parseString("""{"status":1,"error_code":0,"data":{"song_list":[],"card_info":{"card_id":3014},"song_count":0}}""")
        assertTrue(AdditionalRecommendations.parseKugouRecommendations(response).isEmpty())
    }

    @Test fun unparseableNonemptySongsAreNotMistakenForAnEmptyRecommendation() {
        val response = JsonParser.parseString("""{"data":{"song_list":[{"unknown_song_field":"x","authors":[]}]}}""")
        val error = assertThrows(IllegalStateException::class.java) { AdditionalRecommendations.parseKugouRecommendations(response) }
        assertEquals("酷狗推荐歌曲格式暂不支持", error.message)
    }

    @Test fun unrelatedEmptyArraysDoNotMakeAMalformedResponseSuccessful() {
        val response = JsonParser.parseString("""{"data":{"authors":[],"tags":[]}}""")
        val error = assertThrows(IllegalStateException::class.java) { AdditionalRecommendations.parseKugouRecommendations(response) }
        assertEquals("酷狗推荐响应缺少歌曲列表", error.message)
    }
}
