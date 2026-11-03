package com.hypermusic.miuix.ui.pages

import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerArtistAlbumTextTest {
    @Test fun individualArtistsHaveTheirOwnLinksWithoutChangingDisplayedText() {
        val track = Track("1", "歌", "歌手甲 / 歌手乙 / 歌手丙", "1", "专辑", "1",
            sourceUrl = "", imgUrl = "", artistNames = listOf("歌手甲", "歌手乙", "歌手丙"))
        val text = buildPlayerArtistAlbumText(track)
        assertFalse(isPlayerArtistGroup(track))
        assertEquals("歌手甲 / 歌手乙 / 歌手丙 - 专辑", text.text)
        assertEquals("0", text.getStringAnnotations("artist", 1, 1).single().item)
        assertEquals("1", text.getStringAnnotations("artist", 7, 7).single().item)
        assertEquals("2", text.getStringAnnotations("artist", 13, 13).single().item)
        assertEquals("album", text.getStringAnnotations("album", text.length - 1, text.length - 1).single().item)
    }

    @Test fun groupLabelHasArtistLink() {
        val track = Track("1", "歌", "群星", "1", "专辑", "1", sourceUrl = "", imgUrl = "",
            artistNames = listOf("甲", "乙", "丙", "丁"))
        val text = buildPlayerArtistAlbumText(track)
        assertTrue(isPlayerArtistGroup(track))
        assertEquals("群星 - 专辑", text.text)
        assertEquals("0", text.getStringAnnotations("artist", 1, 1).single().item)
    }

    @Test fun fourNamedArtistsOpenGroupPanel() {
        val track = Track("1", "歌", "甲 / 乙 / 丙 / 丁", "1", "专辑", "1", sourceUrl = "", imgUrl = "",
            artistNames = listOf("甲", "乙", "丙", "丁"))
        assertTrue(isPlayerArtistGroup(track))
    }
}
