package com.hypermusic.miuix.data.diagnostics

import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.io.Writer
import java.io.InputStream
import java.io.ByteArrayOutputStream

/** Bounded, synchronous storage: the crash handler must not depend on coroutines or the UI. */
internal class CrashReportStore(private val directory: File) {
    @Synchronized fun save(id: String, text: String, trace: ByteArray? = null) {
        require(id.matches(Regex("[a-zA-Z0-9-]+")))
        check(directory.isDirectory || directory.mkdirs())
        val file = directory.resolve("$id.log")
        if (file.isFile) return
        if (trace != null) directory.resolve("$id.trace").writeBytes(trace.copyOf(minOf(trace.size, MAX_TRACE_BYTES)))
        FileOutputStream(file).use { output ->
            output.write(redactDiagnosticText(text).take(MAX_REPORT_CHARS).toByteArray(Charsets.UTF_8))
            output.fd.sync()
        }
        reports().drop(MAX_REPORTS).forEach { old -> old.delete(); directory.resolve("${old.nameWithoutExtension}.trace").delete() }
    }
    @Synchronized fun reports(): List<File> = directory.listFiles().orEmpty()
        .filter { it.isFile && it.extension == "log" }.sortedByDescending { it.name }
    fun read(file: File): String = file.inputStream().use { it.readDiagnosticBytes(MAX_REPORT_CHARS * 4).toString(Charsets.UTF_8) }
    fun attachments(file: File): List<File> = listOf(file, directory.resolve("${file.nameWithoutExtension}.trace")).filter { it.isFile }
    @Synchronized fun clear() { directory.listFiles().orEmpty().filter { it.extension in setOf("log", "trace") }.forEach { it.delete() } }
    companion object {
        const val MAX_REPORTS = 10
        const val MAX_REPORT_CHARS = 128 * 1024
        const val MAX_TRACE_BYTES = 512 * 1024
    }
}

internal fun InputStream.readDiagnosticBytes(limit: Int): ByteArray {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var remaining = limit
    while (remaining > 0) {
        val count = read(buffer, 0, minOf(buffer.size, remaining))
        if (count < 0) break
        if (count == 0) continue
        output.write(buffer, 0, count)
        remaining -= count
    }
    return output.toByteArray()
}

internal fun redactDiagnosticText(text: String): String = text
    .replace(Regex("(?i)(MUSIC_U|__csrf|musickey|music_key|access_token|refresh_token|authorization|cookie)([\\s\"'=：:]+)[^\\s\",;]+"), "$1$2[已隐藏]")

internal fun diagnosticStackTrace(error: Throwable): String {
    val output = StringBuilder()
    val writer = object : Writer() {
        override fun write(chars: CharArray, offset: Int, count: Int) {
            val remaining = CrashReportStore.MAX_REPORT_CHARS - output.length
            if (remaining > 0) output.append(chars, offset, minOf(count, remaining))
        }
        override fun flush() {}
        override fun close() {}
    }
    error.printStackTrace(PrintWriter(writer))
    return output.toString()
}
