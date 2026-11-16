package com.hypermusic.miuix.data.lyrics

import com.hypermusic.miuix.data.model.LyricLine
import com.hypermusic.miuix.data.model.LyricWord
import com.hypermusic.miuix.data.model.SongLyrics
import com.hypermusic.miuix.data.model.Track
import com.hypermusic.miuix.ui.theme.ThemeSettings
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory

/** Parses AMLL inline and iTunes metadata translations without treating annotations as lyrics. */
object TtmlLyricsParser {
    private fun time(value: String): Long? {
        val s = value.trim()
        if (s.isBlank()) return null
        val multiplier = if (s.endsWith("ms")) 1.0 else 1000.0
        val parts = s.removeSuffix("ms").removeSuffix("s").split(':')
        val numbers = parts.map { it.toDoubleOrNull() ?: return null }
        return (numbers.fold(0.0) { acc, n -> acc * 60 + n } * multiplier).toLong()
    }
    private fun role(e: Element) = e.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "role").ifBlank { e.getAttribute("ttm:role") }
    private fun attr(e: Element, name: String): String = e.getAttribute(name).ifBlank { e.getAttributeNS("http://itunes.apple.com/lyric-ttml-extensions", name).ifBlank { e.getAttributeNS("http://music.apple.com/lyric-ttml-internal", name) } }
    private fun children(e: Element): List<Node> = (0 until e.childNodes.length).map { e.childNodes.item(it) }
    private fun plain(e: Element): String = children(e).joinToString("") { node ->
        when {
            node.nodeType == Node.TEXT_NODE -> node.nodeValue.orEmpty().let { if (it.isBlank() && ('\n' in it || '\r' in it)) "" else it }
            node is Element && role(node) !in setOf("x-bg", "x-translation", "x-roman") -> plain(node)
            else -> ""
        }
    }
    private fun words(e: Element): List<LyricWord> {
        val result = mutableListOf<LyricWord>()
        var pendingText = ""
        for (node in children(e)) {
            if (node.nodeType == Node.TEXT_NODE) {
                val fragment = node.nodeValue.orEmpty()
                if (fragment.isNotEmpty() && !(fragment.isBlank() && ('\n' in fragment || '\r' in fragment))) {
                    if (result.isNotEmpty()) result[result.lastIndex] = result.last().copy(text = result.last().text + fragment)
                    else pendingText += fragment
                }
            } else if (node is Element && role(node).isBlank()) {
                val begin = time(node.getAttribute("begin")) ?: continue
                val finish = time(node.getAttribute("end")) ?: continue
                val fragment = plain(node)
                if (fragment.isNotEmpty()) {
                    result += LyricWord(pendingText + fragment, begin, finish)
                    pendingText = ""
                }
            }
        }
        return result
    }

    private const val WORD_BOUNDARY_TOLERANCE_MS = 1_000L
    private const val EXCESSIVE_PADDING_MS = 15_000L
    private const val EXCESSIVE_DURATION_RATIO = 4L

    /** Repair invalid or clearly inconsistent p/x-bg bounds without stripping ordinary padding. */
    private fun interval(e: Element, words: List<LyricWord>, fallbackStart: Long? = null, fallbackEnd: Long? = null): Pair<Long, Long>? {
        val timed = words.filter { it.text.isNotBlank() && it.startAt >= 0 && it.endAt > it.startAt }
        val first = timed.minOfOrNull { it.startAt }
        val last = timed.maxOfOrNull { it.endAt }
        val start = time(e.getAttribute("begin")) ?: first ?: fallbackStart ?: return null
        val end = time(e.getAttribute("end")) ?: last ?: fallbackEnd ?: start
        if (first == null || last == null) {
            // An explicit zero/reversed paragraph clock with no usable syllable clock cannot
            // be repaired. Do not turn a placeholder into a lyric at 0ms or guess its timing.
            if (e.hasAttribute("end") && end <= start) return null
            return start to end
        }
        if (end <= start) return first to last

        // Do not use a partial/broken word clock to rewrite an otherwise valid paragraph interval.
        val wordText = words.joinToString("") { it.text }.filterNot(Char::isWhitespace)
        val lineText = plain(e).filterNot(Char::isWhitespace)
        val reliable = wordText == lineText && timed.size == words.count { it.text.isNotBlank() } &&
            timed.zipWithNext().all { (a, b) -> b.startAt >= a.startAt && b.endAt >= a.endAt }
        if (!reliable) return start to end

        if (first - end > WORD_BOUNDARY_TOLERANCE_MS || start - last > WORD_BOUNDARY_TOLERANCE_MS) {
            return first to last // Entire line is offset from the actual singing.
        }
        val oversized = end - start >= (last - first) * EXCESSIVE_DURATION_RATIO
        val correctedStart = when {
            start - first > WORD_BOUNDARY_TOLERANCE_MS -> first
            oversized && first - start > EXCESSIVE_PADDING_MS -> first
            else -> start
        }
        val correctedEnd = when {
            last - end > WORD_BOUNDARY_TOLERANCE_MS -> last
            oversized && end - last > EXCESSIVE_PADDING_MS -> last
            else -> end
        }
        return correctedStart to correctedEnd
    }
    fun parse(xml: String, track: Track, showRomanization: Boolean = ThemeSettings.showJapaneseRomaji): SongLyrics {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "") }
            runCatching { setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "") }
        }
        val builder = factory.newDocumentBuilder().apply {
            setEntityResolver { _, _ -> InputSource(StringReader("")) }
        }
        val doc = builder.parse(InputSource(StringReader(xml)))
        val auxiliaries = mutableMapOf<Pair<String, String>, String>()
        for (kind in listOf("translation", "transliteration")) {
            val nodes = doc.getElementsByTagNameNS("*", kind)
            for (i in 0 until nodes.length) {
                val parent = nodes.item(i) as? Element ?: continue
                val texts = parent.getElementsByTagNameNS("*", "text")
                for (j in 0 until texts.length) {
                    val text = texts.item(j) as? Element ?: continue
                    val key = text.getAttribute("for")
                    if (key.isNotBlank()) auxiliaries.putIfAbsent(kind to key, plain(text).trim())
                }
            }
        }
        val lines = mutableListOf<LyricLine>()
        val exclusions = LyricExclusionPass(track.title, track.artistNames.ifEmpty { listOf(track.artist) }, track.album)
        val ps = doc.getElementsByTagNameNS("*", "p")
        for (i in 0 until ps.length) {
            val p = ps.item(i) as? Element ?: continue
            val mainWords = words(p)
            val (start, end) = interval(p, mainWords) ?: continue
            val text = plain(p).trim()
            if (text.isBlank() || exclusions.shouldExclude(text, (end-start).coerceAtLeast(0), start, lines.size)) continue
            val key = attr(p, "key").ifBlank { p.getAttribute("itunes:key") }
            val agent = p.getAttributeNS("http://www.w3.org/ns/ttml#metadata", "agent")
                .ifBlank { p.getAttribute("ttm:agent") }
            val elements = children(p).filterIsInstance<Element>()
            val translation = elements.firstOrNull { role(it) == "x-translation" }?.let { plain(it).trim() }.orEmpty().ifBlank { auxiliaries["translation" to key].orEmpty() }
            val roman = if (showRomanization) elements.firstOrNull { role(it) == "x-roman" }?.let { plain(it).trim() }.orEmpty().ifBlank { auxiliaries["transliteration" to key].orEmpty() } else ""
            lines += LyricLine(text = text, translation = translation, phonetic = roman, startAt = start, endAt = end,
                words = mainWords.takeIf { it.isNotEmpty() }, isBackingVocal = BackingVocals.isWrapped(text), amllAgent = agent)
            // Preserve document ownership/order so overlapping background vocals stay with this main line.
            for (background in elements.filter { role(it) == "x-bg" }) {
                val backgroundText = plain(background).trim()
                if (backgroundText.isBlank()) continue
                val backgroundWords = words(background)
                val (backgroundStart, backgroundEnd) = interval(background, backgroundWords, start, end) ?: continue
                val backgroundElements = children(background).filterIsInstance<Element>()
                val backgroundTranslation = backgroundElements.firstOrNull { role(it) == "x-translation" }?.let { plain(it).trim() }.orEmpty()
                val backgroundRoman = if (showRomanization) backgroundElements.firstOrNull { role(it) == "x-roman" }?.let { plain(it).trim() }.orEmpty() else ""
                lines += LyricLine(text = backgroundText, translation = backgroundTranslation, phonetic = backgroundRoman,
                    startAt = backgroundStart, endAt = backgroundEnd, words = backgroundWords.takeIf { it.isNotEmpty() },
                    isBackingVocal = BackingVocals.isWrapped(backgroundText), amllAgent = agent)
            }
        }
        val filtered = exclusions.filter(lines).map {
            val cleaned = LyricTextCleanup.removeOuterMusicNotes(it)
            cleaned.copy(isBackingVocal = BackingVocals.isWrapped(cleaned.text))
        }
        return SongLyrics(lines = filtered, isPureMusic = lines.isNotEmpty() && filtered.isEmpty())
    }
}
