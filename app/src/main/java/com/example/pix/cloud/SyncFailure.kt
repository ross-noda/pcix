package com.example.pix.cloud

import org.json.JSONObject

/** Sanitized diagnostics: no server message, URL, payload, account ID or token is retained. */
class SyncHttpFailure(val stage: String, val statusCode: Int, val serverCode: String?) : Exception("$stage HTTP $statusCode [$serverCode]") {
    val status: CloudSyncStatus get() = when {
        statusCode == 404 || serverCode in setOf("PGRST202", "PGRST205", "42P01", "42883", "42703") -> CloudSyncStatus.SchemaMissing
        statusCode == 401 -> CloudSyncStatus.SessionExpired
        statusCode == 403 || serverCode == "42501" -> CloudSyncStatus.Forbidden
        statusCode == 408 || statusCode == 429 || statusCode >= 500 -> CloudSyncStatus.Error
        else -> CloudSyncStatus.InvalidData
    }
    companion object {
        fun from(stage: String, response: CloudHttp.Response): SyncHttpFailure {
            val code = runCatching { JSONObject(response.body).optString("code") }.getOrNull()
                ?.takeIf { it.matches(Regex("[A-Z0-9_]{2,20}")) }
            return SyncHttpFailure(stage, response.code, code)
        }
    }
}

object SyncRetryPolicy {
    fun blocked(status: CloudSyncStatus) = status in setOf(CloudSyncStatus.SchemaMissing,
        CloudSyncStatus.Forbidden, CloudSyncStatus.InvalidData, CloudSyncStatus.SessionExpired, CloudSyncStatus.Unconfigured)
    fun retry(status: CloudSyncStatus, attempt: Int) = !blocked(status) && attempt < 5
}
