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
    val allowMobile: Boolean = false, val waitForWifi: Boolean = false,
    val bitrate: Int = 0, val format: String = "", val replaces: List<String> = emptyList()
)

data class DuplicateDownloadRequest(val proposed: DownloadEntry)
data class LocalVersionRequest(val id: String, val track: Track, val onSelected: (DownloadEntry) -> Unit)

object Downloads {
    private val gson = Gson()
    private lateinit var context: Context
    var entries by mutableStateOf<List<DownloadEntry>>(emptyList())
        private set
    var showSheet by mutableStateOf(false)
    var networkConfirmation by mutableStateOf<String?>(null)
        private set
    private var duplicateRequests by mutableStateOf<List<DuplicateDownloadRequest>>(emptyList())
    val duplicateConfirmation get() = duplicateRequests.firstOrNull()
    var localVersionRequest by mutableStateOf<LocalVersionRequest?>(null)
        private set
    private val prefs get() = context.getSharedPreferences("downloads", Context.MODE_PRIVATE)

    @Synchronized fun init(ctx: Context) {
        if (::context.isInitialized) return
        context = ctx.applicationContext
        OfflineLibrary.init(context)
        entries = runCatching {
            gson.fromJson<List<DownloadEntry>>(prefs.getString("entries", "[]"), object : TypeToken<List<DownloadEntry>>() {}.type).orEmpty()
                .mapNotNull { runCatching { it.withStorageDefaults() }.getOrNull() }
        }.getOrDefault(emptyList())
        networkConfirmation = entries.firstOrNull { it.state == "waiting_confirmation" }?.id
        save()
        if (entries.any { it.state == "complete" && OfflineLibrary.needsRepair(it) }) OfflineMetadataWorker.schedule(context)
    }
    @Synchronized private fun save() { prefs.edit().putString("entries", gson.toJson(entries)).commit() }
    @Synchronized fun update(id: String, transform: (DownloadEntry) -> DownloadEntry) {
        if (entries.none { it.id == id }) return
        entries = entries.map { if (it.id == id) transform(it) else it }
        save()
    }
    @Synchronized fun finish(id: String, path: String, size: Long, message: String): Boolean {
        val entry = entries.find { it.id == id } ?: return false
        entries = entries.map { if (it.id == id) it.copy(state = "complete", path = path, bytes = size, message = message, replaces = emptyList()) else it }
        save()
        entry.replaces.orEmpty().filter { it != id }.forEach(::remove)
        return true
    }
    fun enqueue(ctx: Context, track: Track, accountId: String?) {
        submitPrepared(prepare(ctx, track, accountId))
    }
    fun prepare(ctx: Context, track: Track, accountId: String?): DownloadEntry {
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
        return DownloadEntry(UUID.randomUUID().toString(), track, resolveDownloadQuality(configured, playbackQuality), actualAccountId)
    }
    fun prepare(ctx: Context, track: Track): DownloadEntry {
        val playbackPrefs = ctx.getSharedPreferences("player_prefs", Context.MODE_PRIVATE)
        val accountId = if (track.source == "qqmusic") com.hypermusic.miuix.data.api.QqMusicApi.playbackAccountId else {
            val index = playbackPrefs.getInt(if (track.isCloudDisk) "data_account_index" else "playback_account_index", 0)
            com.hypermusic.miuix.data.api.NeteaseApi.getAccounts().getOrNull(index)?.userId
        }
        return prepare(ctx, track, accountId)
    }
    fun submitPrepared(entry: DownloadEntry) {
        synchronized(this) {
            if (entries.any { sameDownloadSong(it.track, entry.track) }) {
                if (duplicateRequests.none { sameDownloadSong(it.proposed.track, entry.track) }) duplicateRequests += DuplicateDownloadRequest(entry)
                return
            }
            entries = listOf(entry) + entries; save()
        }
        showSheet = true
        if (shouldWarnOnCurrentNetwork()) requireNetworkDecision(entry.id) else schedule(entry)
    }
    fun submitBatchPrepared(proposed: List<DownloadEntry>) {
        if (proposed.isEmpty()) return
        val warn = shouldWarnOnCurrentNetwork()
        val accepted = synchronized(this) {
            val (conflicts, fresh) = partitionDownloadConflicts(proposed, entries)
            conflicts.forEach { entry ->
                if (duplicateRequests.none { sameDownloadSong(it.proposed.track, entry.track) })
                    duplicateRequests += DuplicateDownloadRequest(entry)
            }
            val added = fresh.map { if (warn) it.copy(state = "waiting_confirmation", message = "等待确认网络") else it }
            entries = added + entries
            if (warn && networkConfirmation == null) networkConfirmation = added.firstOrNull()?.id
            if (added.isNotEmpty()) save()
            added
        }
        if (accepted.isNotEmpty()) showSheet = true
        if (!warn && accepted.isNotEmpty()) WorkManager.getInstance(context).enqueue(accepted.map(::workRequest))
    }
    @Synchronized fun cancelDuplicateDownload(expectedId: String? = null) {
        if (expectedId == null || duplicateConfirmation?.proposed?.id == expectedId) duplicateRequests = duplicateRequests.drop(1)
    }
    fun confirmDuplicateDownload(expectedId: String? = null) {
        val request = duplicateConfirmation ?: return
        if (expectedId != null && request.proposed.id != expectedId) return
        synchronized(this) { duplicateRequests = duplicateRequests.filterNot { it.proposed.id == request.proposed.id } }
        replacePrepared(request.proposed)
    }
    fun replacePrepared(proposed: DownloadEntry) {
        val matches = entries.filter { sameDownloadSong(it.track, proposed.track) }
        // Keep completed audio available until its replacement finishes successfully.
        val replacement = proposed.copy(replaces = matches.filter { it.state == "complete" }.map { it.id })
        matches.filter { it.state != "complete" }.forEach { remove(it.id) }
        synchronized(this) {
            entries = listOf(replacement) + entries; save()
        }
        showSheet = true
        if (shouldWarnOnCurrentNetwork()) requireNetworkDecision(replacement.id) else schedule(replacement)
    }
    fun requestLocalVersion(track: Track, onSelected: (DownloadEntry) -> Unit) {
        val versions = OfflineLibrary.versions(track)
        if (versions.size <= 1) { versions.firstOrNull()?.let(onSelected); return }
        localVersionRequest = LocalVersionRequest(UUID.randomUUID().toString(), track, onSelected)
    }
    fun dismissLocalVersion() { localVersionRequest = null }
    fun keepLocalVersion(id: String) {
        val request = localVersionRequest ?: return
        val versions = OfflineLibrary.versions(request.track)
        val chosen = versions.find { it.id == id } ?: return
        versions.filter { it.id != id }.forEach { remove(it.id, preservePath = chosen.path) }
        localVersionRequest = null
        request.onSelected(chosen)
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
    private fun workRequest(entry: DownloadEntry): androidx.work.OneTimeWorkRequest {
        val constraints = Constraints.Builder()
        if (entry.waitForWifi) {
            val wifi = android.net.NetworkRequest.Builder()
                .addTransportType(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
            constraints.setRequiredNetworkRequest(wifi, NetworkType.CONNECTED)
        } else constraints.setRequiredNetworkType(NetworkType.CONNECTED)
        return OneTimeWorkRequestBuilder<DownloadWorker>().setId(UUID.fromString(entry.id))
            .setInputData(workDataOf("id" to entry.id)).setConstraints(constraints.build()).build()
    }
    private fun schedule(entry: DownloadEntry) { WorkManager.getInstance(context).enqueue(workRequest(entry)) }
    fun retry(id: String) {
        val old = entries.find { it.id == id } ?: return
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id))
        val next = old.copy(id = UUID.randomUUID().toString(), state = "queued", bytes = 0, total = -1, path = "", message = "", allowMobile = false, waitForWifi = false)
        synchronized(this) { entries = entries.filterNot { it.id == id } + next; save() }
        if (shouldWarnOnCurrentNetwork()) requireNetworkDecision(next.id) else schedule(next)
    }
    fun remove(id: String, preservePath: String? = null) {
        WorkManager.getInstance(context).cancelWorkById(UUID.fromString(id))
        val old = synchronized(this) {
            val found = entries.find { it.id == id }
            entries = entries.filterNot { it.id == id }
            if (networkConfirmation == id) networkConfirmation = entries.firstOrNull { it.state == "waiting_confirmation" }?.id
            save()
            found
        }
        old?.path?.takeIf { it.isNotBlank() && it != preservePath }?.let { java.io.File(it).delete() }
        OfflineLibrary.remove(id)
        context.filesDir.resolve("downloads/$id.part").delete()
    }
}
