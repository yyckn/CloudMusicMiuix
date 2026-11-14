package com.hypermusic.miuix.ui.pages

import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hypermusic.miuix.data.api.AdditionalLibraryChanges
import com.hypermusic.miuix.data.api.BodianAuth
import com.hypermusic.miuix.data.api.BodianClient
import com.hypermusic.miuix.data.api.MusicPlatform
import com.hypermusic.miuix.data.api.PlatformManager
import com.hypermusic.miuix.ui.theme.CloudMusicMiuixTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdditionalPlatformPageTest {
    @get:Rule val compose = createComposeRule()

    @Test fun cachedLibraryRetainsScrollAndRefreshesAfterChange() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue("Requires a disposable test application ID", context.packageName.endsWith(".platformtest"))
        val auth = BodianAuth(context, BodianClient(context))
        assumeTrue("Requires an unlogged test account", auth.currentAccount() == null)
        PlatformManager.init(context)
        val cache = AdditionalLibraryCache().apply {
            loaded = true
            loggedIn = true
            accountRevision = auth.sessionRevision()
            libraryRevision = AdditionalLibraryChanges.revision(MusicPlatform.BODIAN)
            collections = (0 until 40).map { ExtraCollection("bodian:4:$it", "测试歌单 $it", "", 3) } +
                ExtraCollection("bodian:6:456", "测试专辑", "cover", 4, album = true)
        }
        val tab = mutableIntStateOf(1)
        var openedPlaylist = ""
        var openedAlbum = 0L
        compose.setContent {
            CloudMusicMiuixTheme {
                AdditionalPlatformPage(MusicPlatform.BODIAN, tab.intValue, {}, {}, false,
                    { id, _, _ -> openedPlaylist = id }, { id, _ -> openedAlbum = id }, cache,
                    com.hypermusic.miuix.data.api.AdditionalRecommendations(context), {})
            }
        }
        compose.waitForIdle()
        compose.onNodeWithText("测试歌单 0").performClick()
        compose.runOnIdle { assertEquals("bodian:4:0", openedPlaylist) }
        compose.runOnIdle { runBlocking { cache.listState.scrollToItem(42) } }
        compose.onNodeWithText("测试专辑").performClick()
        compose.runOnIdle { assertEquals(456L, openedAlbum) }
        compose.runOnIdle { runBlocking { cache.listState.scrollToItem(20) } }
        compose.runOnIdle { tab.intValue = 0 }
        compose.runOnIdle { tab.intValue = 1 }
        compose.waitForIdle()
        compose.runOnIdle {
            assertEquals(20, cache.listState.firstVisibleItemIndex)
            assertEquals(41, cache.collections.size)
            AdditionalLibraryChanges.changed(MusicPlatform.BODIAN)
        }
        compose.waitUntil(10_000) { !cache.loading && !cache.loggedIn }
        compose.onNodeWithText("点击登录").assertIsDisplayed()
    }
}
