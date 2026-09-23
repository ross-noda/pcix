package com.example.pix.cloud

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class AccountLifecycleManagerTest {
    @Test
    fun logoutOnlineSnapshotsThenClears() = runBlocking {
        val store = FakeAccountStore(owner = "A", pending = 2)
        val manager = manager(store, sync = true)
        assertEquals(LogoutOutcome.Completed, manager.logout())
        assertEquals(listOf("protect:A", "clear", "google", "owner:null"), store.events)
        assertTrue(store.signedOut)
    }

    @Test
    fun logoutOfflineWithEmptyOutboxIsSafe() = runBlocking {
        val store = FakeAccountStore(owner = "A", pending = 0)
        val manager = manager(store, sync = false)
        assertEquals(LogoutOutcome.Completed, manager.logout())
        assertTrue(store.protected.contains("A"))
        assertNull(store.owner())
    }

    @Test
    fun logoutOfflineWithPendingOutboxRequiresExplicitDecision() = runBlocking {
        val store = FakeAccountStore(owner = "A", pending = 3)
        val manager = manager(store, sync = false)
        assertEquals(LogoutOutcome.NeedsPendingConfirmation(3), manager.logout())
        assertEquals("A", store.owner())
        assertFalse(store.signedOut)
        assertFalse(store.cleared)

        assertEquals(LogoutOutcome.Completed, manager.logout(allowPendingSnapshot = true))
        assertTrue(store.protected.contains("A"))
        assertTrue(store.cleared)
    }

    @Test
    fun deleteSuccessAllowsLocalWipe() = runBlocking {
        val store = FakeAccountStore(owner = "A")
        val manager = manager(store, delete = Result.success(DeleteAccountConfirmation.Deleted))
        assertEquals(DeleteOutcome.Completed, manager.deleteAccount())
        assertTrue(store.cleared)
        assertNull(store.owner())
    }

    @Test
    fun deleteAlreadyDeletedConfirmationAllowsLocalWipe() = runBlocking {
        val store = FakeAccountStore(owner = "A")
        val manager = manager(store, delete = Result.success(DeleteAccountConfirmation.AlreadyDeleted))
        assertEquals(DeleteOutcome.Completed, manager.deleteAccount())
        assertTrue(store.cleared)
    }

    @Test
    fun deleteFailureNeverWipesLocalData() = runBlocking {
        val store = FakeAccountStore(owner = "A")
        val failure = IllegalStateException("500")
        val manager = manager(store, delete = Result.failure(failure))
        val result = manager.deleteAccount()
        assertTrue(result is DeleteOutcome.Failed)
        assertFalse(store.cleared)
        assertEquals("A", store.owner())
        assertFalse(store.signedOut)
    }

    private fun manager(
        store: FakeAccountStore,
        sync: Boolean = true,
        delete: Result<DeleteAccountConfirmation> = Result.success(DeleteAccountConfirmation.Deleted),
    ) = AccountLifecycleManager(
        accounts = store,
        syncNow = { sync },
        signOutLocal = { store.signedOut = true },
        deleteRemote = { delete },
    )
}

private class FakeAccountStore(
    private var owner: String?,
    var pending: Int = 0,
) : AccountDataStore {
    val events = mutableListOf<String>()
    val protected = mutableSetOf<String>()
    var cleared = false
    var signedOut = false

    override fun owner(): String? = owner
    override fun setOwner(id: String?) { owner = id; events += "owner:$id" }
    override suspend fun hasLegacyData() = false
    override suspend fun wipeUserData() { cleared = true; events += "clear" }
    override suspend fun clearGoogle() { events += "google" }
    override suspend fun pendingMutationCount() = pending
    override suspend fun protect(ownerId: String) { protected += ownerId; events += "protect:$ownerId" }
    override suspend fun restoreProtected(ownerId: String) = protected.contains(ownerId)
    override fun deleteProtected(ownerId: String) { protected -= ownerId }
}
