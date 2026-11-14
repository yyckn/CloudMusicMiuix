package com.hypermusic.miuix.ui.components

import org.junit.Assert.*
import org.junit.Test

class PageContentSearchTest {
    @Test fun songQueryMatchesTitleArtistOrAlbumCaseInsensitively() {
        val fields = arrayOf("See You Again", "Wiz Khalifa / Charlie Puth", "Furious 7")
        assertTrue(matchesPageQuery("see you", *fields))
        assertTrue(matchesPageQuery("CHARLIE", *fields))
        assertTrue(matchesPageQuery("furious", *fields))
        assertFalse(matchesPageQuery("unrelated", *fields))
    }

    @Test fun allTokensMustMatchButCanSpanDifferentMetadataFields() {
        assertTrue(matchesPageQuery("  晴天 \t 周杰伦  ", "晴天", "周杰伦", "叶惠美"))
        assertFalse(matchesPageQuery("晴天 陈奕迅", "晴天", "周杰伦", "叶惠美"))
        assertTrue(matchesPageQuery("", "晴天"))
        assertTrue(matchesPageQuery("  \t ", "晴天"))
    }

    @Test fun albumQueriesAndRegexCharactersAreLiteral() {
        assertTrue(matchesPageQuery("叶惠美 周杰伦", "叶惠美", "周杰伦"))
        assertTrue(matchesPageQuery("[Live]", "Concert [Live]", "Singer"))
        assertFalse(matchesPageQuery(".*", "任何歌曲"))
        assertFalse(matchesPageQuery("[", "没有方括号"))
    }
}
