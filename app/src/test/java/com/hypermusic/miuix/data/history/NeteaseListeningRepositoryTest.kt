package com.hypermusic.miuix.data.history

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class NeteaseListeningRepositoryTest {
    @Test fun sectionsRunConcurrentlyWithPinnedAccountAndCorrectPeriod() = runBlocking {
        val entered = mutableSetOf<String>()
        val gate = CompletableDeferred<Unit>()
        val names = listOf("recent", "report", "rank", "realtime")
        val job = async {
            NeteaseListeningRepository.fetchSections(names, "month", 1234L, "account-A") { section, type, end, account ->
                assertEquals("account-A", account)
                assertEquals("month", type)
                assertEquals(if (section in setOf("report", "rank")) 1234L else null, end)
                entered += section
                if (entered.size == names.size) gate.complete(Unit)
                gate.await()
                Result.success(JsonObject())
            }
        }
        withTimeout(3_000) { assertEquals(4, job.await().size) }
    }

    @Test fun oneEndpointFailureDoesNotDiscardOtherSections() = runBlocking {
        val results = NeteaseListeningRepository.fetchSections(listOf("recent", "report"), "week", 1234L, "A") { section, _, _, _ ->
            if (section == "report") throw java.io.IOException("测试网络异常")
            Result.success(JsonObject())
        }.toMap()
        assertTrue(results.getValue("recent").isSuccess)
        assertTrue(results.getValue("report").isFailure)
    }

    @Test fun cumulativeAllTimeTotalDoesNotBecomeAnnualDuration() {
        val root = JsonParser.parseString("""{"data":{"totalDuration":999999}}""").asJsonObject
        val snapshot = ListeningCloudSnapshot("A", "year", "2026-01-01", "2026-12-31", mapOf("total" to root), emptyList(), 1L)
        assertTrue(snapshot.summaries().isEmpty())
        assertEquals(999_999_000L, snapshot.cumulativeMs())
    }

    @Test fun snapshotRoundTripPreservesAccountRawResponsesAndStaleStatus() {
        val root = JsonParser.parseString("""{"data":{"totalMinutes":2}}""").asJsonObject
        val snapshot = ListeningCloudSnapshot("A", "week", "2026-11-22", "2026-11-28", mapOf("realtime" to root), listOf("测试失败"), 100L, true)
        val gson = Gson()
        val restored = gson.fromJson(gson.toJson(snapshot), ListeningCloudSnapshot::class.java)
        assertEquals(snapshot, restored)
        assertEquals(120_000L, restored.summaries().single().totalMs)
    }
}
