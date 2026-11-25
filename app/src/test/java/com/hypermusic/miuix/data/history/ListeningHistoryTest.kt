package com.hypermusic.miuix.data.history

import com.google.gson.JsonParser
import com.hypermusic.miuix.data.model.Track
import org.junit.Assert.*
import org.junit.Test
import java.time.*

class ListeningHistoryTest {
    @Test fun playbackTimeUsesDeviceTimeZoneAndPeriodBoundaries() {
        val zone = ZoneId.of("Asia/Shanghai")
        val date = LocalDate.of(2026, 11, 24)
        listOf("00:00" to "凌晨", "05:59" to "凌晨", "06:00" to "早上", "08:59" to "早上",
            "09:00" to "上午", "11:59" to "上午", "12:00" to "中午", "13:59" to "中午",
            "14:00" to "下午", "17:59" to "下午", "18:00" to "晚上", "23:59" to "晚上").forEach { (clock, period) ->
            val timestamp = date.atTime(LocalTime.parse(clock)).atZone(zone).toInstant().toEpochMilli()
            assertEquals("$period$clock", ListeningStats.playbackTime(timestamp, zone))
        }
        val timestamp = Instant.parse("2026-11-24T04:34:00Z").toEpochMilli()
        assertEquals("中午12:34", ListeningStats.playbackTime(timestamp, zone))
        assertEquals("凌晨04:34", ListeningStats.playbackTime(timestamp, ZoneOffset.UTC))
    }

    @Test fun chartLabelsPreferFullUnitsThenRoundHoursThenShortenUnit() {
        val time = 75 * 60_000L
        assertEquals("1.3小时", ListeningStats.chartDuration(time) { true })
        assertEquals("1小时", ListeningStats.chartDuration(time) { it == "1小时" || it == "1时" })
        assertEquals("1时", ListeningStats.chartDuration(time) { it == "1时" })
        assertEquals("1时", ListeningStats.chartDuration(time) { false })
        assertEquals("3小时", ListeningStats.chartDuration(150 * 60_000L) { it == "3小时" })
        assertEquals("59分钟", ListeningStats.chartDuration(59 * 60_000L) { true })
        assertEquals("59分", ListeningStats.chartDuration(59 * 60_000L) { it == "59分" })
        assertEquals("", ListeningStats.chartDuration(0) { true })
    }

    @Test fun listeningDurationsUseHoursWithOneDecimalAtOneHourAndAbove() {
        assertEquals("59分钟", ListeningStats.duration(59 * 60_000L))
        assertEquals("1.0小时", ListeningStats.duration(60 * 60_000L))
        assertEquals("1.3小时", ListeningStats.duration(75 * 60_000L))
        assertEquals("1.5小时", ListeningStats.duration(90 * 60_000L))
        assertEquals("24.0小时", ListeningStats.duration(24 * 3_600_000L))
    }

    @Test fun dailyRowsKeepFractionalMinutesButShareTheHourConversion() {
        assertEquals("0.5分钟", ListeningStats.duration(30_000L, fractionalMinutes = true))
        assertEquals("1.5分钟", ListeningStats.duration(90_000L, fractionalMinutes = true))
        assertEquals("1.5小时", ListeningStats.duration(90 * 60_000L, fractionalMinutes = true))
        assertEquals("30秒", ListeningStats.duration(30_000L))
        assertEquals("0秒", ListeningStats.duration(-1))
    }

    @Test fun thresholdsAreStrict() {
        val session = ListeningSession()
        session.update(0, true)
        session.update(10_000, true)
        assertFalse(session.visible())
        session.update(10_001, true)
        assertTrue(session.visible())
        session.update(30_000, true)
        assertFalse(session.reportable(180_000, false))
        session.update(30_001, true)
        assertTrue(session.reportable(180_000, false))
    }

    @Test fun pausedAndBufferingIntervalsDoNotCount() {
        val session = ListeningSession()
        session.update(0, false)
        session.update(50_000, true)
        session.update(55_000, false)
        session.update(100_000, true)
        session.update(106_000, false)
        assertEquals(11_000L, session.listenedMs)
    }

    @Test fun shortSongMustReachEndAndBeFullyListenedTo() {
        val session = ListeningSession()
        session.update(0, true)
        session.update(12_000, false)
        assertFalse(session.reportable(20_000, false))
        assertFalse(session.reportable(20_000, true)) // seeking straight to end is insufficient
        session.update(13_000, true)
        session.update(21_000, false)
        assertTrue(session.reportable(20_000, true))
    }

    @Test fun shortSongAtDoubleSpeedStillCompletes() {
        val session = ListeningSession()
        session.update(0, true, 2f)
        session.update(10_000, false)
        assertEquals(10_000, session.listenedMs)
        assertTrue(session.reportable(20_000, true))
    }

    @Test fun clockRollbackCannotSubtractTime() {
        val session = ListeningSession()
        session.update(100, true)
        session.update(0, false)
        assertEquals(0, session.listenedMs)
    }

    private fun record(start: Instant, time: Long, days: Map<String, Long> = emptyMap()) = ListeningRecord(
        "test", Track("netrack_1", "song", "artist", "", "album", "", sourceUrl = "", imgUrl = ""),
        "account", start.toEpochMilli(), time, 180_000, false, false, false, null, days)

    @Test fun midnightIntervalsAreSplit() {
        val zone = ZoneId.of("Asia/Shanghai")
        val start = LocalDateTime.of(2026, 11, 21, 23, 59, 50).atZone(zone).toInstant()
        val days = ListeningStats.days(listOf(record(start, 30_000)), zone)
        assertEquals(10_000L, days[LocalDate.of(2026, 11, 21)])
        assertEquals(20_000L, days[LocalDate.of(2026, 11, 22)])
    }

    @Test fun durableActualDayTotalsOverrideElapsedWallTimeIncludingPauses() {
        val start = Instant.parse("2026-11-20T01:00:00Z")
        val days = ListeningStats.days(listOf(record(start, 30_000, mapOf("2026-11-20" to 10_000L, "2026-11-22" to 20_000L))))
        assertEquals(2, days.size)
        assertEquals(20_000L, days[LocalDate.of(2026, 11, 22)])
    }

    @Test fun relativeDateLabelsWorkAcrossYearBoundary() {
        val today = LocalDate.of(2027, 1, 1)
        assertEquals("今天", ListeningStats.dateLabel(today, today))
        assertEquals("昨天", ListeningStats.dateLabel(today.minusDays(1), today))
        assertEquals("前天", ListeningStats.dateLabel(today.minusDays(2), today))
        assertEquals("2026-12-29", ListeningStats.dateLabel(today.minusDays(3), today))
    }

    @Test fun reportIgnoresSongDurationsAndUndatedStatistics() {
        val json = JsonParser.parseString("""{"data":{"songs":[{"duration":300000}],"listenTime":999}}""").asJsonObject
        assertNull(ListeningReport.parse(json))
    }

    @Test fun periodStartAndTotalAreNotMistakenForOneListeningDay() {
        val json = JsonParser.parseString("""{"data":{"startTime":1795276800000,"listenTime":86400,"days":[{"date":"2026-11-21","listenTime":120}]}}""").asJsonObject
        assertEquals(120_000L, requireNotNull(ListeningReport.parse(json)).totalMs)
    }

    @Test fun datedReportParsesSecondsAndMillisecondsWithoutDoubleCountingNestedSummary() {
        val json = JsonParser.parseString("""{"data":{"days":[{"date":"2026-11-21","listenTime":120},{"date":"20261122","listenTimeMs":60000}]}}""").asJsonObject
        val report = requireNotNull(ListeningReport.parse(json))
        assertEquals(180_000L, report.totalMs)
        assertEquals(2, report.days.size)
    }
}
