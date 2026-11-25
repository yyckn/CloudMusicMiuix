package com.hypermusic.miuix.ui.navigation

sealed class Screen {
    object Main : Screen()
    object Search : Screen()
    object LocalMusic : Screen()
    data class PlaylistDetail(
        val coverUrl: String = "",
        val title: String = "",
        val isTopChart: Boolean = false,
        val sharedCoverKey: String? = null
    ) : Screen()
    object Login : Screen()
    object QrLogin : Screen()
    object ThemeSettings : Screen()
    object PlayPageSettings : Screen()
    object SongSettings : Screen()
    object VocalSeparation : Screen()
    object SoundEffects : Screen()
    object CacheSettings : Screen()
    object ListeningHistory : Screen()
    object DownloadSettings : Screen()
    object About : Screen()
    data class Profile(val accountId: String? = null) : Screen()
    data class Artist(val artistId: String) : Screen()
    data class Album(val albumId: Long, val qq: Boolean = false, val initialCoverUrl: String = "", val sharedCoverKey: String? = null) : Screen()
    object LyriconAdapter : Screen()
    object LyricSettings : Screen()
    object LyricUpgradeManage : Screen()
    object LyricRules : Screen()
    object DeveloperOptions : Screen()
    object AppIcon : Screen()
    object ManageAccounts : Screen()
    object UpdateHistory : Screen()
    data class Comment(val songId: String, val songTitle: String, val qqSongId: Long? = null) : Screen()
}
