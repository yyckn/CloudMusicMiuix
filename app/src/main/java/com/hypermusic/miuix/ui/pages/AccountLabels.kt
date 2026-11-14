package com.hypermusic.miuix.ui.pages

internal fun accountOptionLabels(accounts: List<AccountInfo>): List<String> {
    val counts = accounts.groupingBy { it.nickname }.eachCount()
    return accounts.map { account ->
        buildString {
            append(account.nickname)
            account.loginLabel?.let { append("（${it.removeSuffix("登录")}）") }
            if ((counts[account.nickname] ?: 0) > 1) {
                val id = account.userId
                append("（ID：${if (id.length > 4) "**${id.takeLast(4)}" else id}）")
            }
        }
    }
}
