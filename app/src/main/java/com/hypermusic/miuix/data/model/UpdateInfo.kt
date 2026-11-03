package com.hypermusic.miuix.data.model

/** 应用内保存的版本更新记录。 */
data class UpdateInfo(
    val version: String = "",
    val date: String = "",
    val changelog: List<String> = emptyList()
)
