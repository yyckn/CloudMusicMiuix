package com.hypermusic.miuix.data.download

import android.content.Context
import androidx.compose.runtime.*
import com.hypermusic.miuix.data.model.Track
import java.util.UUID

data class BatchDownloadSelection(val id: String, val tracks: List<Track>, val loadAll: (suspend () -> List<Track>)? = null)
data class BatchDownloadConflicts(val id: String, val proposed: List<DownloadEntry>, val manual: Boolean = false)

internal fun distinctDownloadTracks(tracks: List<Track>) = tracks.distinctBy { it.downloadSongKey() }
internal fun partitionDownloadConflicts(proposed: List<DownloadEntry>, existing: List<DownloadEntry>): Pair<List<DownloadEntry>, List<DownloadEntry>> {
    val keys = existing.map { it.track.downloadSongKey() }.toSet()
    return proposed.distinctBy { it.track.downloadSongKey() }.partition { it.track.downloadSongKey() in keys }
}

/** Pending selections are transient; only confirmed tasks enter the persistent downloader. */
object BatchDownloads {
    var selection by mutableStateOf<BatchDownloadSelection?>(null)
        private set
    var conflicts by mutableStateOf<BatchDownloadConflicts?>(null)
        private set
    fun open(tracks: List<Track>, loadAll: (suspend () -> List<Track>)? = null) {
        selection = BatchDownloadSelection(UUID.randomUUID().toString(), distinctDownloadTracks(tracks), loadAll)
    }
    fun closeSelection() { selection = null }
    fun start(context: Context, tracks: List<Track>) {
        val proposals = distinctDownloadTracks(tracks).map { Downloads.prepare(context, it) }
        val (duplicates, fresh) = partitionDownloadConflicts(proposals, Downloads.entries)
        selection = null
        if (duplicates.size > 1) conflicts = BatchDownloadConflicts(UUID.randomUUID().toString(), duplicates)
        Downloads.submitBatchPrepared(fresh)
        if (duplicates.size == 1) Downloads.submitPrepared(duplicates.single())
    }
    fun selectManually() { conflicts = conflicts?.copy(manual = true) }
    fun resolve(id: String, redownload: Set<String>) {
        val request = conflicts?.takeIf { it.id == id } ?: return
        conflicts = null
        request.proposed.filter { it.id in redownload }.forEach(Downloads::replacePrepared)
    }
}
