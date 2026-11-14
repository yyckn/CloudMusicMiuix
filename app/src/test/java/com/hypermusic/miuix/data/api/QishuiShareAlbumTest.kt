package com.hypermusic.miuix.data.api

import org.junit.Assert.*
import org.junit.Test

class QishuiShareAlbumTest {
    @Test fun parsesPcAlbumDetailAsFallback() {
        val root = com.google.gson.JsonParser.parseString("""{"album_info":{"id":"123","name":"专辑","count_tracks":1},"tracks":[{"id":"456","name":"歌曲","duration":2000}]}""").asJsonObject
        val (album, tracks) = QishuiShareAlbum.parsePcAlbum(root, 123L)
        assertEquals("专辑", album.name)
        assertEquals("qishui_456", tracks.single().id)
        assertTrue(runCatching { QishuiShareAlbum.parsePcAlbum(root, 999L) }.isFailure)
    }
    @Test fun parsesArtistPublicAlbumsAndChecksIdentity() {
        val html = """<script>window._ROUTER_DATA = {"loaderData":{"artist_page":{"artistInfo":{"id":"456"},"albumList":[{"id":"789","name":"专辑","count_tracks":10,"artists":[{"id":"456","name":"歌手"}]}]}}};</script>"""
        val albums = QishuiShareAlbum.parseArtistAlbums(html, "456")
        assertEquals(789L, albums.single().id)
        assertEquals("歌手", albums.single().artist)
        assertTrue(runCatching { QishuiShareAlbum.parseArtistAlbums(html, "123") }.isFailure)
    }
    @Test fun parsesAlbumSearchAndCursor() {
        val json = com.google.gson.JsonParser.parseString("""{"result_groups":[{"id":"albums","has_more":true,"next_cursor":"20","data":[{"entity":{"album":{"id":"7145673767388186662","name":"专辑","count_tracks":10,"release_date":1499990400,"artists":[{"name":"歌手"}]}}}]}]}""").asJsonObject
        val result = QishuiShareAlbum.parseSearch(json, 0)
        assertEquals("7145673767388186662", result.items.single().id)
        assertEquals("歌手", result.items.single().creator)
        assertEquals("2017-07-14", result.items.single().date)
        assertEquals(20, result.nextOffset)
        assertTrue(result.hasMore)
        val rejected = com.google.gson.JsonParser.parseString("""{"status_code":10007,"result_groups":[]}""").asJsonObject
        assertTrue(runCatching { QishuiShareAlbum.parseSearch(rejected, 0) }.isFailure)
    }
    @Test fun parsesPublicAlbumAndTracksFromRouterData() {
        val html = """<script>window._ROUTER_DATA = {"loaderData":{"album_page":{"albumInfo":{"id":"7145673767388186662","name":"专辑","count_tracks":1,"release_date":1499990400,"artists":[{"id":"456","name":"歌手"}],"url_cover":{"urls":["https://example.com/"],"uri":"cover","template_prefix":"tpl"}},"trackList":[{"id":"789","name":"歌曲","duration":120000,"artists":[{"id":"456","name":"歌手"}]}]}}};</script>"""
        val (album, tracks) = QishuiShareAlbum.parse(html, 7145673767388186662L)
        assertEquals("专辑", album.name)
        assertEquals(1499990400000L, album.publishTime)
        assertEquals("qishui_789", tracks.single().id)
        assertEquals("7145673767388186662", tracks.single().albumId)
        assertEquals(120000L, tracks.single().duration)
        assertTrue(runCatching { QishuiShareAlbum.parse(html, 123L) }.isFailure)
    }
}
