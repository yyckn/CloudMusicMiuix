package com.hypermusic.miuix.data.download

/** Recognize containers from bytes, rather than assuming the requested quality determines a file format. */
internal object AudioFileFormat {
    /** Some media endpoints return an error document with HTTP 200 and a binary content type. */
    fun looksLikeErrorDocument(bytes: ByteArray): Boolean {
        val text = bytes.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
        return text.startsWith("{") || text.startsWith("[") || text.startsWith("<!doctype html", ignoreCase = true) ||
            text.startsWith("<html", ignoreCase = true) || text.startsWith("<?xml", ignoreCase = true)
    }

    fun detect(bytes: ByteArray): String? {
        fun starts(value: String, offset: Int = 0): Boolean {
            val signature = value.toByteArray(Charsets.US_ASCII)
            return bytes.size >= offset + signature.size && signature.indices.all { bytes[offset + it] == signature[it] }
        }
        if (starts("fLaC")) return "flac"
        if (starts("OggS")) return "ogg"
        if (starts("RIFF") && starts("WAVE", 8)) return "wav"
        if (starts("ftyp", 4)) return "m4a"
        if (starts("ID3")) return "mp3"
        if (bytes.size >= 2 && bytes[0].toInt() and 0xff == 0xff) {
            val second = bytes[1].toInt() and 0xff
            if (second and 0xf6 == 0xf0) return "aac"
            if (second and 0xe0 == 0xe0 && second and 0x06 != 0) return "mp3"
        }
        return null
    }
}
