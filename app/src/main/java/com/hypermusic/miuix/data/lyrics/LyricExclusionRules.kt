package com.hypermusic.miuix.data.lyrics

import android.content.Context
import android.content.SharedPreferences
import androidx.annotation.Keep
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * 歌词排除规则：可配置的「无用歌词」过滤（原 LyricParser.isExcludedLyric 的替代）。
 *
 * - 默认规则：内置的冒号关键词（正则表示冒号）、纯文本关键词、歌曲信息（标题行）、短时关键词。
 * - 用户规则：关键词 / 歌曲信息模板 / 简单正则 / 仅时长，可增删改。
 *
 * 匹配通过 [shouldExclude] 完成；总开关为 [enabled]，默认规则可逐条开关。
 */
object LyricExclusionRules {

    private const val PREFS_NAME = "lyric_exclusion_settings"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_DISABLED_DEFAULT_IDS = "disabled_default_ids"
    private const val KEY_USER_RULES = "user_rules"
    private const val SLASH_RULE_ID = "default-special-slashes"
    private const val NEIGHBOR_RULE_ID = "default-special-excluded-neighbors"

    /** 规则类型。 */
    enum class RuleKind { KEYWORD, REGEX, SONG_INFO, DURATION }

    @Keep
    enum class MatchType(val label: String, val tag: String) {
        ANYWHERE("任意位置包含此关键词", ""),
        START("开头包含此关键词", "开头"),
        END("末尾包含此关键词", "末尾"),
        EXACT("完全匹配此关键词", "完全匹配")
    }

    /** 一条排除规则。[durationMs] 为时长上限，[durationMinMs] 为时长下限（0 表示不限）。 */
    @Keep
    data class Rule(
        val id: String = "",
        val pattern: String = "",
        val durationMs: Long = 0L,
        val durationMinMs: Long = 0L,
        // Null also covers rules persisted before matching types were introduced.
        val matchType: MatchType? = null
    ) {
        val effectiveMatchType: MatchType get() = matchType ?: MatchType.ANYWHERE
    }

    private val gson = Gson()
    private var prefs: SharedPreferences? = null

    /** 总开关：启用歌词排除。 */
    var enabled by mutableStateOf(true)
        private set

    /** 被关闭的默认规则 id 集合（默认规则逐条可开关）。 */
    var disabledDefaultRuleIds by mutableStateOf<Set<String>>(emptySet())
        private set

    /** 用户自定义规则。 */
    var userRules by mutableStateOf<List<Rule>>(emptyList())
        private set

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        disabledDefaultRuleIds = runCatching {
            val json = prefs?.getString(KEY_DISABLED_DEFAULT_IDS, null) ?: return@runCatching emptySet()
            val type = object : TypeToken<Set<String>>() {}.type
            gson.fromJson<Set<String>>(json, type) ?: emptySet()
        }.getOrDefault(emptySet())
        userRules = runCatching {
            val json = prefs?.getString(KEY_USER_RULES, null) ?: return@runCatching emptyList()
            val type = object : TypeToken<List<Rule>>() {}.type
            gson.fromJson<List<Rule>>(json, type) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun updateEnabled(value: Boolean) {
        enabled = value
        prefs?.edit()?.putBoolean(KEY_ENABLED, value)?.apply()
    }

    /** 默认规则是否启用。 */
    fun isDefaultRuleEnabled(id: String): Boolean = id !in disabledDefaultRuleIds

    /** 逐条开关默认规则。 */
    fun updateDefaultRuleEnabled(id: String, enabled: Boolean) {
        val updated = if (enabled) {
            disabledDefaultRuleIds - id
        } else {
            disabledDefaultRuleIds + id
        }
        if (updated == disabledDefaultRuleIds) return
        disabledDefaultRuleIds = updated
        prefs?.edit()?.putString(KEY_DISABLED_DEFAULT_IDS, gson.toJson(updated))?.apply()
    }

    fun addUserRule(rule: Rule) {
        val created = rule.copy(id = rule.id.ifEmpty { UUID.randomUUID().toString() })
        userRules = userRules + created
        persistUserRules()
    }

    fun updateUserRule(rule: Rule) {
        val index = userRules.indexOfFirst { it.id == rule.id }
        if (index < 0) return
        userRules = userRules.toMutableList().apply { set(index, rule) }
        persistUserRules()
    }

    fun removeUserRule(id: String) {
        val updated = userRules.filterNot { it.id == id }
        if (updated.size == userRules.size) return
        userRules = updated
        persistUserRules()
    }

    private fun persistUserRules() {
        prefs?.edit()?.putString(KEY_USER_RULES, gson.toJson(userRules))?.apply()
    }

    // ------------------------------------------------------------------
    // 判定入口
    // ------------------------------------------------------------------

    /**
     * 是否需要排除该行歌词。
     *
     * @param text 歌词原文（已 trim）
     * @param durationMs 该行时长（毫秒）；未知时为 0
     * @param title 歌曲标题
     * @param artists 歌手列表（已拆分为单个歌手）
     * @param album 专辑名
     */
    fun shouldExclude(
        text: String,
        durationMs: Long,
        title: String,
        artists: List<String>,
        album: String
    ): Boolean {
        if (!enabled) return false
        if (text.isEmpty()) return false
        if (defaultRules.any { isDefaultRuleEnabled(it.id) && matches(it, text, durationMs, title, artists, album) }) {
            return true
        }
        return userRules.any { matches(it, text, durationMs, title, artists, album) }
    }

    private fun matches(
        rule: Rule,
        text: String,
        durationMs: Long,
        title: String,
        artists: List<String>,
        album: String
    ): Boolean {
        if (!durationMatches(rule, durationMs)) return false
        if (rule.id == NEIGHBOR_RULE_ID) return false // Requires the original neighboring lines.
        if (rule.id == SLASH_RULE_ID) {
            return text.count { it == '/' || it == '\\' } >= 3
        }
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) {
            // 仅时长规则
            return rule.durationMs > 0 || rule.durationMinMs > 0
        }
        return when (kindOf(pattern, rule.durationMs)) {
            RuleKind.SONG_INFO -> matchesSongInfo(pattern, text, title, artists, album)
            RuleKind.REGEX -> {
                val regex = regexOf(pattern)
                if (regex == null) matchesKeyword(pattern, text, rule.effectiveMatchType)
                else when (rule.effectiveMatchType) {
                    MatchType.ANYWHERE -> regex.containsMatchIn(text)
                    MatchType.START -> regex.find(text)?.range?.first == 0
                    MatchType.END -> regex.findAll(text).any { it.range.last + 1 == text.length }
                    MatchType.EXACT -> regex.matches(text)
                }
            }
            RuleKind.KEYWORD -> matchesKeyword(pattern, text, rule.effectiveMatchType)
            RuleKind.DURATION -> rule.durationMs > 0 || rule.durationMinMs > 0
        }
    }

    private fun matchesKeyword(pattern: String, text: String, matchType: MatchType): Boolean = when (matchType) {
        MatchType.ANYWHERE -> text.contains(pattern)
        MatchType.START -> text.startsWith(pattern)
        MatchType.END -> text.endsWith(pattern)
        MatchType.EXACT -> text == pattern
    }

    fun tags(rule: Rule): List<String> = buildList {
        if (rule.id == SLASH_RULE_ID || rule.id == NEIGHBOR_RULE_ID) add("特殊规则")
        else if (rule.pattern.isNotBlank()) rule.effectiveMatchType.tag.takeIf { it.isNotEmpty() }?.let { add(it) }
        describe(rule).takeIf { it.isNotEmpty() && it != "特殊规则" }?.let { add(it) }
    }

    private fun durationMatches(rule: Rule, duration: Long): Boolean {
        val min = rule.durationMinMs
        val max = rule.durationMs
        if (min <= 0 && max <= 0) return true
        if (duration <= 0) return false
        if (min > 0 && duration < min) return false
        if (max > 0 && duration > max) return false
        return true
    }

    // ------------------------------------------------------------------
    // 规则类型识别
    // ------------------------------------------------------------------

    /** 根据规则文本推断类型。 */
    fun kindOf(pattern: String, durationMs: Long = 0L): RuleKind {
        val p = pattern.trim()
        if (p.isEmpty()) return RuleKind.DURATION
        if (p.contains("{title}") || p.contains("{auther}") || p.contains("{artist}") || p.contains("{album}")) {
            return RuleKind.SONG_INFO
        }
        if (looksLikeRegex(p)) return RuleKind.REGEX
        return RuleKind.KEYWORD
    }

    /** 简单的正则识别：命中常见正则特征时视为正则规则。 */
    private fun looksLikeRegex(p: String): Boolean {
        if (p.startsWith("^") || p.endsWith("$")) return true
        if (p.contains(".*") || p.contains(".+") || p.contains(".?")) return true
        if (p.contains('|')) return true
        if (p.contains('[') && p.contains(']')) return true
        // \d \w \s \S \D \W \b \. 等转义
        if (Regex("""\\[dwsSDWbB.]""").containsMatchIn(p)) return true
        return false
    }

    fun kindLabel(kind: RuleKind): String = when (kind) {
        RuleKind.KEYWORD -> "关键词"
        RuleKind.REGEX -> "正则"
        RuleKind.SONG_INFO -> "歌曲信息"
        RuleKind.DURATION -> "仅时长"
    }

    /** 供 UI 展示规则时长，如「时长 ≤ 1999ms」「时长 1000-2000ms」；未设置时长时返回空串。 */
    fun describe(rule: Rule): String {
        if (rule.id == SLASH_RULE_ID || rule.id == NEIGHBOR_RULE_ID) return "特殊规则"
        val min = rule.durationMinMs
        val max = rule.durationMs
        return when {
            min > 0 && max > 0 -> "时长 ${min}-${max}ms"
            max > 0 -> "时长 ≤ ${max}ms"
            min > 0 -> "时长 ≥ ${min}ms"
            else -> ""
        }
    }

    /**
     * 解析时长输入，支持单个值、闭区间与单边区间：
     * - "1000" → (0, 1000)
     * - "1000-2000" → (1000, 2000)
     * - "-2000" → (0, 2000)
     * - "1000-" → (1000, 0)
     */
    fun parseDurationRange(input: String): Pair<Long, Long> {
        val text = input.trim()
        if (text.isEmpty()) return 0L to 0L
        if (!text.contains('-')) {
            return 0L to (text.toLongOrNull() ?: 0L)
        }
        val index = text.indexOf('-')
        val left = text.substring(0, index).trim().toLongOrNull() ?: 0L
        val right = text.substring(index + 1).trim().toLongOrNull() ?: 0L
        return if (right > 0 && left > right) right to left else left to right
    }

    /** 将规则时长还原为输入框文本。 */
    fun durationInputText(rule: Rule): String = when {
        rule.durationMinMs > 0 && rule.durationMs > 0 -> "${rule.durationMinMs}-${rule.durationMs}"
        rule.durationMs > 0 -> rule.durationMs.toString()
        rule.durationMinMs > 0 -> "${rule.durationMinMs}-"
        else -> ""
    }

    // ------------------------------------------------------------------
    // 歌曲信息模板匹配
    // ------------------------------------------------------------------

    private val SONG_INFO_TOKENS = listOf("{title}", "{auther}", "{artist}", "{album}")

    private fun matchesSongInfo(
        pattern: String,
        text: String,
        title: String,
        artists: List<String>,
        album: String
    ): Boolean {
        val normalizedLine = normalizeLine(text)
        if (normalizedLine.isEmpty()) return false
        // One metadata item beside a separator can have arbitrary text on the other side.
        // Match actual values literally, never interpret song names as regex syntax.
        val normalizedPattern = normalizeLine(pattern)
        val separatorToken = SONG_INFO_TOKENS.firstOrNull { normalizedPattern == "$it-" || normalizedPattern == "-$it" }
        if (separatorToken != null) {
            val values = when (separatorToken) {
                "{title}" -> listOf(title)
                "{album}" -> listOf(album)
                else -> artists + artists.flatMap { it.split("/", "、", "&", "＆", ",", "，", "；", ";") }
            }.map(::normalizeLine).filter { it.isNotEmpty() }
            return values.any { value ->
                normalizedLine.contains(if (normalizedPattern.startsWith(separatorToken)) "$value-" else "-$value")
            }
        }
        val (regex, tokens) = buildSongInfoRegex(normalizeLine(pattern)) ?: return false
        val match = regex.matchEntire(normalizedLine) ?: return false
        for (index in tokens.indices) {
            val value = match.groupValues.getOrNull(index + 1) ?: return false
            when (tokens[index]) {
                "{title}" -> if (value != normalizeLine(title)) return false
                "{album}" -> if (value != normalizeLine(album)) return false
                else -> if (!artistSegmentMatches(value, artists)) return false
            }
        }
        return true
    }

    /** 把歌曲信息模板编译为正则，占位符对应一个捕获组。 */
    private fun buildSongInfoRegex(template: String): Pair<Regex, List<String>>? {
        if (template.isEmpty()) return null
        val tokens = mutableListOf<String>()
        val builder = StringBuilder("^")
        var index = 0
        while (index < template.length) {
            val token = SONG_INFO_TOKENS.firstOrNull { template.startsWith(it, index) }
            if (token != null) {
                builder.append("(.*?)")
                tokens.add(if (token == "{artist}") "{auther}" else token)
                index += token.length
            } else {
                val ch = template[index]
                if (ch in "\\^$.|?*+()[]{}") builder.append('\\')
                builder.append(ch)
                index++
            }
        }
        builder.append('$')
        val regex = runCatching { Regex(builder.toString()) }.getOrNull() ?: return null
        return regex to tokens
    }

    /** 歌手部分只要包含任意一位实际歌手即视为匹配（也兼容简写/全名）。 */
    private fun artistSegmentMatches(segment: String, artists: List<String>): Boolean {
        if (segment.isEmpty()) return false
        val normalizedArtists = artists
            .flatMap { it.split("/", "、", "&", "＆", ",", "，", "；", ";") }
            .map { normalizeLine(it) }
            .filter { it.isNotEmpty() }
        if (normalizedArtists.isEmpty()) return false
        return normalizedArtists.any { artist ->
            segment.contains(artist) || artist.contains(segment)
        }
    }

    /**
     * 标题行比较前的归一化：
     * - 去掉所有括号及其内容（如「(live)」「（伴奏）」）
     * - 统一「-」前后空格（可有可无）
     * - 合并多余空白
     */
    private fun normalizeLine(raw: String): String {
        var s = raw.trim()
        val bracket = Regex("[（(\\[【][^）)\\]】]*[）)\\]】]")
        repeat(4) { s = s.replace(bracket, "") }
        s = s.replace(Regex("\\s*[-－—–]\\s*"), "-")
        // 歌词可能含多余空格，直接全部去掉再比较
        s = s.replace(Regex("\\s+"), "")
        return s.trim().lowercase()
    }

    private val regexCache = ConcurrentHashMap<String, Regex>()

    private fun regexOf(pattern: String): Regex? {
        regexCache[pattern]?.let { return it }
        return runCatching { Regex(pattern) }.getOrNull()?.also { regexCache[pattern] = it }
    }

    // ------------------------------------------------------------------
    // 默认规则（按固定 ID 保存单条开关状态）
    // ------------------------------------------------------------------

    private val compiledPresets: List<Rule> by lazy { PresetLyricRuleCompiler.compile() }

    // 保留这些分组访问器，兼容现有测试与调用；规则定义统一见 PresetLyricRules。
    val defaultColonRules: List<Rule> by lazy { compiledPresets.filter { it.id.startsWith("default-colon-") } }
    val defaultExtraRules: List<Rule> by lazy { compiledPresets.filter { it.id.startsWith("default-extra-") } }
    val defaultPlainRules: List<Rule> by lazy { compiledPresets.filter { it.id.startsWith("default-plain-") } }
    val defaultTitleRules: List<Rule> by lazy { compiledPresets.filter { it.id.startsWith("default-title-") || it.id.startsWith("default-info-") } }
    val defaultShortDurationRules: List<Rule> by lazy { compiledPresets.filter { it.id.startsWith("default-short-") } }

    /** 特殊规则沿用原有 ID 和实现，其余规则由分类清单编译。 */
    val defaultRules: List<Rule> by lazy {
        listOf(Rule(id = SLASH_RULE_ID, pattern = "包含超过4个斜杠或反斜杠")) +
            compiledPresets +
            listOf(Rule(id = NEIGHBOR_RULE_ID, pattern = "前后均为被排除的歌词"))
    }

    /** A single pass over the original exclusions: never use newly excluded lines as seeds. */
    internal fun includeExcludedNeighbors(excluded: List<Boolean>): List<Boolean> {
        if (!enabled || !isDefaultRuleEnabled(NEIGHBOR_RULE_ID)) return excluded
        return excluded.mapIndexed { index, value ->
            value || (index > 0 && index < excluded.lastIndex && excluded[index - 1] && excluded[index + 1])
        }
    }

    /** 当前启用的默认规则数量（供 UI 展示）。 */
    val enabledDefaultRuleCount: Int
        get() = defaultRules.count { isDefaultRuleEnabled(it.id) }

}
