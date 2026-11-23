// SPDX-License-Identifier: AGPL-3.0-or-later
// Endpoint selection and summary field compatibility reference ColdP/AuraPlayer (AGPL-3.0).
package com.hypermusic.miuix.data.history

import androidx.annotation.Keep
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.hypermusic.miuix.data.model.Track
import java.time.*
import kotlin.math.abs

@Keep data class CloudListeningEvent(val track: Track, val playedAt: Long)
data class ListeningTimelineItem(val key: String, val track: Track, val playedAt: Long,
    val listenedMs: Long?, val local: ListeningRecord? = null, val cloud: Boolean = false)
data class CloudSongStat(val track: Track, val count: Int, val listenedMs: Long?)
data class CloudListeningSummary(val totalMs: Long?, val playCount: Int?, val songCount: Int?,
    val days: Map<LocalDate, Long>, val songs: List<CloudSongStat>)

object CloudListeningParser {
    private fun JsonObject.text(key: String): String = runCatching { get(key)?.takeUnless { it.isJsonNull }?.asString }.getOrNull().orEmpty()
    private fun JsonObject.number(key: String): Long? = runCatching { get(key)?.takeUnless { it.isJsonNull }?.asLong }.getOrNull()
    private fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject
    private fun JsonObject.array(key: String) = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
    private fun JsonObject.objects(key: String) = array(key)?.filter { it.isJsonObject }?.map { it.asJsonObject }.orEmpty()

    fun matchesPeriod(root: JsonObject, start: LocalDate, end: LocalDate): Boolean {
        val data = root.obj("data") ?: root
        val containers = listOf(data) + listOf("report", "realtimeReport", "listenData", "summary", "yearReport").mapNotNull { data.obj(it) }
        fun date(value: JsonElement?): LocalDate? = runCatching {
            val text = value?.asString ?: return@runCatching null
            if (text.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))) LocalDate.parse(text.take(10))
            else text.toLongOrNull()?.takeIf { it >= 1_000_000_000 }?.let {
                Instant.ofEpochMilli(if (it < 10_000_000_000) it * 1000 else it).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }.getOrNull()
        containers.forEach { obj ->
            obj.number("year")?.let { if (it.toInt() !in start.year..end.year) return false }
            val first = date(obj.get("startDate")) ?: date(obj.get("startTime"))
            val last = date(obj.get("endDate")) ?: date(obj.get("endTime"))
            if (first != null && first > end) return false
            if (last != null && last < start) return false
        }
        return true
    }

    fun track(song: JsonObject): Track? {
        val id = song.number("id") ?: song.number("songId") ?: return null
        val title = song.text("name").ifBlank { song.text("songName") }
        if (id <= 0 || title.isBlank()) return null
        val artists = song.objects("ar").ifEmpty { song.objects("artists") }
        val album = song.obj("al") ?: song.obj("album")
        val names = artists.map { it.text("name") }.filter { it.isNotBlank() }
        val ids = artists.map { "neartist_${it.number("id") ?: 0}" }
        return Track("netrack_$id", title,
            names.joinToString(" / ").ifBlank { song.text("artistName").ifBlank { song.text("singerName") } },
            ids.firstOrNull() ?: "neartist_${song.number("artistId") ?: 0}",
            album?.text("name").orEmpty().ifBlank { song.text("albumName") },
            "nealbum_${album?.number("id") ?: song.number("albumId") ?: 0}",
            sourceUrl = "https://music.163.com/#/song?id=$id",
            imgUrl = album?.text("picUrl").orEmpty().ifBlank { song.text("picUrl").ifBlank { song.text("coverUrl") } }.replaceFirst("http://", "https://"),
            duration = song.number("dt") ?: song.number("duration") ?: 0,
            isVip = song.number("fee") in setOf(1L, 4L), artistNames = names, artistIds = ids)
    }

    fun recent(root: JsonObject): List<CloudListeningEvent> {
        val data = root.obj("data") ?: root
        return data.objects("list").mapNotNull { row ->
            if (row.text("resourceType").let { it.isNotBlank() && !it.equals("SONG", true) }) return@mapNotNull null
            val song = track(row.obj("data") ?: row.obj("song") ?: row) ?: return@mapNotNull null
            val raw = row.number("playTime") ?: return@mapNotNull null
            val timestamp = if (raw in 1_000_000_000L..9_999_999_999L) raw * 1000 else raw
            if (timestamp <= 0) null else CloudListeningEvent(song, timestamp)
        }.distinctBy { it.track.id to it.playedAt }.sortedByDescending { it.playedAt }
    }

    private fun duration(obj: JsonObject): Long? {
        listOf("totalDurationMs", "listenTimeMs", "listeningTimeMs", "playTimeMs").forEach { key -> obj.number(key)?.takeIf { it >= 0 }?.let { return it } }
        listOf("totalDuration", "listenDuration", "duration", "playDuration", "listenTime", "totalTime", "dayDuration").forEach { key ->
            obj.number(key)?.takeIf { it >= 0 }?.let { return it.coerceAtMost(Long.MAX_VALUE / 1000) * 1000 }
        }
        listOf("totalMinutes", "listenMinutes", "minutes").forEach { key -> obj.number(key)?.takeIf { it >= 0 }?.let { return it.coerceAtMost(Long.MAX_VALUE / 60_000) * 60_000 } }
        return null
    }

    /** Summary duration is read only from report containers, never from song metadata. */
    fun summary(root: JsonObject): CloudListeningSummary {
        val data = root.obj("data") ?: root
        val containers = listOf(data) + listOf("report", "realtimeReport", "listenData", "summary", "total", "yearReport").mapNotNull { data.obj(it) }
        val songs = mutableListOf<CloudSongStat>()
        fun songStat(item: JsonObject): CloudSongStat? {
            val nested = listOf("song", "track", "songInfo", "data").firstNotNullOfOrNull { item.obj(it) }
            val song = track(nested ?: item) ?: return null
            val count = listOf("playCount", "count", "listenCount").firstNotNullOfOrNull { item.number(it) }?.coerceIn(1, Int.MAX_VALUE.toLong())?.toInt() ?: 1
            // A bare song's duration describes its length, not how long it was listened to.
            val listened = if (nested != null) duration(item) else listOf("listenDuration", "playDuration", "listenTime").firstNotNullOfOrNull { item.number(it) }?.coerceAtLeast(0)?.times(1000)
            return CloudSongStat(song, count, listened)
        }
        containers.forEach { obj ->
            listOf("songPlayRankList", "topSongs", "songs", "rankList", "list", "todaySongs", "playRankList", "mostPlayedSongs").forEach { key ->
                obj.objects(key).mapNotNullTo(songs, ::songStat)
            }
            listOf("mostPlayedSong", "topSong", "favoriteSong", "song").forEach { key -> obj.obj(key)?.let { songStat(it)?.let(songs::add) } }
        }
        fun maxNumber(keys: List<String>): Int? = containers.flatMap { obj -> keys.mapNotNull { obj.number(it) } }.maxOrNull()?.coerceIn(0, Int.MAX_VALUE.toLong())?.toInt()
        return CloudListeningSummary(containers.mapNotNull(::duration).maxOrNull(),
            maxNumber(listOf("listenCount", "playCount", "totalPlayCount", "songPlayCount", "totalListenCount")),
            maxNumber(listOf("songCount", "uniqueSongCount", "totalSongCount", "listenSongCount", "trackCount")),
            ListeningReport.parse(root)?.days.orEmpty(),
            songs.groupBy { it.track.id }.values.map { duplicates ->
                val best = duplicates.maxBy { it.count }
                best.copy(listenedMs = duplicates.mapNotNull { it.listenedMs }.maxOrNull())
            })
    }
}

object ListeningHistoryMerger {
    /** One-to-one matching keeps genuine repeat plays; cloud events are never uploaded again. */
    fun timeline(local: List<ListeningRecord>, cloud: List<CloudListeningEvent>, accountId: String): List<ListeningTimelineItem> {
        val localRows = local.filter { it.accountId == accountId }.sortedByDescending { it.startedAt }
        val localBySong = localRows.groupBy { it.track.id }
        val used = mutableSetOf<String>()
        val matched = mutableSetOf<String>()
        val cloudOnly = mutableListOf<ListeningTimelineItem>()
        cloud.distinctBy { it.track.id to it.playedAt }.sortedByDescending { it.playedAt }.forEach { event ->
            val duplicate = localBySong[event.track.id].orEmpty().asSequence().filter { it.id !in used && abs(it.startedAt - event.playedAt) <= 180_000 }
                .minByOrNull { abs(it.startedAt - event.playedAt) }
            if (duplicate != null) { used += duplicate.id; matched += duplicate.id }
            else cloudOnly += ListeningTimelineItem("cloud:${event.track.id}:${event.playedAt}", event.track, event.playedAt, null, cloud = true)
        }
        return (localRows.map { ListeningTimelineItem("local:${it.id}", it.track, it.startedAt, it.listenedMs, it, it.id in matched) } + cloudOnly).sortedByDescending { it.playedAt }
    }

    data class Stats(val days: Map<LocalDate, Long>, val totalMs: Long, val estimatedDays: Boolean,
        val songCount: Int, val playCount: Int, val activeDates: Set<LocalDate>, val totalEstimated: Boolean)

    fun stats(timeline: List<ListeningTimelineItem>, summaries: List<CloudListeningSummary>, start: LocalDate, end: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Stats {
        val rows = timeline.filter { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() in start..end }
        val local = timeline.mapNotNull { it.local }
        val reflected = timeline.filter { it.cloud }.mapNotNull { it.local?.id }.toHashSet()
        val localDays = ListeningStats.days(local, zone).filterKeys { it in start..end }
        val synced = ListeningStats.days(local.filter { it.uploaded || it.id in reflected }, zone).filterKeys { it in start..end }
        val pending = ListeningStats.days(local.filter { !it.uploaded && it.id !in reflected }, zone).filterKeys { it in start..end }
        val officialDays = mutableMapOf<LocalDate, Long>()
        summaries.forEach { summary -> summary.days.filterKeys { it in start..end }.forEach { (date, value) -> officialDays[date] = maxOf(officialDays[date] ?: 0L, value) } }
        val estimated = mutableMapOf<LocalDate, Long>()
        rows.filter { it.local == null }.forEach { row ->
            if (row.track.duration > 0) {
                val date = Instant.ofEpochMilli(row.playedAt).atZone(zone).toLocalDate()
                estimated[date] = (estimated[date] ?: 0) + row.track.duration
            }
        }
        val days = (localDays.keys + officialDays.keys + estimated.keys).associateWith { date ->
            if (date in officialDays) maxOf(officialDays.getValue(date), synced[date] ?: 0) + (pending[date] ?: 0)
            else (localDays[date] ?: 0) + (estimated[date] ?: 0)
        }.mapValues { it.value.coerceAtMost(86_400_000) }
        val officialTotal = (summaries.mapNotNull { it.totalMs } + if (officialDays.isNotEmpty()) listOf(officialDays.values.sum()) else emptyList()).maxOrNull()
        // Cloud recent is incomplete, so its estimated durations cannot override an official total.
        val total = if (officialTotal != null) maxOf(officialTotal, synced.values.sum()) + pending.values.sum() else days.values.sum()
        val knownDates = rows.map { Instant.ofEpochMilli(it.playedAt).atZone(zone).toLocalDate() }.toSet() + days.filterValues { it > 0 }.keys
        return Stats(days, total, estimated.keys.any { it !in officialDays },
            maxOf(rows.map { it.track.id }.distinct().size, summaries.flatMap { it.songs }.map { it.track.id }.distinct().size,
                summaries.mapNotNull { it.songCount }.maxOrNull() ?: 0),
            maxOf(rows.size, summaries.mapNotNull { it.playCount }.maxOrNull() ?: 0), knownDates, officialTotal == null && estimated.keys.any { it !in officialDays })
    }
}
