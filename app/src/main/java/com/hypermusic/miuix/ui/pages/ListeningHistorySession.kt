package com.hypermusic.miuix.ui.pages

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.*
import com.hypermusic.miuix.data.history.*
import java.time.LocalDate

/** ViewModel-owned page state survives opening a song, artist or album and navigating back. */
class ListeningHistorySession {
    private val accounts = mutableMapOf<String, Account>()
    fun account(id: String): Account = accounts.getOrPut(id) { Account() }

    class Account {
        var tab by mutableIntStateOf(0)
        val offsets = mutableStateListOf(0, 0, 0, 0)
        val listStates = List(4) { LazyListState() }
        var records by mutableStateOf<List<ListeningRecord>>(emptyList())
        var cloudRecords by mutableStateOf<List<CloudListeningEvent>>(emptyList())
        var timeline by mutableStateOf<List<ListeningTimelineItem>>(emptyList())
        var revision by mutableLongStateOf(-1L)
        var reading by mutableStateOf(true)
        private val periods = linkedMapOf<String, Period>()
        fun period(type: String, start: LocalDate, end: LocalDate): Period {
            val key = "$type|$start|$end"
            val state = periods.remove(key) ?: Period()
            periods[key] = state
            // Older periods remain on disk; keep only a bounded working set in memory.
            if (periods.size > 12) periods.remove(periods.keys.first())
            return state
        }
    }

    class Period {
        var snapshot by mutableStateOf<ListeningCloudSnapshot?>(null)
        var error by mutableStateOf<String?>(null)
        var loading by mutableStateOf(false)
        var attempted by mutableStateOf(false)
    }
}
