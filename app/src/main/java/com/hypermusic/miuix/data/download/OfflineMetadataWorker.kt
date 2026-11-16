package com.hypermusic.miuix.data.download

import android.content.Context
import androidx.work.*
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.api.QqMusicApi
import com.hypermusic.miuix.data.lyrics.*
import com.hypermusic.miuix.ui.theme.ThemeSettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Local repairs run immediately; missing online assets wait for a connection across app restarts. */
class OfflineMetadataWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    companion object {
        fun schedule(context: Context) {
            val local = OneTimeWorkRequestBuilder<OfflineMetadataWorker>().setInputData(workDataOf("network" to false)).build()
            val remote = OneTimeWorkRequestBuilder<OfflineMetadataWorker>()
                .setInputData(workDataOf("network" to true))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build()
            WorkManager.getInstance(context).beginUniqueWork("offline_download_metadata", ExistingWorkPolicy.KEEP, local).then(remote).enqueue()
        }
    }
    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Downloads.init(applicationContext)
        val network = inputData.getBoolean("network", false)
        if (network) {
            if (QqMusicApi.accounts.isEmpty()) QqMusicApi.init(applicationContext)
            if (NeteaseApi.getAccounts().isEmpty()) NeteaseApi.init(applicationContext)
            ThemeSettings.init(applicationContext)
            LyricMatches.init(applicationContext)
            LyricExclusionRules.init(applicationContext)
            BodianLyricsApi.init(applicationContext)
            KugouLyricsApi.init(applicationContext)
            QishuiLyricsApi.init(applicationContext)
        }
        var incomplete = false
        for (entry in OfflineLibrary.available()) {
            if (!OfflineLibrary.needsRepair(entry)) continue
            try {
                OfflineLibrary.upgradeLegacy(entry, allowNetwork = network)
                val latest = Downloads.entries.find { it.id == entry.id }
                if (latest != null && OfflineLibrary.needsRepair(latest)) incomplete = true
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) {
                incomplete = true
                Downloads.update(entry.id) { it.copy(message = "离线资料补全失败：${error.message ?: "请稍后重试"}") }
            }
        }
        if (network && incomplete && runAttemptCount < 3) Result.retry() else Result.success()
    }
}
