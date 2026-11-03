package com.mocharealm.accompanist.lyrics.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import com.mocharealm.accompanist.lyrics.core.model.SyncedLyrics
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.ui.composable.list.LyricsLazyListState
import com.mocharealm.accompanist.lyrics.ui.composable.lyrics.KaraokeLyricsView
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlin.math.abs

class InterludeLayoutTest {
    @get:Rule val compose = createComposeRule()
    @Test fun followingLineSettlesBesideCurrentLineAfterInterlude() {
        val position = mutableIntStateOf(0)
        val state = LyricsLazyListState()
        val lyrics = SyncedLyrics(listOf(
            SyncedLine(translation = null, content = "Line A", start = 0, end = 1000),
            SyncedLine(translation = null, content = "Line B", start = 9000, end = 11000),
            SyncedLine(translation = null, content = "Line C", start = 12000, end = 14000),
            SyncedLine(translation = null, content = "Line D", start = 15000, end = 17000),
        ))
        compose.setContent { KaraokeLyricsView(listState = state, lyrics = lyrics,
            currentPosition = { position.intValue }, onLineClicked = {}, onLinePressed = {},
            modifier = Modifier.fillMaxSize(), useBlurEffect = false) }
        compose.waitUntil(timeoutMillis = 15_000) { state.heights.size >= 4 }
        compose.mainClock.autoAdvance = false
        fun advance(value: Int, frames: Long = 3000) {
            compose.runOnIdle { position.intValue = value }
            compose.mainClock.advanceTimeBy(frames)
            compose.waitForIdle()
        }
        advance(500)
        advance(2000)
        advance(4000)
        val duringHeight = compose.runOnIdle { state.heights.height(1) }
        advance(8800)
        // Reproduce an idle spring immediately before the interlude collapses.
        compose.runOnIdle { state.chain.reset(state.position) }
        advance(8950, 100)
        advance(9100, 6000)
        compose.runOnIdle {
            assertTrue("B height should shrink after dots disappear", state.heights.height(1) < duringHeight)
            assertTrue("B and C must not retain different spring offsets", abs(state.chain.offset(2) - state.chain.offset(1)) < 1f)
            assertTrue("C and D must not retain different spring offsets", abs(state.chain.offset(3) - state.chain.offset(2)) < 1f)
        }
    }
}

