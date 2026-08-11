package com.cloudmusic.miuix.ui.pages

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.cloudmusic.miuix.data.model.Track
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.BasicComponentDefaults
import top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.utils.overScrollVertical

@Composable
fun PlaylistPage(
    tracks: List<Track>,
    currentTrack: Track?,
    onTrackClick: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = MiuixScrollBehavior()

    Scaffold(
        topBar = {
            TopAppBar(
                title = "歌单",
                largeTitle = "歌单",
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false
            )
        }
    ) { paddingValues ->
        if (tracks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = paddingValues.calculateTopPadding()),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无歌曲",
                    color = MiuixTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .overScrollVertical()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = paddingValues.calculateTopPadding() + 8.dp,
                    bottom = 8.dp
                )
            ) {
                items(tracks) { track ->
                    TrackItem(
                        track = track,
                        isPlaying = track.id == currentTrack?.id,
                        onClick = { onTrackClick(track) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackItem(
    track: Track,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    BasicComponent(
        modifier = modifier.fillMaxWidth(),
        title = track.title,
        titleColor = if (isPlaying) {
            BasicComponentDefaults.titleColor(color = MiuixTheme.colorScheme.primary)
        } else {
            BasicComponentDefaults.titleColor()
        },
        summary = "${track.artist} - ${track.album}",
        summaryColor = if (isPlaying) {
            BasicComponentDefaults.summaryColor(color = MiuixTheme.colorScheme.primary.copy(alpha = 0.7f))
        } else {
            BasicComponentDefaults.summaryColor()
        },
        onClick = onClick
    )
}
