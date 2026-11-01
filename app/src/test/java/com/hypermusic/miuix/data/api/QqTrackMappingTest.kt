package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class QqTrackMappingTest {
    private fun parse(pay: String) = QqMusicApi.run {
        JsonParser.parseString("""{"id":97773,"mid":"0039MnYb0qxYhV","title":"晴天","interval":269,
            "singer":[{"name":"周杰伦","mid":"0025NhlN2yWrP4"}],
            "album":{"id":8220,"mid":"000MkMni19ClKG","name":"叶惠美"},
            "file":{"media_mid":"003Qui1q2u1Zho"},"pay":$pay}""").asJsonObject.toTrack()!!
    }

    @Test fun vipPlaybackRequiresBothMembershipAndPayPlay() {
        assertTrue(parse("""{"pay_month":1,"pay_play":1}""").isVip)
        assertFalse(parse("""{"pay_month":1,"pay_play":0}""").isVip)
        assertFalse(parse("""{"pay_month":0,"pay_play":1}""").isVip)
    }

    @Test fun keepsQqIdentifiersAndAlbumInformation() {
        val track = parse("""{"pay_month":0,"pay_play":0}""")
        assertEquals("qqmusic", track.source)
        assertEquals("qqartist_0025NhlN2yWrP4", track.artistId)
        assertEquals("8220", track.albumId)
        assertEquals("003Qui1q2u1Zho", track.qqMediaMid)
        assertEquals(269000L, track.duration)
    }
}
