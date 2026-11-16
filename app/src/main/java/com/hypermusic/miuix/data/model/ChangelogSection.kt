package com.hypermusic.miuix.data.model

data class ChangelogSection(val title: String?, val lines: List<String>)

/** Keep ungrouped logs intact; Markdown headings become separate UI sections. */
fun UpdateInfo.sections(): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    var title: String? = null
    val lines = mutableListOf<String>()
    fun flush() {
        if (lines.isNotEmpty()) sections += ChangelogSection(title, lines.toList())
        lines.clear()
    }
    changelog.forEach { line ->
        val text = line.trim()
        if (text.startsWith("# ")) {
            flush()
            title = text.removePrefix("# ").trim()
        } else if (text.isNotEmpty()) lines += line
    }
    flush()
    return sections
}
