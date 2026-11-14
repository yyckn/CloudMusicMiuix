package com.mocharealm.accompanist.lyrics.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.hypermusic.miuix.data.lyrics.AppleMusicLyricsConverter
import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.SongLyrics
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import com.mocharealm.accompanist.lyrics.ui.preparation.PreparedLyrics
import com.mocharealm.accompanist.lyrics.ui.preparation.prepareLyrics
import com.mocharealm.accompanist.lyrics.ui.profile.DefaultLyricsProfiles
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackingVocalLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun nestedVocalsUseRequestedTypographyAndLeaveNoHeightAfterExit() {
        val position = mutableIntStateOf(500)
        val state = LyricsLazyListState()
        val lyrics = AppleMusicLyricsConverter.convert(SongLyrics(listOf(
            LyricLine("Main", startAt = 1000, endAt = 3000),
            LyricLine("(Yeah)", "衬词翻译", 2500, 4000, isBackingVocal = true),
            LyricLine("(Again)", startAt = 4500, endAt = 5500, isBackingVocal = true),
            LyricLine("Next", startAt = 6000, endAt = 8000)
        )))
        val normalStyle = TextStyle(fontSize = 40.sp)
        val backingStyle = TextStyle(fontSize = 28.sp)
        val captionStyle = TextStyle(fontSize = 24.sp)
        var metrics: PreparedLyrics? = null
        compose.setContent {
            val density = LocalDensity.current
            val measurer = rememberTextMeasurer()
            val prepared = androidx.compose.runtime.remember(measurer, density) {
                prepareLyrics(lyrics, DefaultLyricsProfiles, measurer, normalStyle, backingStyle,
                    captionStyle, 400f, density.density, true, density.fontScale)
            }
            SideEffect { metrics = prepared }
            KaraokeLyricsView(state, lyrics, { position.intValue }, {}, {}, Modifier.fillMaxSize(),
                normalLineTextStyle = normalStyle, accompanimentLineTextStyle = backingStyle,
                phoneticTextStyle = captionStyle, useBlurEffect = false)
        }
        compose.waitUntil(15_000) { state.heights.size >= 2 && metrics != null }
        compose.runOnIdle {
            val child = metrics!!.lines.first()!!.after.first()
            assertEquals(28.sp, child.rows.first().runs.first().groups.first().units.first().text.layout.layoutInput.style.fontSize)
            assertEquals(20.sp, child.translation!!.layoutInput.style.fontSize)
        }
        compose.mainClock.autoAdvance = false
        fun advance(time: Int, duration: Long = 1000) {
            compose.runOnIdle { position.intValue = time }
            compose.mainClock.advanceTimeBy(duration)
            compose.waitForIdle()
        }
        val hidden = compose.runOnIdle { state.heights.height(0) }
        advance(1000)
        val visible = compose.runOnIdle { state.heights.height(0) }
        assertTrue(visible > hidden)
        advance(4100)
        assertEquals(visible, compose.runOnIdle { state.heights.height(0) })
        advance(5600, 3000)
        assertEquals(hidden, compose.runOnIdle { state.heights.height(0) })
        advance(2000)
        assertEquals(visible, compose.runOnIdle { state.heights.height(0) })
    }
}
