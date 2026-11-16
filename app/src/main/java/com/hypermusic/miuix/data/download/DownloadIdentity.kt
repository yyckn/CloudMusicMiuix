package com.hypermusic.miuix.data.download

import com.hypermusic.miuix.data.model.Track

data class DownloadSongKey(val source: String, val id: String)
fun Track.downloadSongKey() = DownloadSongKey(source, id)
fun sameDownloadSong(first: Track, second: Track) = first.downloadSongKey() == second.downloadSongKey()
fun groupLocalDownloads(entries: List<DownloadEntry>): List<List<DownloadEntry>> =
    entries.groupBy { it.track.downloadSongKey() }.values.toList()

/** Unknown sizes are labelled as such; equal known sizes do not add a comparison row. */
fun showDownloadSizeComparison(current: Long?, existing: List<Long>): Boolean =
    existing.any { it > 0 && (current == null || current <= 0 || it != current) }

/** Only suppress the entire comparison when every existing source size is known and identical. */
fun equalDownloadSourceSizes(current: Long?, existing: List<Long?>): Boolean =
    current != null && current > 0 && existing.isNotEmpty() && existing.all { it == current }
