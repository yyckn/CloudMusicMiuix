package com.hypermusic.miuix.data.api

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.Before
import org.junit.runner.RunWith
import java.util.UUID

/** Public/guest integration checks. Never read the installed user's platform sessions. */
@RunWith(AndroidJUnit4::class)
class AdditionalRecommendationPublicTest {
    @Before fun foregroundDisposableApp() {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        org.junit.Assume.assumeTrue(target.packageName.endsWith(".recommendtest"))
        target.startActivity(android.content.Intent(target, com.hypermusic.miuix.MainActivity::class.java)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
    }
    private fun isolated(): Context {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "recommendation_public_${UUID.randomUUID()}_"
        return object : ContextWrapper(target) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) = target.getSharedPreferences(prefix + name, mode)
        }
    }

    @Test fun bodianHomeAndPersonalFm() = runBlocking {
        val repo = AdditionalRecommendations(isolated())
        repo.loadHome(MusicPlatform.BODIAN)
        val home = requireNotNull(repo.homes[MusicPlatform.BODIAN])
        assertEquals(home.error, listOf("私人FM", "潮趣日推", "新歌打赏"), home.large.map { it.title })
        assertTrue(home.sections.isNotEmpty())
        val daily = repo.collection("extra:bodian:discover:0", refresh = false)
        assertTrue(daily.tracks.isNotEmpty())
        assertTrue(repo.radio("extra:bodian:fm", first = true).all { it.source == "bodian" })
    }

    @Test fun kugouHomeAndPersonalFm() = runBlocking {
        val repo = AdditionalRecommendations(isolated())
        repo.loadHome(MusicPlatform.KUGOU)
        val home = requireNotNull(repo.homes[MusicPlatform.KUGOU])
        assertNull("正常的空推荐卡片不应在首页显示错误", home.error)
        assertEquals(home.error, listOf("每日推荐", "私人FM", "VIP专属推荐", "私人专属好歌"), home.large.map { it.title })
        assertTrue(home.sections.flatMap { it.cards }.any { it.title == "小众宝藏佳作" })
        assertTrue(repo.radio("extra:kugou:fm", first = true).all { it.source == "kugou" && it.duration > 1000 })
    }

    @Test fun qishuiSceneHomeAndContinuousRadio() = runBlocking {
        val repo = AdditionalRecommendations(isolated())
        repo.loadHome(MusicPlatform.QISHUI)
        val home = requireNotNull(repo.homes[MusicPlatform.QISHUI])
        assertEquals(home.error, listOf("私人FM", "专注模式"), home.large.map { it.title })
        assertEquals("heart", home.large.last().background)
        assertTrue((home.large + home.sections.flatMap { it.cards }).all { it.radio })
        val focus = home.large.last()
        val first = repo.radio(focus.id, first = true)
        val next = repo.radio(focus.id, first = false)
        assertTrue(first.isNotEmpty() && next.isNotEmpty())
        assertTrue((first + next).all { it.source == "qishui" })
    }
}
