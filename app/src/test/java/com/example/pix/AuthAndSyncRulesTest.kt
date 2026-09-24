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
    fun serverVersionsAndTombstonesDriveRemoteApplication() {
        assertEquals(
            RemoteAction.DELETE,
            ConflictPolicy.applyRemote(
                remoteDeleted = true,
                remoteVersion = 20,
                appliedVersion = 10,
                pendingOperation = "UPSERT",
            ),
        )
        assertEquals(
            RemoteAction.SKIP,
            ConflictPolicy.applyRemote(
                remoteDeleted = false,
                remoteVersion = 20,
                appliedVersion = 20,
                pendingOperation = null,
            ),
        )
        assertEquals(
            RemoteAction.SKIP,
            ConflictPolicy.applyRemote(
                remoteDeleted = false,
                remoteVersion = 21,
                appliedVersion = 20,
                pendingOperation = "DELETE",
            ),
        )
        assertEquals(
            RemoteAction.UPSERT,
            ConflictPolicy.applyRemote(
                remoteDeleted = false,
                remoteVersion = 21,
                appliedVersion = 20,
                pendingOperation = null,
            ),
        )
    }
}
