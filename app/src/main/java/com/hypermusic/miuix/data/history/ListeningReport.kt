package com.hypermusic.miuix.data.history

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.time.*

/** Only consume explicitly dated listening data, never unrelated song durations or rankings. */
data class ListeningReport(val days: Map<LocalDate, Long>, val totalMs: Long) {
    companion object {
        fun parse(root: JsonObject): ListeningReport? {
            val days = linkedMapOf<LocalDate, Long>()
            fun date(value: JsonElement?): LocalDate? = runCatching {
                val text = value?.asString ?: return@runCatching null
                if (text.matches(Regex("\\d{4}-\\d{2}-\\d{2}.*"))) LocalDate.parse(text.take(10))
                else if (text.matches(Regex("\\d{8}"))) LocalDate.parse(text, java.time.format.DateTimeFormatter.BASIC_ISO_DATE)
                else {
                    val time = text.toLong()
                    if (time < 1_000_000_000) null else Instant.ofEpochMilli(if (time < 10_000_000_000) time * 1000 else time).atZone(ZoneId.systemDefault()).toLocalDate()
                }
            }.getOrNull()
            fun walk(element: JsonElement) {
                when {
                    element.isJsonArray -> element.asJsonArray.forEach(::walk)
                    element.isJsonObject -> {
                        val obj = element.asJsonObject
                        val day = listOf("date", "day", "dateTime", "listenDate").firstNotNullOfOrNull { date(obj.get(it)) }
                        val millis = listOf("listenTimeMs", "listeningTimeMs", "playTimeMs").firstNotNullOfOrNull { key -> runCatching { obj.get(key)?.asLong }.getOrNull() }
                        val seconds = listOf("listenTime", "listeningTime", "playTime", "listenDuration").firstNotNullOfOrNull { key -> runCatching { obj.get(key)?.asLong }.getOrNull() }
                        if (day != null && (millis != null || seconds != null)) {
                            days[day] = millis?.coerceIn(0, 86_400_000) ?: (seconds!!.coerceIn(0, 86_400) * 1000)
                        } else obj.entrySet().forEach { (_, value) -> walk(value) }
                    }
                }
            }
            root.get("data")?.let(::walk)
            return if (days.isEmpty()) null else ListeningReport(days, days.values.sum())
        }
    }
}
