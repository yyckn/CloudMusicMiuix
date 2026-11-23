// SPDX-License-Identifier: AGPL-3.0-or-later
// References ColdP/AuraPlayer's parallel official snapshot and local/cloud merge design.
package com.hypermusic.miuix.data.history

import android.content.Context
import androidx.annotation.Keep
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.cache.PageCache
import kotlinx.coroutines.*
import java.time.*

@Keep data class ListeningCloudSnapshot(val accountId: String, val type: String, val startDate: String,
    val endDate: String, val sections: Map<String, JsonObject>, val errors: List<String>, val fetchedAt: Long,
    val stale: Boolean = false) {
    fun summaries(): List<CloudListeningSummary> = sections.filterKeys { it != "recent" && it != "total" }.values
        .filter { CloudListeningParser.matchesPeriod(it, LocalDate.parse(startDate), LocalDate.parse(endDate)) }
        .map(CloudListeningParser::summary)
    fun cumulativeMs(): Long? = sections["total"]?.let(CloudListeningParser::summary)?.totalMs
    val connected get() = sections.isNotEmpty()
}

object NeteaseListeningRepository {
    private fun key(account: String, type: String, start: LocalDate, end: LocalDate) = "listening-v1|$account|$type|$start|$end"

    suspend fun cached(context: Context, account: String, type: String, start: LocalDate, end: LocalDate): ListeningCloudSnapshot? =
        PageCache.read<ListeningCloudSnapshot>(context, key(account, type, start, end))?.takeIf {
            it.accountId == account && it.type == type && it.startDate == start.toString() && it.endDate == end.toString()
        }

    internal suspend fun fetchSections(names: List<String>, type: String, endTime: Long, account: String,
        fetch: suspend (String, String, Long?, String) -> Result<JsonObject>): List<Pair<String, Result<JsonObject>>> = coroutineScope {
        names.map { name -> async {
            val result = try { fetch(name, type, if (name in setOf("report", "rank")) endTime else null, account) }
            catch (error: CancellationException) { throw error }
            catch (error: Exception) { Result.failure(error) }
            name to result
        } }.awaitAll()
    }

    suspend fun refresh(context: Context, account: String, type: String, start: LocalDate, end: LocalDate,
        current: Boolean, fetch: suspend (String, String, Long?, String) -> Result<JsonObject> = NeteaseApi::listeningCloudSection): ListeningCloudSnapshot = coroutineScope {
        val names = buildList {
            add("recent")
            if (type == "day" && current) add("today")
            if (type != "day") { add("report"); if (type != "year") add("rank") }
            if (current && type in setOf("week", "month")) add("realtime")
            if (type == "year") { add("total"); if (current) add("year") }
        }
        val endTime = end.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val results = fetchSections(names, type, endTime, account, fetch)
        val sections = results.mapNotNull { (name, result) -> result.getOrNull()?.let { name to it } }.toMap()
        val errors = results.mapNotNull { (_, result) -> result.exceptionOrNull()?.message }.distinct()
        sections["recent"]?.let { ListeningHistory.saveCloud(account, CloudListeningParser.recent(it)) }
        val previous = cached(context, account, type, start, end)
        val retained = previous?.sections.orEmpty().filterKeys { it !in sections && it in names }
        val snapshot = ListeningCloudSnapshot(account, type, start.toString(), end.toString(), retained + sections, errors, System.currentTimeMillis(), retained.isNotEmpty())
        // An offline refresh must not overwrite a previously successful cloud snapshot.
        if (sections.isNotEmpty()) PageCache.write(context, key(account, type, start, end), snapshot)
        snapshot
    }
}
