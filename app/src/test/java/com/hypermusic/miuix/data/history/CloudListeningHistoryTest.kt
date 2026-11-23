package com.hypermusic.miuix.data.history

import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class CloudListeningHistoryTest {
    private val date = LocalDate.of(2026, 11, 23)
    private val zone = ZoneId.of("Asia/Shanghai")
    private val at = date.atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val song = Track("netrack_123", "测试歌曲", "测试歌手", "", "测试专辑", "", sourceUrl = "", imgUrl = "", duration = 180_000)
    private fun local(id: String, time: Long = at, ms: Long = 40_000, uploaded: Boolean = false, account: String = "A") =
        ListeningRecord(id, song, account, time, ms, song.duration, true, true, uploaded, null, mapOf(date.toString() to ms))
    private fun json(text: String) = JsonParser.parseString(text).asJsonObject

    @Test fun recentParsesTrackAndTimestampAndSkipsNonSongs() {
        val rows = CloudListeningParser.recent(json("""{"data":{"list":[
          {"resourceType":"SONG","playTime":1795392000000,"data":{"id":123,"name":"测试歌曲","dt":180000,"fee":1,"ar":[{"id":9,"name":"歌手"}],"al":{"id":8,"name":"专辑","picUrl":"http://example.test/a.jpg"}}},
          {"resourceType":"VIDEO","playTime":1795392000000,"data":{"id":456,"name":"视频"}}
        ]}}"""))
        assertEquals(1, rows.size)
        assertEquals("netrack_123", rows[0].track.id)
        assertEquals("neartist_9", rows[0].track.artistId)
        assertEquals(180_000, rows[0].track.duration)
        assertTrue(rows[0].track.isVip)
        assertTrue(rows[0].track.imgUrl.startsWith("https://"))
    }

    @Test fun cloudDuplicatesAreRemovedOnlyAtSameSongAndTimestamp() {
        val cloud = listOf(CloudListeningEvent(song, at), CloudListeningEvent(song, at), CloudListeningEvent(song, at + 1000))
        assertEquals(2, ListeningHistoryMerger.timeline(emptyList(), cloud, "A").size)
    }

    @Test fun localAndCloudOverlapUsesActualLocalDuration() {
        val rows = ListeningHistoryMerger.timeline(listOf(local("one")), listOf(CloudListeningEvent(song, at + 5000)), "A")
        assertEquals(1, rows.size)
        assertTrue(rows.single().cloud)
        assertEquals(40_000L, rows.single().listenedMs)
    }

    @Test fun oneCloudEventDoesNotCollapseTwoLocalRepeatPlays() {
        val rows = ListeningHistoryMerger.timeline(listOf(local("one"), local("two", at + 60_000)), listOf(CloudListeningEvent(song, at + 60_000)), "A")
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.cloud })
    }

    @Test fun accountsAreIsolated() {
        val rows = ListeningHistoryMerger.timeline(listOf(local("one", account = "B")), emptyList(), "A")
        assertTrue(rows.isEmpty())
    }

    @Test fun farApartPlaysRemainSeparate() {
        val rows = ListeningHistoryMerger.timeline(listOf(local("one")), listOf(CloudListeningEvent(song, at + 181_000)), "A")
        assertEquals(2, rows.size)
        assertNull(rows.first().listenedMs)
    }

    @Test fun officialSummaryWithoutDailyDetailsIsUsable() {
        val summary = CloudListeningParser.summary(json("""{"data":{"realtimeReport":{"totalDuration":123,"listenCount":4,"songCount":3}}}"""))
        assertEquals(123_000L, summary.totalMs)
        assertEquals(4, summary.playCount)
        assertEquals(3, summary.songCount)
        assertTrue(summary.days.isEmpty())
    }

    @Test fun minutesAndExplicitMillisecondsKeepTheirUnits() {
        assertEquals(120_000L, CloudListeningParser.summary(json("""{"data":{"totalMinutes":2}}""")).totalMs)
        assertEquals(120_000L, CloudListeningParser.summary(json("""{"data":{"listenTimeMs":120000}}""")).totalMs)
    }

    @Test fun songLengthIsNeverClaimedAsMeasuredListeningDuration() {
        val summary = CloudListeningParser.summary(json("""{"data":{"songs":[{"id":123,"name":"歌曲","duration":180000,"playCount":2}]}}"""))
        assertNull(summary.totalMs)
        assertNull(summary.songs.single().listenedMs)
        assertEquals(2, summary.songs.single().count)
    }

    @Test fun repeatedReportContainersTakeMaximumRatherThanSum() {
        val summary = CloudListeningParser.summary(json("""{"data":{"totalDuration":120,"report":{"totalDuration":120},"songs":[{"id":123,"name":"歌","playCount":2}],"topSongs":[{"id":123,"name":"歌","playCount":3}]}}"""))
        assertEquals(120_000L, summary.totalMs)
        assertEquals(1, summary.songs.size)
        assertEquals(3, summary.songs.single().count)
    }

    @Test fun syncedLocalTimeIsNotAddedAgainToCloudTotal() {
        val timeline = ListeningHistoryMerger.timeline(listOf(local("synced", ms = 40_000, uploaded = true), local("pending", at + 5000, 20_000)), emptyList(), "A")
        val summary = CloudListeningSummary(60_000, null, null, emptyMap(), emptyList())
        val stats = ListeningHistoryMerger.stats(timeline, listOf(summary), date, date, zone)
        assertEquals(80_000L, stats.totalMs)
    }

    @Test fun matchedPendingLocalTimeIsNotAddedAgainToCloudTotal() {
        val timeline = ListeningHistoryMerger.timeline(listOf(local("pending")), listOf(CloudListeningEvent(song, at)), "A")
        val summary = CloudListeningSummary(60_000, null, null, emptyMap(), emptyList())
        assertEquals(60_000L, ListeningHistoryMerger.stats(timeline, listOf(summary), date, date, zone).totalMs)
    }

    @Test fun estimatedCloudDaysDoNotOverrideAuthoritativeTotal() {
        val timeline = ListeningHistoryMerger.timeline(emptyList(), listOf(CloudListeningEvent(song, at)), "A")
        val summary = CloudListeningSummary(60_000, null, null, emptyMap(), emptyList())
        val stats = ListeningHistoryMerger.stats(timeline, listOf(summary), date, date, zone)
        assertEquals(60_000L, stats.totalMs)
        assertEquals(180_000L, stats.days[date])
        assertTrue(stats.estimatedDays)
    }

    @Test fun unknownSongDurationStillHighlightsListeningDateWithoutInventingTime() {
        val timeline = ListeningHistoryMerger.timeline(emptyList(), listOf(CloudListeningEvent(song.copy(duration = 0), at)), "A")
        val stats = ListeningHistoryMerger.stats(timeline, emptyList(), date, date, zone)
        assertEquals(0L, stats.totalMs)
        assertTrue(date in stats.activeDates)
        assertFalse(stats.estimatedDays)
    }

    @Test fun previousYearReportDoesNotBecomeCurrentYearReport() {
        val report = json("""{"data":{"yearReport":{"year":2025,"totalDuration":3600}}}""")
        assertFalse(CloudListeningParser.matchesPeriod(report, date.withDayOfYear(1), date.withMonth(12).withDayOfMonth(31)))
    }

    @Test fun reportDeclaredOutsideRequestedMonthIsRejected() {
        val report = json("""{"data":{"startDate":"2026-10-01","endDate":"2026-10-31","totalDuration":3600}}""")
        assertFalse(CloudListeningParser.matchesPeriod(report, date.withDayOfMonth(1), date))
    }
}
