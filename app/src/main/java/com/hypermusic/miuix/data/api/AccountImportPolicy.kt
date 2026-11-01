package com.hypermusic.miuix.data.api

/** Shared policy for importing an account without disturbing unrelated saved accounts. */
internal object AccountImportPolicy {
    fun requiresChoice(hasStoredAccount: Boolean, multiAccountEnabled: Boolean): Boolean =
        hasStoredAccount && !multiAccountEnabled

    fun <T, K> merge(existing: List<T>, incoming: T, replace: Boolean, key: (T) -> K): List<T> {
        if (replace) return listOf(incoming)
        val result = existing.toMutableList()
        val index = result.indexOfFirst { key(it) == key(incoming) }
        if (index >= 0) result[index] = incoming else result.add(incoming)
        return result
    }
}
