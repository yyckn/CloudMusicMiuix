package com.hypermusic.miuix.data.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.google.gson.Gson
import com.hypermusic.miuix.data.api.NeteaseApi
import com.hypermusic.miuix.data.model.Track
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

data class ListeningRecord(val id: String, val track: Track, val accountId: String,
    val startedAt: Long, val listenedMs: Long, val duration: Long, val eligible: Boolean,
    val finished: Boolean, val uploaded: Boolean, val error: String?, val dayTotals: Map<String, Long> = emptyMap())

/** Durable user data, deliberately stored outside all disposable cache directories. */
object ListeningHistory {
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val attempted = mutableSetOf<String>()
    private val uploadMutex = Mutex()
    internal var reportTransport: suspend (Track, String, Long, Long) -> Result<Unit> = NeteaseApi::reportListening
    private val revision = MutableStateFlow(0L)
    val changes = revision.asStateFlow()
    private lateinit var db: SQLiteDatabase
    private var initialized = false

    @Synchronized fun init(context: Context) {
        if (initialized) return
        val helper = object : SQLiteOpenHelper(context.applicationContext, "listening_history.db", null, 3) {
            override fun onCreate(db: SQLiteDatabase) {
                db.execSQL("CREATE TABLE plays (id TEXT PRIMARY KEY, track TEXT NOT NULL, account TEXT NOT NULL, started INTEGER NOT NULL, listened INTEGER NOT NULL, duration INTEGER NOT NULL, eligible INTEGER NOT NULL, finished INTEGER NOT NULL, uploaded INTEGER NOT NULL DEFAULT 0, error TEXT, days TEXT)")
                db.execSQL("CREATE INDEX plays_time ON plays(started DESC)")
                createCloudTable(db)
            }
            override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
                if (oldVersion < 2) db.execSQL("ALTER TABLE plays ADD COLUMN days TEXT")
                if (oldVersion < 3) createCloudTable(db)
            }
        }
        db = helper.writableDatabase
        initialized = true
        scope.launch {
            // Sessions left open by a killed process retain their last durable checkpoint.
            db.execSQL("UPDATE plays SET finished=1 WHERE finished=0")
            for (write in writes) runCatching { write() }
        }
    }

    private fun createCloudTable(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE cloud_plays (account TEXT NOT NULL, song TEXT NOT NULL, played INTEGER NOT NULL, track TEXT NOT NULL, PRIMARY KEY(account,song,played))")
    }

    suspend fun saveCloud(accountId: String, events: List<CloudListeningEvent>) {
        if (accountId.isBlank() || events.isEmpty()) return
        val done = CompletableDeferred<Unit>()
        writes.send {
            try {
                db.beginTransaction()
                try {
                    events.forEach { event ->
                        val values = ContentValues().apply {
                            put("account", accountId); put("song", event.track.id); put("played", event.playedAt)
                            put("track", gson.toJson(event.track.copy(url = null, localDownloadId = null)))
                        }
                        db.insertWithOnConflict("cloud_plays", null, values, SQLiteDatabase.CONFLICT_REPLACE)
                    }
                    db.setTransactionSuccessful()
                } finally { db.endTransaction() }
                revision.value++
                done.complete(Unit)
            } catch (error: Exception) { done.completeExceptionally(error) }
        }
        done.await()
    }

    suspend fun cloudRecords(accountId: String): List<CloudListeningEvent> = withContext(Dispatchers.IO) {
        if (!initialized || accountId.isBlank()) return@withContext emptyList()
        buildList {
            db.rawQuery("SELECT track,played FROM cloud_plays WHERE account=? ORDER BY played DESC", arrayOf(accountId)).use { cursor ->
                while (cursor.moveToNext()) runCatching { add(CloudListeningEvent(gson.fromJson(cursor.getString(0), Track::class.java), cursor.getLong(1))) }
            }
        }
    }

    fun save(record: ListeningRecord) {
        check(initialized)
        writes.trySend {
            val values = ContentValues().apply {
                put("id", record.id); put("track", gson.toJson(record.track.copy(url = null)))
                put("account", record.accountId); put("started", record.startedAt)
                put("listened", record.listenedMs); put("duration", record.duration)
                put("eligible", if (record.eligible) 1 else 0); put("finished", if (record.finished) 1 else 0)
                put("days", gson.toJson(record.dayTotals))
            }
            if (db.update("plays", values, "id=?", arrayOf(record.id)) == 0)
                db.insertOrThrow("plays", null, values)
            revision.value++
            if (record.finished) scope.launch { uploadPending() }
        }
    }

    suspend fun records(accountId: String? = null): List<ListeningRecord> = withContext(Dispatchers.IO) {
        if (!initialized) return@withContext emptyList()
        buildList {
            val accountFilter = if (accountId != null) " AND account=?" else ""
            db.rawQuery("SELECT * FROM plays WHERE listened>10000$accountFilter ORDER BY started DESC",
                accountId?.let { arrayOf(it) }).use { c ->
                while (c.moveToNext()) runCatching {
                    fun s(key: String) = c.getString(c.getColumnIndexOrThrow(key))
                    fun n(key: String) = c.getLong(c.getColumnIndexOrThrow(key))
                    add(ListeningRecord(s("id"), gson.fromJson(s("track"), Track::class.java), s("account"),
                        n("started"), n("listened"), n("duration"), n("eligible") == 1L,
                        n("finished") == 1L, n("uploaded") == 1L, s("error"),
                        s("days")?.let { gson.fromJson<Map<String, Long>>(it, object : com.google.gson.reflect.TypeToken<Map<String, Long>>() {}.type) }.orEmpty()))
                }
            }
        }
    }

    /** Called once after accounts have been initialized at application entry. */
    fun retryAtLaunch() {
        if (initialized) writes.trySend { scope.launch { uploadPending() } }
    }

    private suspend fun uploadPending() = uploadMutex.withLock {
        val pending = buildList {
            db.rawQuery("SELECT id,track,account,listened,started FROM plays WHERE eligible=1 AND finished=1 AND uploaded=0 ORDER BY started", null).use { c ->
                while (c.moveToNext()) add(arrayOf(c.getString(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4)))
            }
        }
        for (row in pending) {
            val id = row[0]
            if (!attempted.add(id)) continue
            val result = reportTransport(gson.fromJson(row[1], Track::class.java), row[2], row[3].toLong(), row[4].toLong())
            val values = ContentValues().apply {
                put("uploaded", if (result.isSuccess) 1 else 0)
                if (result.isSuccess) putNull("error") else put("error", result.exceptionOrNull()?.message ?: "上传失败")
            }
            db.update("plays", values, "id=?", arrayOf(id)); revision.value++
        }
    }

    fun newId(): String = UUID.randomUUID().toString()

    internal suspend fun awaitWrites() {
        val done = CompletableDeferred<Unit>()
        writes.send { done.complete(Unit) }
        done.await()
    }

    internal suspend fun deleteForTest(ids: List<String>) {
        val done = CompletableDeferred<Unit>()
        writes.send { ids.forEach { db.delete("plays", "id=?", arrayOf(it)) }; revision.value++; done.complete(Unit) }
        done.await()
    }
}
