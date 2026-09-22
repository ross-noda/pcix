package com.example.pix

import com.example.pix.cloud.AuthErrors
import com.example.pix.cloud.ConflictPolicy
import com.example.pix.cloud.RemoteAction
import org.junit.Assert.*
import org.junit.Test

class AuthAndSyncRulesTest {
    @Test
    fun mapsCredentialAndOfflineErrors() {
        assertEquals(R.string.auth_offline, AuthErrors.map(0, ""))
        assertEquals(R.string.auth_invalid_credentials, AuthErrors.map(400, "Invalid login credentials"))
        assertEquals(R.string.auth_account_exists, AuthErrors.map(422, "User already registered"))
        assertEquals(R.string.auth_rate_limited, AuthErrors.map(429, "slow"))
        assertEquals(R.string.auth_server, AuthErrors.map(500, "oops"))
    }

    @Test
    fun tombstoneWinsAndSkipsPendingDeletes() {
        assertEquals(
            RemoteAction.DELETE,
            ConflictPolicy.applyRemote(true, 20, 10, pendingDelete = false, pendingUpsertUpdated = null),
        )
        assertEquals(
            RemoteAction.SKIP,
            ConflictPolicy.applyRemote(true, 20, 10, pendingDelete = true, pendingUpsertUpdated = null),
        )
        assertEquals(
            RemoteAction.SKIP,
            ConflictPolicy.applyRemote(false, 5, 10, pendingDelete = false, pendingUpsertUpdated = null),
        )
        assertEquals(
            RemoteAction.UPSERT,
            ConflictPolicy.applyRemote(false, 15, 10, pendingDelete = false, pendingUpsertUpdated = null),
        )
        assertEquals(
            RemoteAction.SKIP,
            ConflictPolicy.applyRemote(false, 15, 10, pendingDelete = false, pendingUpsertUpdated = 20),
        )
    }
}
