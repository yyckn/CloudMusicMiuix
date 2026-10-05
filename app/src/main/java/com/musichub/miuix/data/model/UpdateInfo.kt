package com.musichub.miuix.data.model

/** 检查更新返回的版本信息 */
data class UpdateInfo(
    val version: String = "",
    val date: String = "",
    val changelog: List<String> = emptyList(),
    val downloadUrl: String = ""
)
