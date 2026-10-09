package com.example.pix.cloud

import org.junit.Assert.*
import org.junit.Test

class SyncFailureTest {
    @Test fun permanentFailuresNeverEnterAutomaticRetryLoop() {
        for ((http, code, expected) in listOf(
            Triple(404, "PGRST202", CloudSyncStatus.SchemaMissing),
            Triple(400, "42703", CloudSyncStatus.SchemaMissing),
            Triple(403, "42501", CloudSyncStatus.Forbidden),
            Triple(401, "invalid_token", CloudSyncStatus.SessionExpired),
            Triple(400, "23503", CloudSyncStatus.InvalidData))) {
            val error = SyncHttpFailure("push", http, code)
            assertEquals(expected, error.status)
            assertFalse(SyncRetryPolicy.retry(error.status, 0))
        }
    }
    @Test fun legacyServerWithoutHabitSupportRequiresMigrations() {
        for (type in listOf("habit_groups", "habits", "habit_rules", "habit_logs")) {
            val error = SyncHttpFailure.from("push:$type", CloudHttp.Response(400,
                """{"code":"P0001","message":"unknown entity"}""", emptyMap()))
            assertEquals(CloudSyncStatus.SchemaMissing, error.status)
            assertFalse(SyncRetryPolicy.retry(error.status, 0))
            assertFalse(error.message!!.contains("unknown entity"))
        }
    }
    @Test fun unrelatedValidationErrorsAreNotHiddenAsSchemaFailures() {
        for ((stage, message) in listOf("push:habits" to "payload identity mismatch", "push:tasks" to "unknown entity")) {
            val error = SyncHttpFailure.from(stage, CloudHttp.Response(400,
                """{"code":"P0001","message":"$message"}""", emptyMap()))
            assertEquals(CloudSyncStatus.InvalidData, error.status)
        }
    }
    @Test fun temporaryFailuresHaveBoundedRetries() {
        for (http in listOf(408, 429, 500, 503)) {
            val status = SyncHttpFailure("pull", http, null).status
            assertTrue(SyncRetryPolicy.retry(status, 0))
            assertFalse(SyncRetryPolicy.retry(status, 5))
        }
        assertTrue(SyncRetryPolicy.retry(CloudSyncStatus.Offline, 0))
    }
    @Test fun diagnosticsCannotContainServerPayloadOrTokens() {
        val error = SyncHttpFailure.from("snapshot", CloudHttp.Response(404,
            """{"code":"PGRST202","message":"private payload secret-token"}""", emptyMap()))
        assertEquals("PGRST202", error.serverCode)
        assertFalse(error.message!!.contains("secret"))
        val unsafe = SyncHttpFailure.from("snapshot", CloudHttp.Response(400,
            """{"code":"secret / token"}""", emptyMap()))
        assertNull(unsafe.serverCode)
    }
}
