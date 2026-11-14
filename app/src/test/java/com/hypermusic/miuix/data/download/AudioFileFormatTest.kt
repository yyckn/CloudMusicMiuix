package com.hypermusic.miuix.data.download

import org.junit.Assert.*
import org.junit.Test

class AudioFileFormatTest {
    @Test fun identifiesErrorDocumentsWithoutRejectingAudioHeaders() {
        fun bytes(value: String) = value.toByteArray(Charsets.UTF_8)
        assertTrue(AudioFileFormat.looksLikeErrorDocument(bytes(" \n{\"error\":1}")))
        assertTrue(AudioFileFormat.looksLikeErrorDocument(bytes("<!DOCTYPE html><html>error")))
        assertTrue(AudioFileFormat.looksLikeErrorDocument(bytes("<?xml version=\"1.0\"?>")))
        assertFalse(AudioFileFormat.looksLikeErrorDocument(bytes("ID3audio")))
        assertFalse(AudioFileFormat.looksLikeErrorDocument(byteArrayOf(0xff.toByte(), 0xf1.toByte())))
    }
    @Test fun detectsSupportedContainerHeaders() {
        fun bytes(value: String) = value.toByteArray(Charsets.US_ASCII)
        assertEquals("flac", AudioFileFormat.detect(bytes("fLaCmetadata")))
        assertEquals("mp3", AudioFileFormat.detect(bytes("ID3metadata")))
        assertEquals("mp3", AudioFileFormat.detect(byteArrayOf(0xff.toByte(), 0xfb.toByte())))
        assertEquals("aac", AudioFileFormat.detect(byteArrayOf(0xff.toByte(), 0xf1.toByte())))
        assertEquals("m4a", AudioFileFormat.detect(bytes("0000ftypM4A ")))
        assertEquals("ogg", AudioFileFormat.detect(bytes("OggSdata")))
        assertEquals("wav", AudioFileFormat.detect(bytes("RIFF0000WAVE")))
        assertNull(AudioFileFormat.detect(bytes("unknown")))
    }
}
