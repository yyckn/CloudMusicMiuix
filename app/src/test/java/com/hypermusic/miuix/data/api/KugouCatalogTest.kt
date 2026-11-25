package com.hypermusic.miuix.data.api

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import com.hypermusic.miuix.data.model.SearchCategory

class KugouCatalogTest {
    @Test fun playlistRetainsCreatorIdentifiersForCollection() {
        val data = JsonParser.parseString("""{"count":0,"list_info":{"name":"歌单","global_collection_id":"collection_3_1002717860_518_0","list_create_userid":1002717860,"list_create_listid":518,"listid":518},"songs":[]}""").asJsonObject
        val info = KugouCatalog.playlistInfo("collection_3_1002717860_518_0", KugouCatalog.parsePlaylist(data, 0))
        assertEquals("1002717860", info.kugouCreatorId)
        assertEquals("518", info.kugouOriginalListId)
        assertEquals("collection_3_1002717860_518_0", info.kugouCreatorGid)
    }
    @Test fun albumUsesNestedAuthorIdForCollection() {
        val row = JsonParser.parseString("""{"album_id":123,"album_name":"专辑","authors":[{"base":{"author_id":456}}]}""").asJsonObject
        val album = KugouCatalog.parseAlbum(row, 2)
        assertEquals(456L, album.artistId)
        assertEquals("专辑", album.name)
    }
    @Test fun songMetadataAndPlaybackIdentityArePreserved() {
        val row = JsonParser.parseString("""{"FileName":"歌手 - 歌名","OriSongName":"歌名","SingerName":"歌手","FileHash":"1f7e7659b9fff4fb6f71562a61bbc25f","MixSongID":"7351234567890123456","AlbumID":"958909","AlbumName":"专辑","Duration":299,"PayType":3,"Image":"http://imge.kugou.com/stdmusic/{size}/cover.jpg","Singers":[{"name":"歌手","id":3520}]}""").asJsonObject
        val track = KugouCatalog.parseTrack(row)
        assertEquals("kugou_7351234567890123456", track.id)
        assertEquals("1F7E7659B9FFF4FB6F71562A61BBC25F", track.platform.kugouHash)
        assertEquals("7351234567890123456", track.platform.kugouAlbumAudioId)
        assertEquals(299000L, track.duration)
        assertEquals("https://imge.kugou.com/stdmusic/400/cover.jpg", track.imgUrl)
        assertEquals(listOf("歌手"), track.artistNames)
        assertTrue(track.isVip)
    }

    @Test fun invalidIdentityIsRejected() {
        assertTrue(runCatching { KugouCatalog.parseTrack(JsonParser.parseString("""{"FileHash":"invalid"}""").asJsonObject) }.isFailure)
        val alternative = KugouCatalog.parseTrack(JsonParser.parseString("""{"MixSongID":"0","album_audio_id":"32029511"}""").asJsonObject)
        assertEquals("kugou_32029511", alternative.id)
    }
    @Test fun songArtistNamesStayAlignedWithTheirIds() {
        val row = JsonParser.parseString("""{"hash":"1F7E7659B9FFF4FB6F71562A61BBC25F","singerinfo":[{"name":"","id":10},{"name":"歌手","id":20}]}""").asJsonObject
        val track = KugouCatalog.parseTrack(row)
        assertEquals(listOf("歌手"), track.artistNames)
        assertEquals(listOf("20"), track.artistIds)
        assertEquals("20", track.artistId)
        val fallback = JsonParser.parseString("""{"hash":"1F7E7659B9FFF4FB6F71562A61BBC25F","author_name":"歌手","author_id":30}""").asJsonObject
        assertEquals("30", KugouCatalog.parseTrack(fallback).artistId)
    }
    @Test fun playlistUsesMillisecondDurationAndGlobalMetadata() {
        val data = JsonParser.parseString("""{"count":2,"list_info":{"name":"收藏"},"songs":[{"hash":"a6049aab184a305caa906c6bbb8e8233","name":"歌手 - 歌曲","mixsongid":64531905,"timelen":258142,"album_id":"978031","albuminfo":{"name":"专辑"},"singerinfo":[{"name":"歌手","id":3520}],"download":[{"pay_type":3}],"cover":"http://imge.kugou.com/{size}/c.jpg"}]}""").asJsonObject
        val page = KugouCatalog.parsePlaylist(data, 0)
        val track = page.songs.items.single()
        assertEquals("歌曲", track.title)
        assertEquals(258142L, track.duration)
        assertEquals("专辑", track.album)
        assertTrue(track.isVip)
        assertTrue(page.songs.hasMore)
    }
    @Test fun searchPlaylistUsesGlobalIdForDetail() {
        val row = JsonParser.parseString("""{"gid":"collection_3_1_2_0","specialid":42,"specialname":"歌单","nickname":"作者","song_count":8,"total_play_count":12,"img":"http://imge.kugou.com/{size}/c.jpg"}""").asJsonObject
        val result = KugouCatalog.parseSearchEntity(row, SearchCategory.PLAYLIST)!!
        assertEquals("collection_3_1_2_0", result.id)
        assertEquals("作者", result.creator)
        assertEquals(8L, result.songCount)
        assertEquals(12L, result.playCount)
    }
    @Test fun albumSongUsesNestedAudioMetadata() {
        val row = JsonParser.parseString("""{"base":{"album_id":958909,"author_name":"周杰伦","audio_name":"夜曲","album_audio_id":32029511},"audio_info":{"hash":"16C8AB298231370293D16BCF9E5FF9B6","duration":226873},"album_info":{"album_name":"十一月的萧邦","cover":"http://imge.kugou.com/{size}/a.jpg"},"authors":[{"author_name":"周杰伦","author_id":3520}]}""").asJsonObject
        val track = KugouCatalog.parseAlbumTrack(row)
        assertEquals("kugou_32029511", track.id)
        assertEquals("夜曲", track.title)
        assertEquals(226873L, track.duration)
        assertEquals("https://imge.kugou.com/400/a.jpg", track.imgUrl)
        assertEquals("3520", track.artistId)
    }
    @Test fun albumSongWithoutHashRetainsAudioIdForPlaybackLookup() {
        val row = JsonParser.parseString("""{"base":{"album_audio_id":32029511,"audio_name":"歌曲"},"audio_info":{"duration":226873}}""").asJsonObject
        val track = KugouCatalog.parseAlbumTrack(row)
        assertEquals("kugou_32029511", track.id)
        assertEquals("32029511", track.platform.kugouAlbumAudioId)
        assertEquals("", track.platform.kugouHash)
        assertEquals("kugou_32029511", KugouCatalog.parseAlbumTrack(JsonParser.parseString("""{"base":{"album_audio_id":32029511}}""").asJsonObject).id)
        assertTrue(runCatching { KugouCatalog.parseAlbumTrack(JsonParser.parseString("""{"base":{},"audio_info":{}}""").asJsonObject) }.isFailure)
    }
    @Test fun artistAndArtistSongFieldsAreMapped() {
        val artist = KugouCatalog.parseArtist(JsonParser.parseString("""{"author_id":3520,"author_name":"周杰伦","sizable_avatar":"http://singerimg.kugou.com/{size}/a.jpg","song_count":1861,"album_count":49,"long_intro":"介绍"}""").asJsonObject)
        assertEquals(3520L, artist.id)
        assertEquals("https://singerimg.kugou.com/400/a.jpg", artist.picUrl)
        assertEquals(1861, artist.musicSize)
        val song = KugouCatalog.parseTrack(JsonParser.parseString("""{"hash":"1F7E7659B9FFF4FB6F71562A61BBC25F","audio_name":"发如雪","author_name":"周杰伦","album_name":"十一月的萧邦","album_id":958909,"album_audio_id":32029513,"timelength":299284,"pay_type":3}""").asJsonObject)
        assertEquals("发如雪", song.title)
        assertEquals(299284L, song.duration)
        assertTrue(song.isVip)
    }
}
