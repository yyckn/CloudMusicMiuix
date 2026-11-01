package com.hypermusic.miuix.data.api

import org.junit.Assert.*
import org.junit.Test

class AccountImportPolicyTest {
    private data class Account(val id: String, val token: String)
    @Test fun conflictDependsOnTargetPlatformAccountAndMultiAccountSetting() {
        assertFalse(AccountImportPolicy.requiresChoice(false, false))
        assertFalse(AccountImportPolicy.requiresChoice(false, true))
        assertTrue(AccountImportPolicy.requiresChoice(true, false))
        assertFalse(AccountImportPolicy.requiresChoice(true, true))
    }
    @Test fun addingAndUpdatingPreserveAccountOrderAndOtherAccounts() {
        val old = listOf(Account("a", "old-a"), Account("b", "old-b"))
        assertEquals(listOf(Account("a", "old-a"), Account("b", "new-b")),
            AccountImportPolicy.merge(old, Account("b", "new-b"), false) { it.id })
        assertEquals(old + Account("c", "new-c"), AccountImportPolicy.merge(old, Account("c", "new-c"), false) { it.id })
        assertEquals(listOf(Account("c", "new-c")), AccountImportPolicy.merge(old, Account("c", "new-c"), true) { it.id })
        assertEquals(listOf(Account("a", "old-a"), Account("b", "old-b")), old)
    }
}
