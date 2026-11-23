package com.hypermusic.miuix.data.history

import java.time.*
import kotlin.math.roundToLong

object ListeningStats {
    /** Attribute each real listening interval to its local calendar date, including midnight. */
    fun days(records: List<ListeningRecord>, zone: ZoneId = ZoneId.systemDefault()): Map<LocalDate, Long> {
        val result = mutableMapOf<LocalDate, Long>()
        records.forEach { record ->
            if (record.dayTotals.isNotEmpty()) {
                record.dayTotals.forEach { (date, time) ->
                    runCatching { LocalDate.parse(date) }.getOrNull()?.let { result[it] = (result[it] ?: 0) + time }
                }
                return@forEach
            }
            var at = Instant.ofEpochMilli(record.startedAt)
            var remaining = record.listenedMs
            while (remaining > 0) {
                val date = at.atZone(zone).toLocalDate()
                val untilMidnight = Duration.between(at, date.plusDays(1).atStartOfDay(zone).toInstant()).toMillis()
                val part = minOf(remaining, untilMidnight)
                result[date] = (result[date] ?: 0) + part
                at = at.plusMillis(part); remaining -= part
            }
        }
        return result
    }

    fun dateLabel(date: LocalDate, today: LocalDate = LocalDate.now()): String = when (date) {
        today -> "今天"
        today.minusDays(1) -> "昨天"
        today.minusDays(2) -> "前天"
        else -> date.toString()
    }

    fun duration(ms: Long, fractionalMinutes: Boolean = false): String {
        val safeMs = ms.coerceAtLeast(0L)
        return when {
            safeMs >= 3_600_000L -> String.format(java.util.Locale.ROOT, "%.1f小时", safeMs / 3_600_000.0)
            fractionalMinutes && safeMs % 60_000L != 0L -> String.format(java.util.Locale.ROOT, "%.1f分钟", safeMs / 60_000.0)
            safeMs >= 60_000L || fractionalMinutes -> "${safeMs / 60_000L}分钟"
            else -> "${safeMs / 1000L}秒"
        }
    }

    /** Keep the fullest label that fits at the chart's normal text size. */
    fun chartDuration(ms: Long, fits: (String) -> Boolean): String {
        if (ms <= 0) return ""
        val candidates = if (ms >= 3_600_000L) {
            val hours = (ms / 3_600_000.0).roundToLong()
            listOf(duration(ms), "${hours}小时", "${hours}时")
        } else {
            val minutes = if (ms < 60_000L) String.format(java.util.Locale.ROOT, "%.1f", ms / 60_000.0) else (ms / 60_000L).toString()
            listOf("${minutes}分钟", "${minutes}分")
        }
        return candidates.firstOrNull(fits) ?: candidates.last()
    }
}
