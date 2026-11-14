package com.hypermusic.miuix.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class QqPlaylistTitleTest {
    @Test fun genericDetailKeepsOtherOwnersName() {
        assertEquals("小明 收藏的音乐", displayQqPlaylistDetailTitle("我喜欢", "小明 收藏的音乐", "自己"))
        assertEquals("自己 收藏的音乐", displayQqPlaylistDetailTitle("我喜欢", "我喜欢", "自己"))
        assertEquals("小明 收藏的音乐", displayQqPlaylistDetailTitle("小明的我喜欢", "", "自己"))
        assertEquals("普通歌单", displayQqPlaylistDetailTitle("普通歌单", "旧名称", "自己"))
    }
}
