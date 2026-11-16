package com.hypermusic.miuix.data.model

import org.junit.Assert.*
import org.junit.Test

class VersionHistoryTest {
    @Test fun currentLogIsLatestHistoricalEntryWithCorrectedDates() {
        assertSame(VersionHistory.entries.first(), VersionHistory.current)
        assertEquals("2.1.0更新", VersionHistory.current.version)
        assertEquals("2026-11-15", VersionHistory.current.date)
        assertEquals("2026-11-06", VersionHistory.entries.first { it.version == "2.0.0更新" }.date)
        assertTrue(VersionHistory.entries.all { it.date.startsWith("2026-") })
    }
    @Test fun groupedLogsPreserveEveryBulletInOrder() {
        val info = VersionHistory.current
        val sections = info.sections()
        assertEquals(listOf("重要功能更新", "常规功能更新", "优化修复"), sections.map { it.title })
        assertEquals(listOf(3, 7, 9), sections.map { it.lines.size })
        assertEquals(info.changelog.filterNot { it.startsWith("# ") }, sections.flatMap { it.lines })
    }
    @Test fun oldUngroupedVersionsKeepTheirOriginalText() {
        val old = UpdateInfo(changelog = listOf("- 新增：功能。", "- 修复：问题。"))
        assertEquals(listOf(ChangelogSection(null, old.changelog)), old.sections())
        assertTrue(UpdateInfo().sections().isEmpty())
    }
}
