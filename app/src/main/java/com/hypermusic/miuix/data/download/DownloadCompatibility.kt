package com.hypermusic.miuix.data.download

import com.hypermusic.miuix.data.model.Track

/** Gson does not run Kotlin default arguments when reading records from older app versions. */
internal fun Track.withStorageDefaults(): Track = Track(
    id = id.orEmpty(), title = title.orEmpty(), artist = artist.orEmpty(), artistId = artistId.orEmpty(),
    album = album.orEmpty(), albumId = albumId.orEmpty(), source = source.orEmpty().ifBlank { "netease" },
    sourceUrl = sourceUrl.orEmpty(), imgUrl = imgUrl.orEmpty(), url = url, duration = duration,
    isVip = isVip, artistNames = artistNames.orEmpty().filterNotNull(), artistIds = artistIds.orEmpty().filterNotNull(),
    isCloudDisk = isCloudDisk, qqMediaMid = qqMediaMid.orEmpty(), qqSongId = qqSongId,
    bodianFreeSign = bodianFreeSign.orEmpty(), kugouHash = kugouHash.orEmpty(),
    kugouAlbumAudioId = kugouAlbumAudioId.orEmpty(), localDownloadId = localDownloadId
)

internal fun DownloadEntry.withStorageDefaults(): DownloadEntry = DownloadEntry(
    id = id.orEmpty(), track = track.withStorageDefaults(), qualityIndex = qualityIndex, accountId = accountId,
    state = state.orEmpty().ifBlank { if (path.orEmpty().isNotBlank()) "complete" else "failed" },
    bytes = bytes, total = total, path = path.orEmpty(), quality = quality.orEmpty(), message = message.orEmpty(),
    allowMobile = allowMobile, waitForWifi = waitForWifi, bitrate = bitrate, format = format.orEmpty(), replaces = replaces.orEmpty()
)
