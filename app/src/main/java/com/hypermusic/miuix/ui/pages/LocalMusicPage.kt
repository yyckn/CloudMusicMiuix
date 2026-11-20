package com.hypermusic.miuix.ui.pages

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.hypermusic.miuix.data.download.Downloads
import com.hypermusic.miuix.data.download.OfflineLibrary
import com.hypermusic.miuix.data.download.groupLocalDownloads
import com.hypermusic.miuix.data.download.downloadSongKey
import com.hypermusic.miuix.data.download.qualityLabel
import com.hypermusic.miuix.data.download.comparisonSize
import com.hypermusic.miuix.data.model.*
import com.hypermusic.miuix.viewmodel.MusicPlayerViewModel

/** Reuses the playlist's rows, search, more sheet and scroll behavior, with local file actions. */
@Composable
internal fun LocalMusicPage(viewModel: MusicPlayerViewModel, onBack: () -> Unit,
    onArtist: (String) -> Unit, onAlbum: (Long, Boolean) -> Unit, onComment: (Track) -> Unit,
    sharedTransitionScope: SharedTransitionScope, animatedVisibilityScope: AnimatedVisibilityScope,
    modifier: Modifier = Modifier) {
    val entries = OfflineLibrary.available()
    val groups = groupLocalDownloads(entries).associateBy { it.first().track.downloadSongKey() }
    val context = androidx.compose.ui.platform.LocalContext.current
    val tracks = groups.values.map { OfflineLibrary.localTrack(it.first()) }
    val latest = remember(entries) { OfflineLibrary.latest(entries) }
    val cover = latest?.let { OfflineLibrary.cover(it)?.absolutePath ?: it.track.imgUrl }.orEmpty()
    val detail = PlaylistDetail(Playlist("local_downloads", "本地音乐", cover, "", tracks.size), tracks)
    PlaylistDetailPage(playlistDetail = detail, isLoading = false, error = null,
        currentTrack = viewModel.playerState.currentTrack, initialTitle = "本地音乐", isLocalMusic = true,
        sharedCoverKey = com.hypermusic.miuix.ui.components.playlistCoverKey("local_downloads"),
        onShowDownloads = { Downloads.showSheet = true }, onBackClick = onBack,
        trackQuality = { groups[it.downloadSongKey()]?.firstOrNull()?.qualityLabel() },
        trackFileSize = { track -> groups[track.downloadSongKey()]?.firstOrNull()?.comparisonSize()?.let {
            android.text.format.Formatter.formatShortFileSize(context, it)
        } },
        trackVersionCount = { groups[it.downloadSongKey()]?.size ?: 1 },
        resolveTrackForMore = { track, open -> Downloads.requestLocalVersion(track) { open(OfflineLibrary.localTrack(it)) } },
        onTrackClick = { viewModel.playTrackWithMode(it, tracks) },
        onPlayAllClick = { songs -> songs.firstOrNull()?.let { viewModel.playTrackWithMode(it, songs) } },
        onShuffleClick = { viewModel.shufflePlaylist(it) },
        onCheckTrackLiked = { if (OfflineLibrary.hasNetwork()) viewModel.trackLiked(it) else Result.failure(Exception("当前无网络")) },
        onSetTrackLiked = { track, liked -> viewModel.setTrackLiked(track, liked) },
        onShare = { track -> context.startActivity(android.content.Intent.createChooser(android.content.Intent(android.content.Intent.ACTION_SEND)
            .setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, "${track.title} - ${track.artist}\n${track.sourceUrl}"), "分享歌曲")) },
        onNavigateToArtist = onArtist,
        onNavigateToTrackAlbum = { track, id -> onAlbum(id, track.source == "qqmusic") },
        onNavigateToComment = { id, _ -> tracks.firstOrNull { it.id == id }?.let(onComment) },
        sharedTransitionScope = sharedTransitionScope, animatedVisibilityScope = animatedVisibilityScope, modifier = modifier)
}
