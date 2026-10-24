package com.musichub.miuix.data.lyrics

import com.musichub.miuix.data.model.LyricLine
import com.musichub.miuix.data.model.LyricWord
import com.musichub.miuix.data.model.SongLyrics

/**
 * 各音乐平台通用的歌词处理：原文/翻译/逐字歌词解析、歌手行判断等。
 * API 层只负责获取原始歌词字符串，统一交给本类处理。
 *
 * 「无用歌词」过滤交由 [LyricExclusionRules]（支持用户自定义）。
 */
object LyricParser {

    /**
     * 统一歌词解析入口：优先逐字歌词 yrc，否则使用 lrc。
     * artistNames 为空时会从 songArtist 按分隔符拆分。
     * 含「纯音乐」判断与开头空行处理。
     */
    fun parse(
        lrc: String,
        tlyric: String,
        yrc: String,
        songTitle: String = "",
        songArtist: String = "",
        songAlbum: String = "",
        artistNames: List<String> = emptyList()
    ): SongLyrics {
        val effectiveArtistNames = resolveArtistNames(songArtist, artistNames)
        val lyrics = if (yrc.isNotEmpty()) {
            val fromYrc = parseYrc(yrc, tlyric, effectiveArtistNames, songTitle, songAlbum)
            // 部分歌曲的 yrc 只有作词/作曲等元信息，解析不出正文时回退到 lrc
            if (fromYrc.lines.isEmpty() && lrc.isNotEmpty()) {
                parseLrc(lrc, tlyric, effectiveArtistNames, songTitle, songAlbum)
            } else {
                fromYrc
            }
        } else {
            parseLrc(lrc, tlyric, effectiveArtistNames, songTitle, songAlbum)
        }
        return finalize(lyrics, songTitle, songArtist)
    }

    /** 统一收尾：行尾中文标点清理、纯音乐判断与开头标题行。 */
    private fun finalize(lyrics: SongLyrics, songTitle: String, songArtist: String): SongLyrics {
        val normalized = lyrics.copy(lines = lyrics.lines.map { normalizeTrailingPunctuation(it) })
        val isPureMusic = normalized.lines.size <= 3
        return if (isPureMusic) {
            SongLyrics(lines = normalized.lines, isPureMusic = true)
        } else if (songTitle.isNotEmpty() && songArtist.isNotEmpty() && normalized.lines.isNotEmpty()) {
            val titleLine = LyricLine(text = " ", translation = "", startAt = 0L)
            SongLyrics(lines = listOf(titleLine) + normalized.lines, isPureMusic = false)
        } else {
            normalized
        }
    }

    /**
     * 去掉行尾的中文逗号「，」或句号「。」。
     * 逐字歌词会同步清理最后一个字，避免文字与逐字数据不一致。
     */
    private fun normalizeTrailingPunctuation(line: LyricLine): LyricLine {
        val words = line.words
        if (words.isNullOrEmpty()) {
            val text = line.text.trimEnd('，', '。').trimEnd()
            val translation = line.translation.trimEnd('，', '。').trimEnd()
            return if (text == line.text && translation == line.translation) {
                line
            } else {
                line.copy(text = text, translation = translation)
            }
        }

        val trimmedWords = words.toMutableList()
        while (trimmedWords.isNotEmpty()) {
            val last = trimmedWords.last()
            val newLastText = last.text.trimEnd('，', '。').trimEnd()
            if (newLastText == last.text) break
            if (newLastText.isEmpty()) {
                trimmedWords.removeAt(trimmedWords.lastIndex)
            } else {
                trimmedWords[trimmedWords.lastIndex] = last.copy(text = newLastText)
                break
            }
        }

        val text = trimmedWords.joinToString("") { it.text }.trimEnd('，', '。').trimEnd()
        val translation = line.translation.trimEnd('，', '。').trimEnd()
        return line.copy(
            text = text,
            translation = translation,
            words = trimmedWords.takeIf { it.isNotEmpty() }
        )
    }

    /**
     * QQ 音乐 QRC 逐字歌词。
     * 格式：[行开始,时长]文本(字开始,时长)文本(字开始,时长)...
     * 时间均为毫秒绝对时间；也兼容 XML 包装（LyricContent）。
     */
    fun parseQrc(
        qrc: String,
        tlyric: String,
        songTitle: String = "",
        songArtist: String = "",
        songAlbum: String = "",
        artistNames: List<String> = emptyList()
    ): SongLyrics {
        val effectiveArtistNames = resolveArtistNames(songArtist, artistNames)
        val content = extractQrcContent(qrc)
        val tlyricMap = parseTranslation(tlyric)
        val lines = mutableListOf<LyricLine>()
        val headerRegex = Regex("""^\[(\d+),(\d+)]""")
        val timingRegex = Regex("""\((\d+),(\d+)\)""")

        for (raw in content.split("\n")) {
            val line = raw.trim()
            val header = headerRegex.find(line) ?: continue
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val rest = line.substring(header.range.last + 1)

            val words = mutableListOf<LyricWord>()
            var pos = 0
            while (pos < rest.length) {
                val match = timingRegex.find(rest, pos) ?: break
                val text = rest.substring(pos, match.range.first)
                val wordStart = match.groupValues[1].toLong()
                val wordDur = match.groupValues[2].toLong()
                if (text.isNotEmpty()) {
                    words.add(LyricWord(text = text, startAt = wordStart, endAt = wordStart + wordDur))
                }
                pos = match.range.last + 1
            }
            val tail = rest.substring(pos)
            if (tail.isNotEmpty()) {
                words.add(LyricWord(text = tail, startAt = start + dur, endAt = start + dur))
            }

            val text = words.joinToString("") { it.text }.trim()
            if (text.isEmpty() || shouldExclude(text, dur, songTitle, effectiveArtistNames, songAlbum)) continue
            appendParsedLine(
                lines = lines,
                text = text,
                translation = findTranslation(tlyricMap, start),
                start = start,
                end = start + dur,
                words = words,
                artistNames = effectiveArtistNames
            )
        }

        return finalize(
            SongLyrics(lines = expandIrrelevantBetween(lines.sortedBy { it.startAt })),
            songTitle,
            songArtist
        )
    }

    /**
     * 酷狗 KRC 逐字歌词。
     * 格式：[行开始,时长]<字偏移,时长,0>文本<字偏移,时长,0>文本...
     * 字偏移相对行开始时间。
     */
    fun parseKrc(
        krc: String,
        tlyric: String,
        songTitle: String = "",
        songArtist: String = "",
        songAlbum: String = "",
        artistNames: List<String> = emptyList()
    ): SongLyrics {
        val effectiveArtistNames = resolveArtistNames(songArtist, artistNames)
        val tlyricMap = parseTranslation(tlyric)
        val lines = mutableListOf<LyricLine>()
        val headerRegex = Regex("""^\[(\d+),(\d+)]""")
        val wordRegex = Regex("""<(\d+),(\d+)(?:,[-\d]+)?>([^<]*)""")

        for (raw in krc.split("\n")) {
            val line = raw.trim()
            val header = headerRegex.find(line) ?: continue
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val rest = line.substring(header.range.last + 1)

            val words = wordRegex.findAll(rest).mapNotNull { match ->
                val text = match.groupValues[3]
                if (text.isEmpty()) return@mapNotNull null
                val wordStart = start + match.groupValues[1].toLong()
                val wordDur = match.groupValues[2].toLong()
                LyricWord(text = text, startAt = wordStart, endAt = wordStart + wordDur)
            }.toList()

            val text = words.joinToString("") { it.text }.trim()
            if (text.isEmpty() || shouldExclude(text, dur, songTitle, effectiveArtistNames, songAlbum)) continue
            appendParsedLine(
                lines = lines,
                text = text,
                translation = findTranslation(tlyricMap, start),
                start = start,
                end = start + dur,
                words = words,
                artistNames = effectiveArtistNames
            )
        }

        return finalize(
            SongLyrics(lines = expandIrrelevantBetween(lines.sortedBy { it.startAt })),
            songTitle,
            songArtist
        )
    }

    private fun resolveArtistNames(songArtist: String, artistNames: List<String>): List<String> =
        if (artistNames.isNotEmpty()) {
            artistNames
        } else {
            songArtist.split("/", "、", " ").map { it.trim() }.filter { it.isNotEmpty() }
        }

    /** 交给可配置的排除规则处理。 */
    private fun shouldExclude(
        text: String,
        duration: Long,
        songTitle: String,
        artistNames: List<String>,
        songAlbum: String
    ): Boolean = LyricExclusionRules.shouldExclude(
        text = text,
        durationMs = duration,
        title = songTitle,
        artists = artistNames,
        album = songAlbum
    )

    /** 解析 LRC 翻译为时间戳映射。 */
    private fun parseTranslation(tlyric: String): Map<Long, String> {
        val map = mutableMapOf<Long, String>()
        if (tlyric.isEmpty()) return map
        val leadingTagsRegex = Regex("""^\s*((?:\[\d+:\d+(?:\.\d+)?])+)(.*)$""")
        val tagRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        for (line in tlyric.split("\n")) {
            val match = leadingTagsRegex.find(line) ?: continue
            val tag = tagRegex.find(match.groupValues[1]) ?: continue
            val time = tag.groupValues[1].toLong() * 60_000L +
                (tag.groupValues[2].toDouble() * 1000).toLong()
            val text = match.groupValues[2].trim()
            if (text.isNotEmpty()) {
                map[time] = text
            }
        }
        return map
    }

    /** 去除 QRC 的 XML 包装，取出 LyricContent。 */
    private fun extractQrcContent(text: String): String {
        if (!text.trimStart().startsWith("<")) return text
        val greedy = Regex("LyricContent=\"([\\s\\S]*)\"\\s*/?>").find(text)
        val cdata = Regex("<!\\[CDATA\\[([\\s\\S]*?)]]>").find(text)
        val attr = Regex("LyricContent=\"([^\"]*)\"").find(text)
        val raw = greedy?.groupValues?.get(1)
            ?: cdata?.groupValues?.get(1)
            ?: attr?.groupValues?.get(1)
            ?: return text
        return unescapeXml(raw)
    }

    private fun unescapeXml(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#039;", "'")
            .replace("&amp;", "&")

    /** QRC/KRC 共用：歌手行拆分、过滤后追加到结果。 */
    private fun appendParsedLine(
        lines: MutableList<LyricLine>,
        text: String,
        translation: String,
        start: Long,
        end: Long,
        words: List<LyricWord>,
        artistNames: List<String>
    ) {
        val wordList = words.takeIf { it.isNotEmpty() }
        val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)

        if (isArtist && splitLyrics != null) {
            val artistText = formatArtistText(splitLyrics.first)
            val lyricText = splitLyrics.second
            lines.add(
                LyricLine(
                    text = artistText,
                    translation = "",
                    startAt = start,
                    endAt = end,
                    words = null,
                    isArtistLine = true
                )
            )
            lines.add(
                LyricLine(
                    text = lyricText,
                    translation = translation,
                    startAt = start,
                    endAt = end,
                    words = wordList,
                    isArtistLine = false
                )
            )
        } else if (isArtist) {
            lines.add(
                LyricLine(
                    text = formatArtistText(text),
                    translation = translation,
                    startAt = start,
                    endAt = end,
                    words = wordList,
                    isArtistLine = true
                )
            )
        } else {
            lines.add(
                LyricLine(
                    text = text,
                    translation = translation,
                    startAt = start,
                    endAt = end,
                    words = wordList,
                    isArtistLine = false
                )
            )
        }
    }

    private fun parseLrc(
        lrc: String,
        tlyric: String,
        artistNames: List<String> = emptyList(),
        songTitle: String = "",
        songAlbum: String = ""
    ): SongLyrics {
        val tagRegex = Regex("""\[(\d+):(\d+(?:\.\d+)?)]""")
        val leadingTagsRegex = Regex("""^\s*((?:\[\d+:\d+(?:\.\d+)?])+)(.*)$""")
        val trailingTagRegex = Regex("""^(.*?)\s*\[(\d+):(\d+(?:\.\d+)?)]\s*$""")
        val tlyricMap = mutableMapOf<Long, String>()

        fun toMillis(minutes: String, seconds: String): Long =
            minutes.toLong() * 60_000L + (seconds.toDouble() * 1000).toLong()

        // 解析翻译歌词
        if (tlyric.isNotEmpty()) {
            for (line in tlyric.split("\n")) {
                val match = leadingTagsRegex.find(line) ?: continue
                val tag = tagRegex.find(match.groupValues[1]) ?: continue
                val time = toMillis(tag.groupValues[1], tag.groupValues[2])
                val text = match.groupValues[2].trim()
                if (text.isNotEmpty()) {
                    tlyricMap[time] = text
                }
            }
        }

        // 解析原文歌词
        val lines = mutableListOf<LyricLine>()

        // 收集所有歌词行及其时间（支持行尾结束时间戳）
        val rawLines = mutableListOf<Triple<Long, Long?, String>>()
        for (line in lrc.split("\n")) {
            val leadingMatch = leadingTagsRegex.find(line) ?: continue
            val leadingTags = tagRegex.findAll(leadingMatch.groupValues[1]).toList()
            if (leadingTags.isEmpty()) continue

            var text = leadingMatch.groupValues[2]
            var end: Long? = null
            // 行尾的第二个时间戳表示该行结束时间，例如：
            // [00:14.940]祝福你在每一天里 永远多彩多姿[00:20.880]
            trailingTagRegex.find(text)?.let { trailing ->
                text = trailing.groupValues[1]
                end = toMillis(trailing.groupValues[2], trailing.groupValues[3])
            }
            text = text.trim()
            if (text.isEmpty()) continue

            // 开头连续多个时间戳则视为同一句歌词的多个起始时间
            for (tag in leadingTags) {
                val start = toMillis(tag.groupValues[1], tag.groupValues[2])
                rawLines.add(Triple(start, end, text))
            }
        }

        // 按时间排序
        rawLines.sortBy { it.first }

        // 计算每行歌词的时长并过滤
        for (i in rawLines.indices) {
            val time = rawLines[i].first
            val explicitEnd = rawLines[i].second
            val text = rawLines[i].third
            val duration = when {
                explicitEnd != null && explicitEnd > time -> explicitEnd - time
                i < rawLines.size - 1 -> rawLines[i + 1].first - time
                else -> 0L // 最后一行
            }

            if (!shouldExclude(text, duration, songTitle, artistNames, songAlbum)) {
                val translation = findTranslation(tlyricMap, time)
                val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)

                if (isArtist && splitLyrics != null) {
                    // 需要拆行：冒号前是歌手行，冒号后是正常歌词
                    val artistText = formatArtistText(splitLyrics.first)
                    val lyricText = splitLyrics.second
                    // 歌手行
                    lines.add(LyricLine(text = artistText, translation = "", startAt = time, endAt = explicitEnd ?: 0L, isArtistLine = true))
                    // 正常歌词（使用相同的时间戳）
                    lines.add(LyricLine(text = lyricText, translation = translation, startAt = time, endAt = explicitEnd ?: 0L, isArtistLine = false))
                } else if (isArtist) {
                    // 整行是歌手行
                    val artistText = formatArtistText(text)
                    lines.add(LyricLine(text = artistText, translation = translation, startAt = time, endAt = explicitEnd ?: 0L, isArtistLine = true))
                } else {
                    // 正常歌词
                    lines.add(LyricLine(text = text, translation = translation, startAt = time, endAt = explicitEnd ?: 0L, isArtistLine = false))
                }
            }
        }

        return SongLyrics(lines = expandIrrelevantBetween(lines.sortedBy { it.startAt }))
    }

    /**
     * 格式化歌手行文本
     * 1. 去掉冒号（中英文）
     */
    private fun formatArtistText(text: String): String {
        var result = text.trim()

        // 去掉末尾的冒号（中英文）
        if (result.endsWith("：")) {
            result = result.substring(0, result.length - 1).trim()
        } else if (result.endsWith(":")) {
            result = result.substring(0, result.length - 1).trim()
        }

        return result
    }

    /**
     * 去掉外围完整括号
     * 只有当歌词最外围是完整括号时才去掉
     */
    private fun removeOuterBrackets(text: String): String {
        val trimmed = text.trim()

        // 检查是否是完整括号
        if (trimmed.startsWith("（") && trimmed.endsWith("）")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        if (trimmed.startsWith("(") && trimmed.endsWith(")")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }
        if (trimmed.startsWith("【") && trimmed.endsWith("】")) {
            return trimmed.substring(1, trimmed.length - 1).trim()
        }

        return trimmed
    }

    /**
     * 解析歌手行
     * 返回：Pair<Boolean, Pair<String, String>?>
     * - 第一个值：是否是歌手行
     * - 第二个值：如果需要拆行，返回Pair(歌手文本, 歌词文本)；否则返回null
     */
    private fun parseArtistLine(text: String, artistNames: List<String>): Pair<Boolean, Pair<String, String>?> {
        val trimmedText = text.trim()

        // 1. 去掉外围完整括号
        val contentWithoutBrackets = removeOuterBrackets(trimmedText)

        // 2. 关键词列表
        val keywords = mutableListOf<String>()
        // 歌手名称（拆分/、顿号、空格）
        keywords.addAll(artistNames.flatMap { it.split("/", "、", " ") }.map { it.trim() }.filter { it.isNotEmpty() })
        // 额外关键词
        keywords.addAll(listOf("童声", "合", "合唱", "男", "女", "何炅", "方力申", "万妮达", "袁娅维", "时代少年团宋亚轩", "艾热AIR"))

        // 3. 检查是否包含冒号（中英文都算）
        val hasChineseColon = contentWithoutBrackets.contains("：")
        val hasEnglishColon = contentWithoutBrackets.contains(":")

        if (hasChineseColon || hasEnglishColon) {
            // 找到冒号的位置（优先中文冒号）
            val colonIndex = if (hasChineseColon) contentWithoutBrackets.indexOf("：")
            else contentWithoutBrackets.indexOf(":")

            // 检查冒号前是否有括号字符（如果有则不处理）
            val beforeColon = contentWithoutBrackets.substring(0, colonIndex)
            if (beforeColon.contains("(") || beforeColon.contains("（") ||
                beforeColon.contains("[") || beforeColon.contains("【")) {
                return Pair(false, null)
            }

            val afterColon = contentWithoutBrackets.substring(colonIndex + 1).trim()

            if (afterColon.isNotEmpty()) {
                // 3.1.1 冒号后有文字：检查冒号前是否包含关键词
                for (keyword in keywords) {
                    if (beforeColon.contains(keyword)) {
                        // 返回需要拆行的标识
                        return Pair(true, Pair(beforeColon.trim(), afterColon))
                    }
                }
            } else {
                // 3.1.2 冒号后没有文字：除冒号以外的文字和关键词进行比对，剩余逻辑参考3.2
                val beforeColonTrimmed = beforeColon.trim()

                // 3.2.1 关键词和歌词完全一样，算作歌手行
                for (keyword in keywords) {
                    if (beforeColonTrimmed == keyword) {
                        return Pair(true, null)
                    }
                }

                // 3.2.2 多位歌手识别
                if (beforeColonTrimmed.contains(" ") || beforeColonTrimmed.contains("、") ||
                    beforeColonTrimmed.contains("/") || beforeColonTrimmed.contains("\\")) {
                    val artists = beforeColonTrimmed.split(" ", "、", "/", "\\")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    if (artists.isNotEmpty()) {
                        var allMatch = true
                        for (artist in artists) {
                            var artistMatch = false
                            for (keyword in keywords) {
                                // 3种情况：歌手和关键字相同/歌手包含关键字/关键字包含歌手
                                if (artist == keyword || artist.contains(keyword) || keyword.contains(artist)) {
                                    artistMatch = true
                                    break
                                }
                            }
                            if (!artistMatch) {
                                allMatch = false
                                break
                            }
                        }
                        if (allMatch) {
                            return Pair(true, null)
                        }
                    }
                }
            }
        } else {
            // 3.2 不包含冒号：直接用整行歌词和关键词做对比

            // 3.2.1 关键词和歌词完全一样，算作歌手行
            for (keyword in keywords) {
                if (contentWithoutBrackets == keyword) {
                    return Pair(true, null)
                }
            }

            // 3.2.2 多位歌手识别
            if (contentWithoutBrackets.contains(" ") || contentWithoutBrackets.contains("、") ||
                contentWithoutBrackets.contains("/") || contentWithoutBrackets.contains("\\")) {
                val artists = contentWithoutBrackets.split(" ", "、", "/", "\\")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                if (artists.isNotEmpty()) {
                    var allMatch = true
                    for (artist in artists) {
                        var artistMatch = false
                        for (keyword in keywords) {
                            // 3种情况：歌手和关键字相同/歌手包含关键字/关键字包含歌手
                            if (artist == keyword || artist.contains(keyword) || keyword.contains(artist)) {
                                artistMatch = true
                                break
                            }
                        }
                        if (!artistMatch) {
                            allMatch = false
                            break
                        }
                    }
                    if (allMatch) {
                        return Pair(true, null)
                    }
                }
            }
        }

        return Pair(false, null)
    }

    /**
     * 查找与 [time] 匹配的翻译行。
     * ytlrc 的时间戳是厘秒精度，与 yrc 的毫秒行首可能存在几十毫秒误差，
     * 因此允许 ±500ms 的近似匹配。
     */
    private fun findTranslation(map: Map<Long, String>, time: Long): String {
        if (map.isEmpty()) return ""
        map[time]?.let { return it }
        var bestValue = ""
        var bestDiff = Long.MAX_VALUE
        for ((key, value) in map) {
            val diff = kotlin.math.abs(key - time)
            if (diff < bestDiff) {
                bestDiff = diff
                bestValue = value
            }
        }
        return if (bestDiff <= 500L) bestValue else ""
    }

    private fun parseYrc(
        yrc: String,
        tlyric: String,
        artistNames: List<String> = emptyList(),
        songTitle: String = "",
        songAlbum: String = ""
    ): SongLyrics {
        val headerRegex = Regex("""\[(\d{1,19}),\s*(\d{1,19})]""")
        val segRegex = Regex("""\((\d{1,19}),\s*(\d{1,19}),\s*[-\d]{1,20}\)([^()\n\r]*)""")
        val tlyricRegex = Regex("""\[(\d+):(\d+\.\d+)](.*)""")

        // 解析翻译歌词
        val tlyricMap = mutableMapOf<Long, String>()
        if (tlyric.isNotEmpty()) {
            for (line in tlyric.split("\n")) {
                val match = tlyricRegex.find(line)
                if (match != null) {
                    val min = match.groupValues[1].toLong()
                    val sec = (match.groupValues[2].toDouble() * 1000).toLong()
                    val time = min * 60 * 1000 + sec
                    val text = match.groupValues[3].trim()
                    if (text.isNotEmpty()) {
                        tlyricMap[time] = text
                    }
                }
            }
        }

        val lines = mutableListOf<LyricLine>()

        for (line in yrc.split("\n")) {
            val header = headerRegex.find(line) ?: continue
            val start = header.groupValues[1].toLong()
            val dur = header.groupValues[2].toLong()
            val end = start + dur

            val segs = segRegex.findAll(line).toList()
            val words = mutableListOf<LyricWord>()
            val sb = StringBuilder()

            for (m in segs) {
                val ws = m.groupValues[1].toLong()
                val wd = m.groupValues[2].toLong()
                val t = m.groupValues[3]
                sb.append(t)
                if (t.isNotEmpty()) {
                    words.add(
                        LyricWord(
                            text = t,
                            startAt = ws,
                            endAt = ws + wd
                        )
                    )
                }
            }

            val text = sb.toString().trim()
            if (text.isNotEmpty() && !shouldExclude(text, dur, songTitle, artistNames, songAlbum)) {
                // 查找翻译（使用开始时间匹配）
                val translation = findTranslation(tlyricMap, start)
                val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)

                if (isArtist && splitLyrics != null) {
                    // 需要拆行：冒号前是歌手行，冒号后是正常歌词
                    val artistText = formatArtistText(splitLyrics.first)
                    val lyricText = splitLyrics.second
                    // 歌手行
                    lines.add(
                        LyricLine(
                            text = artistText,
                            translation = "",
                            startAt = start,
                            endAt = end,
                            words = null,
                            isArtistLine = true
                        )
                    )
                    // 正常歌词（使用相同的时间戳）
                    lines.add(
                        LyricLine(
                            text = lyricText,
                            translation = translation,
                            startAt = start,
                            endAt = end,
                            words = words.takeIf { it.isNotEmpty() },
                            isArtistLine = false
                        )
                    )
                } else if (isArtist) {
                    // 整行是歌手行
                    val artistText = formatArtistText(text)
                    lines.add(
                        LyricLine(
                            text = artistText,
                            translation = translation,
                            startAt = start,
                            endAt = end,
                            words = words.takeIf { it.isNotEmpty() },
                            isArtistLine = true
                        )
                    )
                } else {
                    // 正常歌词
                    lines.add(
                        LyricLine(
                            text = text,
                            translation = translation,
                            startAt = start,
                            endAt = end,
                            words = words.takeIf { it.isNotEmpty() },
                            isArtistLine = false
                        )
                    )
                }
            }
        }

        return SongLyrics(lines = expandIrrelevantBetween(lines.sortedBy { it.startAt }))
    }

    /**
     * 无关歌词过滤：
     * - 只有单个点（. 或 。）的行算无关；
     * - 前后行都是「无关歌词」时，中间行也算无关（A/B/C，A、C 无关 → B 无关）；
     * - 边界：开头第一行没有上一句，只判断下一句 —— B 为无关则 A 也算无关。
     * 判定只看原文行（非翻译行）；判定为无关的行直接过滤掉（连同其翻译）。
     * 注意：由 parseArtistLine 识别出的真实「歌手行」会保留，且不参与无关行扩展，
     * 避免轮唱歌曲中夹在两位歌手名之间的歌词被误删。
     */
    private fun expandIrrelevantBetween(lines: List<LyricLine>): List<LyricLine> {
        if (lines.isEmpty()) return lines
        val dotRegex = Regex("""\[\d+:\d+\.\d+]""")
        fun isDotOnly(text: String): Boolean {
            val t = text.replace(dotRegex, "").trim()
            return t == "." || t == "。"
        }
        if (lines.size == 1) return if (isDotOnly(lines[0].text)) emptyList() else lines

        val originalArtist = lines.map { it.isArtistLine }
        // 歌手行不作为扩展种子：否则「歌手名 / 歌词 / 下一位歌手名」这种轮唱格式，
        // 会把夹在两位歌手之间的正常歌词误判为无关行并删除。
        val irrelevant = lines.map { isDotOnly(it.text) }.toMutableList()
        var changed = true
        while (changed) {
            changed = false
            // 开头第一行：没有上一句，只看下一句
            if (!irrelevant[0] && irrelevant[1]) {
                irrelevant[0] = true
                changed = true
            }
            for (i in 1 until irrelevant.size - 1) {
                if (!irrelevant[i] && irrelevant[i - 1] && irrelevant[i + 1]) {
                    irrelevant[i] = true
                    changed = true
                }
            }
        }
        // 过滤掉无关行：单独点行，或由邻接扩展标记出的非歌手行
        return lines.filterIndexed { index, line ->
            val dot = isDotOnly(line.text)
            !(dot || (irrelevant[index] && !originalArtist[index]))
        }
    }
}
