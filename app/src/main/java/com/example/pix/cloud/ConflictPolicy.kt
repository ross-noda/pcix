package com.example.pix.cloud

/**
 * Local application policy for an authoritative server change stream.
 *
 * Live update/update conflicts are resolved by server commit order (`server_version`). A local
 * pending mutation is kept visible until it is pushed, so a live remote UPSERT is not allowed to
 * overwrite that optimistic state. Tombstones are different: deletion is terminal for the same
 * identity and therefore removes stale local state even when an UPSERT is pending.
 */
object ConflictPolicy {
    fun applyRemote(
        remoteDeleted: Boolean,
        remoteVersion: Long,
        appliedVersion: Long?,
        pendingOperation: String?,
    ): RemoteAction {
        if (appliedVersion != null && appliedVersion >= remoteVersion) return RemoteAction.SKIP
        if (remoteDeleted) return RemoteAction.DELETE
        if (pendingOperation != null) return RemoteAction.SKIP
        return RemoteAction.UPSERT
    }
}

enum class RemoteAction {
    UPSERT,
    DELETE,
    SKIP,
}
