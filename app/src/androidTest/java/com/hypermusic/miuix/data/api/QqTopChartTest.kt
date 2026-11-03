package com.hypermusic.miuix.data.api

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class QqTopChartTest {
    @Test fun publicChartsReturnMetadataAndPaginatedSongs() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("qqLiveRead") == "true")
        val charts = QqMusicApi.getTopCharts().getOrThrow()
        assertEquals(listOf(62, 26, 27, 5, 59, 61, 3, 16, 17), charts.map { it.id })
        assertTrue(charts.all { it.cover.startsWith("https://") && it.updateDate.matches(Regex("\\d{4}-\\d{2}-\\d{2}")) })
        val detail = QqMusicApi.getTopChartDetail(26).getOrThrow()
        assertTrue(detail.tracks.size > 100)
        assertEquals(detail.tracks.size, detail.tracks.map { it.id }.distinct().size)
        assertTrue(detail.tracks.all { it.source == "qqmusic" && it.id.isNotBlank() })
        assertTrue(detail.info.description.matches(Regex("\\d{4}-\\d{2}-\\d{2}")))
    }
}
