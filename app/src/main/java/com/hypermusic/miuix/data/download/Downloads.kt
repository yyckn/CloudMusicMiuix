package com.hypermusic.miuix.data.download

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hypermusic.miuix.data.model.Track
import java.util.UUID

/** 不保存临时播放链接或登录凭证，任务重试时重新获取。 */
data class DownloadEntry(
    val id: String, val track: Track, val qualityIndex: Int, val accountId: String?,
    val state: String = "queued", val bytes: Long = 0, val total: Long = -1,
    val path: String = "", val quality: String = "", val message: String = "",
    val allowMobile: Boolean = false, val waitForWifi: Boolean = false
)

object Downloads {
    private val gson = Gson()
    private lateinit var context: Context
    var entries by mutableStateOf<List<DownloadEntry>>(emptyList())
        private set
    var showSheet by mutableStateOf(false)
    var networkConfirmation by mutableStateOf<String?>(null)
        private set
    private val prefs get() = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    @Synchronized fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        entries = runCatching {
            gson.fromJson<List<DownloadEntry>>(prefs.getString("entries", "[]"), object : TypeToken<List<DownloadEntry>>() {}.type).orEmpty()
        }.getOrDefault(emptyList())
        networkConfirmation = entries.firstOrNull { it.state == "waiting_confirmation" }?.id
    }
    @Synchronized private fun save() { prefs.edit().putString("entries", gson.toJson(entries)).commit() }
    @Synchronized fun update(id: String, transform: (DownloadEntry) -> DownloadEntry) {
        if (entries.none { it.id == id }) return
        entries = entries.map { if (it.id == id) transform(it) else it }
        save()
    }
    @Synchronized fun finish(id: String, path: String, size: Long, message: String): Boolean {
        if (entries.none { it.id == id }) return false
        entries = entries.map { if (it.id == id) it.copy(state = "complete", path = path, bytes = size, message = message) else it }
        save()
        return true
    }
    fun enqueue(ctx: Context, track: Track, accountId: String?) {
        init(ctx)
        val key = when (track.source) { "qqmusic" -> "quality_qq"; "bodian" -> "quality_bodian"; "kugou" -> "quality_kugou"; "qishui" -> "quality_qishui"; else -> "quality_netease" }
        val configured = prefs.getInt(key, -1)
        val playbackPrefs = context.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val platform = com.hypermusic.miuix.data.api.MusicPlatform.fromTrackSource(track.source)
            ?: com.hypermusic.miuix.data.api.MusicPlatform.NETEASE
        val playbackQuality = com.hypermusic.miuix.data.model.PlaybackQualityPreferences.resolve(playbackPrefs, platform)
        val actualAccountId = if (track.source == "bodian") {
            val client = com.hypermusic.miuix.data.api.BodianClient(context)
            com.hypermusic.miuix.data.api.BodianAuth(context, client).currentAccount(playback = true)?.uid
        } else if (track.source == "kugou") {
            val client = com.hypermusic.miuix.data.api.KugouClient(context)
            com.hypermusic.miuix.data.api.KugouAuth(context, client).account(playback = true)?.userId
        } else if (track.source == "qishui") {
            val client = com.hypermusic.miuix.data.api.QishuiClient(context)
            com.hypermusic.miuix.data.api.QishuiAuth(context, client).account(playback = true)?.userId
        } else accountId
        val entry = DownloadEntry(UUID.randomUUID().toString(), track, resolveDownloadQuality(configured, playbackQuality), actualAccountId)
        synchronized(this) { entries = listOf(entry) + entries; save() }
        showSheet = true
        if (shouldWarnOnCurrentNetwork()) requireNetworkDecision(entry.id) else schedule(entry)
    }
    fun isWifi(): Boolean {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        return manager.getNetworkCapabilities(manager.activeNetwork)?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
    }
    fun shouldWarnOnCurrentNetwork(): Boolean {
        val manager = context.getSystemService(android.net.ConnectivityManager::class.java)
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork)
        return shouldPromptForDownload(prefs.getBoolean("warn_mobile", true),
            capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true,
            capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true)
    }
    @Synchronized fun requireNetworkDecision(id: String) {
        if (entries.none { it.id == id }) return
        entries = entries.map { if (it.id == id) it.copy(state = "waiting_confirmation", message = "等待确认网络") else it }
        save()
        if (networkConfirmation == null) networkConfirmation = id
    }
    fun confirmNetwork(waitForWifi: Boolean) {
        val id = networkConfirmation ?: return
        val old = synchronized(this) {
            val entry = entries.find { it.id == id }
            entries = entries.filterNot { it.id == id }
            networkConfirmation = entries.firstOrNull { it.state == "waiting_confirmation" }?.id
            entry
        } ?: return
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id))
        val next = old.copy(id = UUID.randomUUID().toString(), state = "queued", bytes = 0, total = -1,
            message = if (waitForWifi) "等待 Wi-Fi" else "", waitForWifi = waitForWifi, allowMobile = !waitForWifi)
        synchronized(this) { entries = listOf(next) + entries; save() }
        schedule(next)
    }
    private fun schedule(entry: DownloadEntry) {
        val constraints = Constraints.Builder()
        if (entry.waitForWifi) {
            val wifi = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            constraints.setRequiredNetworkRequest(wifi, NetworkType.CONNECTED)
        } else constraints.setRequiredNetworkType(NetworkType.CONNECTED)
        val request = OneTimeWorkRequestBuilder<DownloadWorker>().setId(UUID.fromString(entry.id))
            .setInputData(workDataOf("id" to entry.id)).setConstraints(constraints.build()).build()
        WorkManager.getInstance(context).enqueue(request)
    }
    fun retry(id: String) {
        val old = entries.find { it.id == id } ?: return
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id))
        val next = old.copy(id = UUID.randomUUID().toString(), state = "queued", bytes = 0, total = -1, path = "", message = "", allowMobile = false, waitForWifi = false)
        synchronized(this) { entries = entries.filterNot { it.id == id } + next; save() }
        if (shouldWarnOnCurrentNetwork()) requireNetworkDecision(next.id) else schedule(next)
    }
    fun remove(id: String) {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id))
        val old = synchronized(this) {
            val found = entries.find { it.id == id }
            entries = entries.filterNot { it.id == id }
            if (networkConfirmation == id) networkConfirmation = entries.firstOrNull { it.state == "waiting_confirmation" }?.id
            save()
            found
        }
        old?.path?.takeIf { it.isNotBlank() }?.let { java.io.File(it).delete() }
        context.filesDir.resolve("downloads/$id.part").delete()
    }
}
