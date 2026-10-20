package com.musichub.miuix.data.lyrics

import com.musichub.miuix.data.model.LyricLine
import com.musichub.miuix.data.model.LyricWord
import com.musichub.miuix.data.model.SongLyrics

/**
 * 各音乐平台通用的歌词处理：原文/翻译/逐字歌词解析、歌手行判断、无用歌词过滤等。
 * API 层只负责获取原始歌词字符串，统一交给本类处理。
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
        artistNames: List<String> = emptyList()
    ): SongLyrics {
        val effectiveArtistNames = if (artistNames.isNotEmpty()) {
            artistNames
        } else {
            songArtist.split("/", "、", " ").map { it.trim() }.filter { it.isNotEmpty() }
        }
        val lyrics = if (yrc.isNotEmpty()) {
            val fromYrc = parseYrc(yrc, tlyric, effectiveArtistNames, songTitle)
            // 部分歌曲的 yrc 只有作词/作曲等元信息，解析不出正文时回退到 lrc
            if (fromYrc.lines.isEmpty() && lrc.isNotEmpty()) {
                parseLrc(lrc, tlyric, effectiveArtistNames, songTitle)
            } else {
                fromYrc
            }
        } else {
            parseLrc(lrc, tlyric, effectiveArtistNames, songTitle)
        }
        return finalize(lyrics, songTitle, songArtist)
    }

    /** 统一收尾：纯音乐判断与开头标题行。 */
    private fun finalize(lyrics: SongLyrics, songTitle: String, songArtist: String): SongLyrics {
        val isPureMusic = lyrics.lines.size <= 3
        return if (isPureMusic) {
            SongLyrics(lines = lyrics.lines, isPureMusic = true)
        } else if (songTitle.isNotEmpty() && songArtist.isNotEmpty() && lyrics.lines.isNotEmpty()) {
            val titleLine = LyricLine(text = " ", translation = "", startAt = 0L)
            SongLyrics(lines = listOf(titleLine) + lyrics.lines, isPureMusic = false)
        } else {
            lyrics
        }
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
            if (text.isEmpty() || isExcludedLyric(text, dur, songTitle, effectiveArtistNames)) continue
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
            if (text.isEmpty() || isExcludedLyric(text, dur, songTitle, effectiveArtistNames)) continue
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

    private fun parseLrc(lrc: String, tlyric: String, artistNames: List<String> = emptyList(), songTitle: String = ""): SongLyrics {
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
            
            if (!isExcludedLyric(text, duration, songTitle, artistNames)) {
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

    private fun parseYrc(yrc: String, tlyric: String, artistNames: List<String> = emptyList(), songTitle: String = ""): SongLyrics {
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
            val words = mutableListOf<com.musichub.miuix.data.model.LyricWord>()
            val sb = StringBuilder()
            
            for (m in segs) {
                val ws = m.groupValues[1].toLong()
                val wd = m.groupValues[2].toLong()
                val t = m.groupValues[3]
                sb.append(t)
                if (t.isNotEmpty()) {
                    words.add(com.musichub.miuix.data.model.LyricWord(
                        text = t,
                        startAt = ws,
                        endAt = ws + wd
                    ))
                }
            }

            val text = sb.toString().trim()
            if (text.isNotEmpty() && !isExcludedLyric(text, dur, songTitle, artistNames)) {
                // 查找翻译（使用开始时间匹配）
                val translation = findTranslation(tlyricMap, start)
                val (isArtist, splitLyrics) = parseArtistLine(text, artistNames)
                
                if (isArtist && splitLyrics != null) {
                    // 需要拆行：冒号前是歌手行，冒号后是正常歌词
                    val artistText = formatArtistText(splitLyrics.first)
                    val lyricText = splitLyrics.second
                    // 歌手行
                    lines.add(LyricLine(
                        text = artistText,
                        translation = "",
                        startAt = start,
                        endAt = end,
                        words = null,
                        isArtistLine = true
                    ))
                    // 正常歌词（使用相同的时间戳）
                    lines.add(LyricLine(
                        text = lyricText,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = false
                    ))
                } else if (isArtist) {
                    // 整行是歌手行
                    val artistText = formatArtistText(text)
                    lines.add(LyricLine(
                        text = artistText,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = true
                    ))
                } else {
                    // 正常歌词
                    lines.add(LyricLine(
                        text = text,
                        translation = translation,
                        startAt = start,
                        endAt = end,
                        words = words.takeIf { it.isNotEmpty() },
                        isArtistLine = false
                    ))
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

    private fun isExcludedLyric(text: String, duration: Long = 0, songTitle: String = "", artistNames: List<String> = emptyList()): Boolean {
        val colonKeywords = listOf(
            "编曲", "母带", "OP", "SP", "出品", "贡献者", "调教", "编写", "改编", "和声", "吉他", "鼓", "混音", "剪辑", "MV", "曲绘", "后期", "推广", "出品人", "制作人", "监制", "统筹", "平面视觉", "感谢", "录音棚", "录音", "设计", "手风琴", "巴拉莱卡", "弦乐", "小提琴", "编辑", "录音室", "工程师", "导演", "造型师", "妆发", "文案", "企划", "宣传", "艺人经纪", "经纪人", "营销", "总监", "op", "sp", "pv", "PV", "OA", "OC", "oa", "oc", "制作", "署名", "策划", "表演", "词", "曲", "发行", "原唱", "打击乐", "贝斯", "钢琴", "键盘", "Program", "合音", "合唱", "演唱", "乐谱", "提琴", "和音", "鸣谢", "运营", "执行", "拍摄", "摄影", "花絮", "助理", "服装", "顾问", "版权", "翻译", "制片", "道具师", "灯光师", "调色", "合成", "承制", "艺人", "行销", "媒介", "有限公司", "资产", "技术支持", "合作伙伴", "视频平台", "口哨", "三弦", "长笛", "黑管", "童声", "缩混", "合作", "指导", "PGM", "编舞", "舞团", "宣发", "录混", "唢呐", "萨克斯", "笛子", "声明", "古筝", "箫", "人声", "配唱", "主唱", "封面", "A&R", "乐队", "队长", "女生", "项目支持", "曼陀林", "指挥", "簧管", "独奏", "乐团", "协力", "项目", "rap", "友情客串", "二胡", "帮帮团", "Guitar", "旋律创作", "陶笛", "冬不拉", "扬琴", "卡龙", "小号", "中号", "大号", "竹笛", "音乐技术", "Producers", "采样", "Drum", "Recording Engineers", "Mixing Engineer", "Recording Studio", "Mixing Studio", "Musicians", "乐器演奏", "厂牌", "伴唱", "商务", "Arranged by", "Produced by", "果沃琴演奏Guowoqin performer", "快递员 Performer", "音乐", "彝语念白", "吴语念白", "箱琴", "收件人", "特别派送"
        )

        val hasColon = text.contains("：") || text.contains(":") || text.contains(" :")
        if (hasColon) {
            for (keyword in colonKeywords) {
                if (text.contains(keyword)) {
                    return true
                }
            }
        }

        val plainKeywords = listOf(
            "支持好歌曲，为歌曲留名",
            "支持好音乐，为歌曲留名",
            "网易云音乐ACG企划",
            "纯音乐，请欣赏",
            "未经著作权人许可",
            "保留著作权",
            "著作权保留",
            "未经书面授权",
            "不得翻唱",
            "本作品经过词曲著作权利方授权",
            "ISRC ",
            "Co.",
            "Ltd.",
            "发布会现场",
            "萨克斯管/",
            "小号/",
            "长号",
            "@牛班NEWBAND",
            "御坂的蓝白",
            "长笛 ",
            "钟琴 ",
            "竖琴 ",
            "YCE ",
            "乐器配置 ",
            "录制 ",
            "音序工坊",
            "⌈π⌋ 计划"
        )
        
        for (keyword in plainKeywords) {
            if (text.contains(keyword)) {
                return true
            }
        }

        // 检查是否匹配"歌手-歌名"或"歌名-歌手"格式
        if (songTitle.isNotEmpty() && artistNames.isNotEmpty()) {
            val trimmedText = text.trim()
            // 多个歌手拆开逐个判断（也兼容 "A/B" 这种把多个歌手塞在一个名字里的情况）
            val individualArtists = artistNames
                .flatMap { it.split("/", "\\", "、", "&", "＆", ",", "，", "；", ";") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            for (artistName in individualArtists) {
                // 歌手-歌名
                if (trimmedText == "$artistName-$songTitle") return false
                // 歌手 - 歌名
                if (trimmedText == "$artistName - $songTitle") return false
                // 歌名 - 歌手
                if (trimmedText == "$songTitle - $artistName") return false
                // 歌名-歌手
                if (trimmedText == "$songTitle-$artistName") return false
            }
        }
        
        // 不带冒号的关键词+时长小于1000ms双重判定
        val shortDurationKeywords = listOf(
            "工作团队", "出品", "广播电视台", "故宫博物院", "音乐集团",
            "联合出品", "文化传媒", "微博", "协办单位", "产业大会", "汪斯遥", "Zhang"
        )
        
        if (duration in 1..1999) {
            for (keyword in shortDurationKeywords) {
                if (text.contains(keyword)) {
                    return true
                }
            }
        }
        
        // 过滤开头包含"未经著作权人书面许可"的歌词
        if (text.contains("未经著作权人书面许可")) {
            return true
        }
        
        return false
    }
}