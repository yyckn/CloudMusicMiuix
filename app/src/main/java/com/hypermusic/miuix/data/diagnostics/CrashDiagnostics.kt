package com.hypermusic.miuix.data.diagnostics

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import com.hypermusic.miuix.BuildConfig
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

object CrashDiagnostics {
    private val installed = AtomicBoolean(false)
    private val handling = AtomicBoolean(false)
    private val fields = ConcurrentHashMap<String, String>()
    private val writer = Executors.newSingleThreadExecutor { task -> Thread(task, "CrashDiagnostics").apply { isDaemon = true } }
    @Volatile private var snapshot = "尚未进入播放页"
    private lateinit var app: Context
    internal lateinit var store: CrashReportStore
        private set
    private val snapshotFile get() = File(app.filesDir, "crash_reports/last-session.txt")
    private val preferences get() = app.getSharedPreferences("crash_diagnostics", Context.MODE_PRIVATE)

    fun install(context: Context) {
        if (!installed.compareAndSet(false, true)) return
        app = context.applicationContext ?: context
        store = CrashReportStore(File(app.filesDir, "crash_reports"))
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                if (handling.compareAndSet(false, true)) {
                    val now = System.currentTimeMillis()
                    store.save("$now-java-${android.os.Process.myPid()}", buildString {
                        appendLine("Java / Kotlin 未捕获异常")
                        appendLine(header(now))
                        appendLine("线程：${thread.name}（${thread.id}）")
                        appendLine("【播放与歌词现场】\n$snapshot")
                        appendLine("【异常堆栈】")
                        append(diagnosticStackTrace(error))
                    })
                }
            } catch (_: Throwable) {
                // Attempt a minimal report if formatting the full report failed, e.g. during OOM.
                try { store.save("${System.currentTimeMillis()}-minimal-${android.os.Process.myPid()}",
                    "未捕获异常（简要记录）\n${error.javaClass.name}\n线程：${thread.name}\n$snapshot") } catch (_: Throwable) {}
            } finally {
                if (previous != null) previous.uncaughtException(thread, error)
                else { android.os.Process.killProcess(android.os.Process.myPid()); kotlin.system.exitProcess(10) }
            }
        }
        writer.execute {
            runCatching {
                val lastSession = snapshotFile.takeIf { it.isFile }?.readText().orEmpty()
                collectSystemExits(lastSession)
            }
        }
    }

    fun clearReports() {
        // Do not re-import cleared historical system exits after restarting the app.
        preferences.edit().putLong("system_exit_after", System.currentTimeMillis()).commit()
        store.clear()
    }

    fun update(section: String, details: String) {
        if (!installed.get()) return
        fields[section] = details.take(4096)
        snapshot = fields.toSortedMap().entries.joinToString("\n") { "${it.key}：${it.value}" }
        val current = snapshot
        writer.execute {
            runCatching {
                snapshotFile.parentFile?.mkdirs()
                snapshotFile.writeText(redactDiagnosticText(current).take(16 * 1024))
                if (Build.VERSION.SDK_INT >= 30) app.getSystemService(ActivityManager::class.java)
                    .setProcessStateSummary("v${BuildConfig.VERSION_NAME}; ${current.take(30)}".toByteArray().take(128).toByteArray())
            }
        }
    }

    private fun header(time: Long) = buildString {
        appendLine("记录时间：${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", java.util.Locale.ROOT).format(java.util.Date(time))}")
        appendLine("采集版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})，${app.packageName}")
        appendLine("设备：${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        appendLine("系统：Android ${Build.VERSION.RELEASE} / API ${Build.VERSION.SDK_INT}，${Build.SUPPORTED_ABIS.joinToString()}")
        val runtime = Runtime.getRuntime()
        appendLine("当前 Java 内存：已用 ${(runtime.totalMemory() - runtime.freeMemory()) / 1024} KB，上限 ${runtime.maxMemory() / 1024} KB")
    }

    private fun collectSystemExits(lastSession: String) {
        if (Build.VERSION.SDK_INT < 30) return
        val after = preferences.getLong("system_exit_after", 0)
        val exits = app.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(app.packageName, 0, 20)
        val lastExitTime = exits.maxOfOrNull { it.timestamp }
        exits.filter { it.timestamp > after && it.reason in setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_CRASH_NATIVE,
            ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE) }.forEach { exit ->
            runCatching {
                val reason = when (exit.reason) {
                    ApplicationExitInfo.REASON_CRASH -> "系统记录：应用异常退出"
                    ApplicationExitInfo.REASON_CRASH_NATIVE -> "系统记录：原生崩溃"
                    ApplicationExitInfo.REASON_ANR -> "系统记录：应用无响应（ANR）"
                    ApplicationExitInfo.REASON_LOW_MEMORY -> "系统记录：低内存终止"
                    else -> "系统记录：资源占用终止"
                }
                val trace = runCatching { exit.traceInputStream?.use { it.readDiagnosticBytes(CrashReportStore.MAX_TRACE_BYTES) } }.getOrNull()
                store.save("${exit.timestamp}-system-${exit.pid}", buildString {
                    appendLine(reason)
                    appendLine(header(exit.timestamp))
                    appendLine("进程：${exit.processName}，PID ${exit.pid}，原因 ${exit.reason}，状态 ${exit.status}")
                    appendLine("退出时内存：PSS ${exit.pss} KB，RSS ${exit.rss} KB")
                    appendLine("系统描述：${exit.description.orEmpty()}")
                    exit.processStateSummary?.let { appendLine("系统保存的运行摘要：${it.toString(Charsets.UTF_8)}") }
                    if (exit.timestamp == lastExitTime && lastSession.isNotBlank()) appendLine("【上次运行最后现场】\n$lastSession")
                    if (trace != null && exit.reason == ApplicationExitInfo.REASON_ANR) appendLine("【系统 ANR 堆栈】\n${trace.toString(Charsets.UTF_8)}")
                    else appendLine(if (trace != null) "系统原始追踪已附在分享文件中；原生追踪可能为二进制格式。" else "系统未提供堆栈，请结合 Java/Kotlin 日志和退出原因定位。")
                }, trace)
            }
        }
        if (lastExitTime != null && lastExitTime > after) preferences.edit().putLong("system_exit_after", lastExitTime).commit()
    }
}
