package com.hypermusic.miuix.data.history

import com.hypermusic.miuix.ui.pages.ListeningHistorySession
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ListeningHistorySessionTest {
    private val start = LocalDate.of(2026, 11, 22)

    @Test fun navigatingBackAndSwitchingAccountsRetainsEachAccountsPageState() {
        val session = ListeningHistorySession()
        val first = session.account("A")
        first.tab = 2
        first.offsets[2] = -1
        val period = first.period("month", start, start.plusMonths(1))
        period.attempted = true
        period.error = "原有提示"
        session.account("B").tab = 1
        val restored = session.account("A")
        assertSame(first, restored)
        assertEquals(2, restored.tab)
        assertEquals(-1, restored.offsets[2])
        assertSame(period, restored.period("month", start, start.plusMonths(1)))
        assertTrue(restored.period("month", start, start.plusMonths(1)).attempted)
        assertSame(first.listStates[0], restored.listStates[0])
        assertEquals(1, session.account("B").tab)
    }

    @Test fun eachPeriodKeepsItsOwnReportWhenSwitchingTabs() {
        val account = ListeningHistorySession().account("A")
        val week = account.period("week", start, start.plusDays(6))
        week.attempted = true
        val month = account.period("month", start.withDayOfMonth(1), start.withDayOfMonth(30))
        assertFalse(month.attempted)
        assertTrue(account.period("week", start, start.plusDays(6)).attempted)
        assertNotSame(week, month)
    }

    @Test fun oldPeriodWorkingSetsAreBoundedWhileRecentPeriodIsKept() {
        val account = ListeningHistorySession().account("A")
        val first = account.period("week", start, start.plusDays(6))
        val newest = (1..12).map { offset ->
            val date = start.minusWeeks(offset.toLong())
            account.period("week", date, date.plusDays(6))
        }.last()
        val lastDate = start.minusWeeks(12)
        assertSame(newest, account.period("week", lastDate, lastDate.plusDays(6)))
        assertNotSame(first, account.period("week", start, start.plusDays(6)))
    }
}
