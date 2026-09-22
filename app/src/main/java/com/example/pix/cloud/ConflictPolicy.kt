package com.example.pix.cloud

object ConflictPolicy {
    /**
     * Last-write-wins among live updates using client `updatedAt` millis. A tombstone always wins:
     * deleted records are never resurrected by an older or equal update.
     */
    fun applyRemote(
        remoteDeleted: Boolean,
        remoteUpdated: Long,
        localUpdated: Long?,
        pendingDelete: Boolean,
        pendingUpsertUpdated: Long?,
    ): RemoteAction {
        if (pendingDelete) return RemoteAction.SKIP
        if (remoteDeleted) return RemoteAction.DELETE
        val pending = pendingUpsertUpdated
        if (pending != null && pending > remoteUpdated) return RemoteAction.SKIP
        if (localUpdated != null && localUpdated > remoteUpdated) return RemoteAction.SKIP
        return RemoteAction.UPSERT
    }
}

enum class RemoteAction {
    UPSERT,
    DELETE,
    SKIP,
}
