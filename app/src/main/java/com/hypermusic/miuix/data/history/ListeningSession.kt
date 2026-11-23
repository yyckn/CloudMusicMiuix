package com.hypermusic.miuix.data.history

/** Monotonic real playback time; seeking and buffering cannot inflate listening time. */
class ListeningSession {
    var listenedMs = 0L
        private set
    var playedMediaMs = 0L
        private set
    private var lastTick: Long? = null
    private var playing = false
    private var speed = 1f

    fun update(now: Long, isPlaying: Boolean, playbackSpeed: Float = 1f) {
        lastTick?.let { previous ->
            if (playing) {
                val delta = (now - previous).coerceAtLeast(0)
                listenedMs += delta
                playedMediaMs += (delta * speed).toLong()
            }
        }
        lastTick = now
        playing = isPlaying
        speed = playbackSpeed
    }

    fun visible() = listenedMs > 10_000L
    fun reportable(duration: Long, completed: Boolean) = if (duration in 1 until 30_000L) {
        completed && playedMediaMs >= duration - 250L
    } else listenedMs > 30_000L
}
