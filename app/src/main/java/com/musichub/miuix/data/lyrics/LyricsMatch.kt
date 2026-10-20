package com.musichub.miuix.data.lyrics

import com.musichub.miuix.data.model.Track

/** 各平台搜索到的歌词候选（用于按歌名/歌手/时长匹配）。 */
data class LyricsCandidate<T>(
    val name: String,
    val artist: String,
    val album: String?,
    val duration: Long,
    val extra: T
)

/**
 * 跨平台歌词候选匹配，参考 SPlayer 的 pickBestCandidate：
 * 歌名必须相同或互相包含（且较短者占比 >= 34%），歌手必须匹配，
 * 时长相差超过 20s 的候选直接排除，再按相似度打分取最高。
 */
object LyricsMatch {

    private val normalizeRegex = Regex("""[\s\-_'"`~!?.,，。、/\\|()（）&;；:：]+""")

    fun normalize(text: String?): String =
        (text ?: "").lowercase().replace(normalizeRegex, "")

    private fun splitArtists(text: String?): List<String> =
        (text ?: "")
            .split(Regex("""[/\\、&,;，；|]+"""))
            .map { normalize(it) }
            .filter { it.isNotEmpty() }

    private fun bothContains(left: String, right: String): Boolean =
        left.isNotEmpty() && right.isNotEmpty() && (left.contains(right) || right.contains(left))

    private fun trackArtists(track: Track): List<String> {
        val names = buildList {
            addAll(track.artistNames)
            if (track.artist.isNotEmpty()) add(track.artist)
        }
        return names
            .flatMap { it.split(Regex("""[/\\、&,;，；|]+""")) }
            .map { normalize(it) }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun artistMatches(candidateArtist: String, artists: List<String>): Pair<Boolean, Boolean> {
        if (artists.isEmpty()) return false to false
        val full = normalize(candidateArtist)
        val parts = splitArtists(candidateArtist)
        if (full.isEmpty()) return false to false
        val exact = artists.any { it == full || parts.any { part -> part == it } }
        if (exact) return true to false
        val contains = artists.any { artist ->
            artist.length >= 2 &&
                (bothContains(full, artist) || parts.any { part -> bothContains(part, artist) })
        }
        return false to contains
    }

    private fun durationClose(left: Long, right: Long): Boolean =
        left > 0 && right > 0 && kotlin.math.abs(left - right) <= 5_000L

    private fun durationFar(left: Long, right: Long): Boolean =
        left > 0 && right > 0 && kotlin.math.abs(left - right) > 20_000L

    fun pickBestCandidate(candidates: List<LyricsCandidate<*>>, track: Track): LyricsCandidate<*>? {
        val trackName = normalize(track.title)
        val artists = trackArtists(track)
        val trackAlbum = normalize(track.album)

        var best: LyricsCandidate<*>? = null
        var bestScore = 0

        for (candidate in candidates) {
            val candidateName = normalize(candidate.name)
            val nameExact = candidateName.isNotEmpty() && candidateName == trackName
            if (!nameExact) {
                if (!bothContains(candidateName, trackName)) continue
                val longer = maxOf(candidateName.length, trackName.length)
                val shorter = minOf(candidateName.length, trackName.length)
                if (longer == 0 || shorter.toFloat() / longer < 0.34f) continue
            }

            if (durationFar(candidate.duration, track.duration)) continue

            val (artistExact, artistContains) = artistMatches(candidate.artist, artists)
            if (artists.isNotEmpty() && !artistExact && !artistContains) continue

            var score = if (nameExact) 10 else 4
            if (artistExact) score += 5 else if (artistContains) score += 2
            if (trackAlbum.isNotEmpty() && normalize(candidate.album) == trackAlbum) score += 2
            if (durationClose(candidate.duration, track.duration)) score += 3

            if (score > bestScore) {
                bestScore = score
                best = candidate
            }
        }
        return best
    }
}
