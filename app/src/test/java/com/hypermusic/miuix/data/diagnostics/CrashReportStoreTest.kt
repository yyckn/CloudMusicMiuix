package com.hypermusic.miuix.data.diagnostics

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReportStoreTest {
    @get:Rule val folder = TemporaryFolder()
    @Test fun reportsSurviveReopeningAndRetainOnlyTenWithTheirAttachments() {
        val directory = folder.newFolder()
        val store = CrashReportStore(directory)
        repeat(12) { store.save("${1000 + it}-test", "report $it", byteArrayOf(it.toByte())) }
        val reopened = CrashReportStore(directory)
        assertEquals(10, reopened.reports().size)
        assertEquals("1011-test.log", reopened.reports().first().name)
        assertFalse(directory.resolve("1000-test.trace").exists())
        assertEquals("report 11", reopened.read(reopened.reports().first()))
        assertEquals(2, reopened.attachments(reopened.reports().first()).size)
        reopened.clear()
        assertTrue(reopened.reports().isEmpty())
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
    @Test fun logsAreBoundedAndCredentialsAreRedacted() {
        val store = CrashReportStore(folder.newFolder())
        store.save("1000-test", "MUSIC_U=secret musickey: qq-secret access_token=token\n" + "a".repeat(CrashReportStore.MAX_REPORT_CHARS + 100))
        val text = store.read(store.reports().single())
        assertFalse(text.contains("secret"))
        assertFalse(text.contains("access_token=token"))
        assertTrue(text.contains("[已隐藏]"))
        assertTrue(text.length <= CrashReportStore.MAX_REPORT_CHARS)
    }
    @Test fun stackTraceRetainsCausesAndSuppressedExceptions() {
        val error = IllegalStateException("outer", IllegalArgumentException("inner"))
        error.addSuppressed(UnsupportedOperationException("suppressed"))
        val trace = diagnosticStackTrace(error)
        assertTrue(trace.contains("IllegalStateException: outer"))
        assertTrue(trace.contains("Caused by: java.lang.IllegalArgumentException: inner"))
        assertTrue(trace.contains("Suppressed: java.lang.UnsupportedOperationException: suppressed"))
        assertTrue(trace.length <= CrashReportStore.MAX_REPORT_CHARS)
    }
}
