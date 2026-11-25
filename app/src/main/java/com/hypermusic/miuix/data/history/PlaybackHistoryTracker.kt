package com.hypermusic.miuix.data.history

import android.content.Context
import android.os.SystemClock
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.model.Track

/** Owned by the media service, never by a foreground screen. */
class PlaybackHistoryTracker(private val context: Context) {
    private var track: Track? = null
    private var account = ""
    private var id = ""
    private var startedAt = 0L
    private var duration = 0L
    private var session = ListeningSession()
    private var checkpoint = 0L
    private var dayTotals = mutableMapOf<String, Long>()
    private var startedPlayback = false
    private var persisted = false

    fun begin(track: Track) {
        if (track.source != "netease") return
        this.track = track
        val prefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val index = prefs.getInt(if (track.platform.isCloudDisk) "data_account_index" else "playback_account_index", 0)
        account = NeteaseApi.getAccounts().getOrNull(index)?.userId.orEmpty()
        id = ListeningHistory.newId(); startedAt = System.currentTimeMillis()
        duration = track.duration; session = ListeningSession(); checkpoint = 0
        dayTotals = mutableMapOf()
        startedPlayback = false; persisted = false
    }

    fun update(playing: Boolean, speed: Float, duration: Long = this.duration) {
        if (track == null) return
        if (duration > 0) this.duration = duration
        if (playing && !startedPlayback) { startedAt = System.currentTimeMillis(); startedPlayback = true }
        val before = session.listenedMs
        session.update(SystemClock.elapsedRealtime(), playing, speed)
        addTime(session.listenedMs - before)
        if ((!persisted && session.visible()) || session.listenedMs - checkpoint >= 5_000) persist(false, false)
    }

    fun finish(completed: Boolean = false) {
        if (track == null) return
        val before = session.listenedMs
        session.update(SystemClock.elapsedRealtime(), false)
        addTime(session.listenedMs - before)
        persist(true, completed)
        track = null
    }

    private fun persist(finished: Boolean, completed: Boolean) {
        val song = track ?: return
        val eligible = session.reportable(duration, completed)
        if (session.visible() || eligible) {
            ListeningHistory.save(ListeningRecord(id, song, account, startedAt,
                session.listenedMs, duration, eligible, finished, false, null, dayTotals.toMap()))
            persisted = true
        }
        checkpoint = session.listenedMs
    }

    private fun addTime(delta: Long) {
        var remaining = delta
        var end = java.time.Instant.ofEpochMilli(System.currentTimeMillis())
        val zone = java.time.ZoneId.systemDefault()
        while (remaining > 0) {
            val date = end.minusMillis(1).atZone(zone).toLocalDate()
            val sinceMidnight = java.time.Duration.between(date.atStartOfDay(zone).toInstant(), end).toMillis()
            val part = minOf(remaining, sinceMidnight)
            dayTotals[date.toString()] = (dayTotals[date.toString()] ?: 0) + part
            remaining -= part; end = end.minusMillis(part)
        }
    }
}
