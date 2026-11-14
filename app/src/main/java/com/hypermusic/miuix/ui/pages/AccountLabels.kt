package com.hypermusic.miuix.ui.pages

internal fun accountOptionLabels(accounts: List<AccountInfo>): List<String> {
    // QQ 和微信账号的数据互相独立，登录类型本身已经可以区分同名账号。
    fun identity(account: AccountInfo) = account.nickname to account.loginLabel?.removeSuffix("登录")
    val counts = accounts.groupingBy(::identity).eachCount()
    return accounts.map { account ->
        buildString {
            append(account.nickname)
            account.loginLabel?.let { append("（${it.removeSuffix("登录")}）") }
            if ((counts[identity(account)] ?: 0) > 1) {
                val id = account.userId
                append("（ID：${if (id.length > 4) "**${id.takeLast(4)}" else id}）")
            }
        }
    }
}
