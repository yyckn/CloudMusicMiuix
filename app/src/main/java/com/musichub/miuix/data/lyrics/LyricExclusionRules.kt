package com.musichub.miuix.data.lyrics

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

    /** 规则类型。 */
    enum class RuleKind { KEYWORD, REGEX, SONG_INFO, DURATION }

    /** 一条排除规则。[durationMs] 为时长上限，[durationMinMs] 为时长下限（0 表示不限）。 */
    @Keep
    data class Rule(
        val id: String = "",
        val pattern: String = "",
        val durationMs: Long = 0L,
        val durationMinMs: Long = 0L
    )

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
        val pattern = rule.pattern.trim()
        if (pattern.isEmpty()) {
            // 仅时长规则
            return rule.durationMs > 0 || rule.durationMinMs > 0
        }
        return when (kindOf(pattern, rule.durationMs)) {
            RuleKind.SONG_INFO -> matchesSongInfo(pattern, text, title, artists, album)
            RuleKind.REGEX -> {
                val regex = regexOf(pattern)
                if (regex != null) regex.containsMatchIn(text) else text.contains(pattern)
            }
            RuleKind.KEYWORD -> text.contains(pattern)
            RuleKind.DURATION -> rule.durationMs > 0 || rule.durationMinMs > 0
        }
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
    // 默认规则（内置，不可单独编辑，只能整体开关）
    // ------------------------------------------------------------------

    private fun regexLiteral(keyword: String): String {
        val meta = "\\^$.|?*+()[]{}"
        return buildString {
            for (ch in keyword) {
                if (ch in meta) append('\\')
                append(ch)
            }
        }
    }

    /** 冒号关键词：用正则表示冒号，如 编曲\s*[:：]。 */
    val defaultColonRules: List<Rule> by lazy {
        colonKeywords.mapIndexed { index, keyword ->
            Rule(
                id = "default-colon-$index",
                pattern = regexLiteral(keyword) + "\\s*[:：]"
            )
        }
    }

    /** 纯文本关键词（含版权声明类）。 */
    val defaultPlainRules: List<Rule> by lazy {
        plainKeywords.mapIndexed { index, keyword ->
            Rule(id = "default-plain-$index", pattern = keyword)
        }
    }

    /** 额外冒号规则（中文/英文混排的制作信息）。 */
    val defaultExtraRules: List<Rule> by lazy {
        val fromKeywords = extraColonKeywords.mapIndexed { index, keyword ->
            Rule(
                id = "default-extra-$index",
                pattern = regexLiteral(keyword) + "\\s*[:：]"
            )
        }
        val fromRaw = extraRawPatterns.mapIndexed { index, pattern ->
            Rule(id = "default-extra-raw-$index", pattern = pattern)
        }
        fromKeywords + fromRaw
    }

    /** 歌曲信息（标题行）：{title}/{auther}/{album} 任意两项、任意顺序，共 6 种。 */
    val defaultTitleRules: List<Rule> = listOf(
        "{title} - {auther}",
        "{auther} - {title}",
        "{title} - {album}",
        "{album} - {title}",
        "{auther} - {album}",
        "{album} - {auther}"
    ).mapIndexed { index, template ->
        Rule(id = "default-title-$index", pattern = template)
    }

    /** 短时关键词：仅当行时长不超过 1999ms 时排除。 */
    val defaultShortDurationRules: List<Rule> by lazy {
        shortDurationKeywords.mapIndexed { index, keyword ->
            Rule(
                id = "default-short-$index",
                pattern = keyword,
                durationMs = 1999L
            )
        }
    }

    /** 全部默认规则。 */
    val defaultRules: List<Rule> by lazy {
        defaultColonRules + defaultExtraRules + defaultPlainRules + defaultTitleRules + defaultShortDurationRules
    }

    /** 当前启用的默认规则数量（供 UI 展示）。 */
    val enabledDefaultRuleCount: Int
        get() = defaultRules.count { isDefaultRuleEnabled(it.id) }

    // ------------------------------------------------------------------
    // 内置关键词表（原 LyricParser 中的列表迁入此处）
    // ------------------------------------------------------------------

    private val colonKeywords = listOf(
        "编曲", "母带", "OP", "SP", "出品", "贡献者", "调教", "编写", "改编", "和声", "吉他", "鼓", "混音",
        "剪辑", "MV", "曲绘", "后期", "推广", "出品人", "制作人", "监制", "统筹", "平面视觉", "感谢",
        "录音棚", "录音", "设计", "手风琴", "巴拉莱卡", "弦乐", "小提琴", "编辑", "录音室", "工程师",
        "导演", "造型师", "妆发", "文案", "企划", "宣传", "艺人经纪", "经纪人", "营销", "总监", "op", "sp",
        "pv", "PV", "OA", "OC", "oa", "oc", "制作", "署名", "策划", "表演", "词", "曲", "发行", "原唱",
        "打击乐", "贝斯", "钢琴", "键盘", "Program", "合音", "合唱", "演唱", "乐谱", "提琴", "和音",
        "鸣谢", "运营", "执行", "拍摄", "摄影", "花絮", "助理", "服装", "顾问", "版权", "翻译", "制片",
        "道具师", "灯光师", "调色", "合成", "承制", "艺人", "行销", "媒介", "有限公司", "资产",
        "技术支持", "合作伙伴", "视频平台", "口哨", "三弦", "长笛", "黑管", "童声", "缩混", "合作",
        "指导", "PGM", "编舞", "舞团", "宣发", "录混", "唢呐", "萨克斯", "笛子", "声明", "古筝", "箫",
        "人声", "配唱", "主唱", "封面", "A&R", "乐队", "队长", "女生", "项目支持", "曼陀林", "指挥",
        "簧管", "独奏", "乐团", "协力", "项目", "rap", "友情客串", "二胡", "帮帮团", "Guitar",
        "旋律创作", "陶笛", "冬不拉", "扬琴", "卡龙", "小号", "中号", "大号", "竹笛", "音乐技术",
        "Producers", "采样", "Drum", "Recording Engineers", "Mixing Engineer", "Recording Studio",
        "Mixing Studio", "Musicians", "乐器演奏", "厂牌", "伴唱", "商务", "Arranged by", "Produced by",
        "mastering",
        "果沃琴演奏Guowoqin performer", "快递员 Performer", "音乐", "彝语念白", "吴语念白", "箱琴",
        "收件人", "特别派送"
    )

    /** 额外冒号规则：中英混排的制作信息（用正则表示冒号）。 */
    private val extraColonKeywords = listOf(
        "歌词版权管理",
        "作词（寄件人）",
        "作曲（寄件人）",
        "监制Executive producer",
        "制作公司Manufacturing Company",
        "词 Lyricist",
        "曲 Composer",
        "编曲 Arrangement",
        "制作人 Producer",
        "贝斯 Bass",
        "和声编写 Backing Vocal Editor",
        "和声 Backing Vocal",
        "录音工程师 Recording Engineer",
        "人声编辑 Vocal Editor",
        "母带处理工程师 Mastering Engineer",
        "混音 & 母带处理工作室 Mixing & Mastering Studio"
    )

    /** 额外的原始正则规则。 */
    private val extraRawPatterns = listOf(
        "\\s*[:：]"
    )

    private val plainKeywords = listOf(
        "支持好歌曲，为歌曲留名",
        "支持好音乐，为歌曲留名",
        "网易云音乐ACG企划",
        "纯音乐，请欣赏",
        "未经著作权人许可",
        "未经著作权人书面许可",
        "版权公司授权",
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

    private val shortDurationKeywords = listOf(
        "工作团队", "出品", "广播电视台", "故宫博物院", "音乐集团",
        "联合出品", "文化传媒", "微博", "协办单位", "产业大会", "汪斯遥", "Zhang"
    )
}
