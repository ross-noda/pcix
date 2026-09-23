package com.example.pix.cloud

sealed class LogoutOutcome {
    data object Completed : LogoutOutcome()
    data class NeedsPendingConfirmation(val pending: Int) : LogoutOutcome()
    data class Failed(val cause: Throwable) : LogoutOutcome()
}

sealed class DeleteOutcome {
    data object Completed : DeleteOutcome()
    data class Failed(val cause: Throwable) : DeleteOutcome()
}

/**
 * Coordinates destructive account transitions. No local wipe is allowed until data is either
 * synchronized or protected by an internal account snapshot.
 */
class AccountLifecycleManager(
    private val accounts: AccountDataStore,
    private val syncNow: suspend () -> Boolean,
    private val signOutLocal: suspend () -> Unit,
    private val deleteRemote: suspend () -> Result<DeleteAccountConfirmation>,
    private val beforeLocalClear: suspend () -> Unit = {},
) {
    suspend fun logout(allowPendingSnapshot: Boolean = false): LogoutOutcome {
        val owner = accounts.owner()
            ?: return try {
                signOutLocal()
                LogoutOutcome.Completed
            } catch (error: Throwable) {
                LogoutOutcome.Failed(error)
            }
        return try {
            val synced = syncNow()
            val pending = accounts.pendingMutationCount()
            if (!synced && pending > 0 && !allowPendingSnapshot) {
                return LogoutOutcome.NeedsPendingConfirmation(pending)
            }
            // Always keep a recovery copy before clearing Room. Besides pending mutations this
            // protects local-only attachments and reminder state that cloud sync cannot recreate.
            accounts.protect(owner)
            beforeLocalClear()
            signOutLocal()
            accounts.wipeUserData()
            accounts.clearGoogle()
            accounts.setOwner(null)
            LogoutOutcome.Completed
        } catch (error: Throwable) {
            LogoutOutcome.Failed(error)
        }
    }

    suspend fun deleteAccount(): DeleteOutcome {
        val owner = accounts.owner()
        return try {
            val confirmation = deleteRemote().getOrElse { return DeleteOutcome.Failed(it) }
            when (confirmation) {
                DeleteAccountConfirmation.Deleted,
                DeleteAccountConfirmation.AlreadyDeleted -> Unit
            }
            // Only an explicit backend confirmation reaches this point.
            beforeLocalClear()
            signOutLocal()
            accounts.wipeUserData()
            accounts.clearGoogle()
            accounts.setOwner(null)
            if (owner != null) accounts.deleteProtected(owner)
            DeleteOutcome.Completed
        } catch (error: Throwable) {
            DeleteOutcome.Failed(error)
        }
    }
}
