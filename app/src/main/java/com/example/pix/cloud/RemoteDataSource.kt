package com.example.pix.cloud

import com.example.pix.data.SyncOutboxEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Remote protocol boundary used by SyncEngine and replaceable by deterministic fakes in tests. */
interface SyncRemote {
    fun configured(): Boolean

    suspend fun push(token: String, row: SyncOutboxEntity): PushAck

    suspend fun snapshot(token: String): Long

    suspend fun pull(token: String, after: Long, through: Long, limit: Int = 200): PullPage
}

data class PushAck(
    val mutationId: String,
    val entityType: String,
    val entityId: String,
    val entityId2: String?,
    val outcome: String,
    val serverVersion: Long,
    val deleted: Boolean,
)

data class RemoteChange(
    val serverVersion: Long,
    val entityType: String,
    val entityId: String,
    val entityId2: String?,
    val operation: String,
    val payload: JSONObject?,
) {
    val deleted: Boolean
        get() = operation == OutboxRecorder.DELETE
}

data class PullPage(val rows: List<RemoteChange>, val nextCursor: Long, val more: Boolean)

class RemoteDataSource(private val config: CloudConfig, private val http: CloudHttp) : SyncRemote {
    override fun configured() = config.configured

    /**
     * A 2xx alone is deliberately insufficient. pcix_apply_mutation returns a durable receipt keyed
     * by outbox.id (mutation id), including the authoritative server_version and whether the canonical
     * identity is tombstoned. Identical retries return the same receipt.
     */
    override suspend fun push(token: String, row: SyncOutboxEntity): PushAck = withContext(Dispatchers.IO) {
        val (id, id2) = splitIdentity(row.entityType, row.entityId)
        val body =
            JSONObject()
                .put("p_entity", row.entityType)
                .put("p_operation", row.operation)
                .put("p_id", id)
                .put("p_id2", id2 ?: JSONObject.NULL)
                .put(
                    "p_payload",
                    if (row.operation == OutboxRecorder.UPSERT) JSONObject(row.payload)
                    else JSONObject.NULL,
                )
                .put("p_mutation_id", row.id)
        val response =
            http.request(
                "POST",
                "/rest/v1/rpc/pcix_apply_mutation",
                token,
                body.toString(),
                mapOf("Prefer" to "handling=strict"),
            )
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("push ${row.entityType} ${response.code}")
        val json = runCatching { JSONObject(response.body) }.getOrElse { throw ProtocolError("invalid push ack") }
        return@withContext PushAck(
            mutationId = json.requireString("mutation_id"),
            entityType = json.requireString("entity_type"),
            entityId = json.requireString("entity_id"),
            entityId2 = json.optionalString("entity_id2"),
            outcome = json.requireString("outcome"),
            serverVersion = json.requirePositiveLong("server_version"),
            deleted = json.getBoolean("deleted"),
        )
    }

    /** Reserves a high-water mark. Mutations committed after it necessarily get a greater version. */
    override suspend fun snapshot(token: String): Long = withContext(Dispatchers.IO) {
        val response = http.request("POST", "/rest/v1/rpc/pcix_sync_snapshot", token, "{}")
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("snapshot ${response.code}")
        val json = runCatching { JSONObject(response.body) }.getOrElse { throw ProtocolError("invalid snapshot") }
        return@withContext json.requireNonNegativeLong("through")
    }

    /** Stable keyset page over one account-wide change stream. No OFFSET and no timestamp ties. */
    override suspend fun pull(token: String, after: Long, through: Long, limit: Int): PullPage = withContext(Dispatchers.IO) {
        require(limit in 1..500)
        require(after >= 0 && through >= after)
        val body =
            JSONObject()
                .put("p_after", after)
                .put("p_through", through)
                .put("p_limit", limit)
        val response =
            http.request("POST", "/rest/v1/rpc/pcix_pull_changes", token, body.toString())
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("pull ${response.code}")
        val raw = SyncCodec.parseArray(response.body.ifBlank { "[]" })
        var previous = after
        val rows =
            raw.map { json ->
                val version = json.requirePositiveLong("server_version")
                if (version <= previous || version > through) {
                    throw ProtocolError("non-monotonic pull page")
                }
                previous = version
                val operation = json.requireString("operation")
                if (operation != OutboxRecorder.UPSERT && operation != OutboxRecorder.DELETE) {
                    throw ProtocolError("unknown remote operation")
                }
                val payload =
                    if (operation == OutboxRecorder.UPSERT) {
                        json.optJSONObject("payload") ?: throw ProtocolError("missing upsert payload")
                    } else null
                RemoteChange(
                    serverVersion = version,
                    entityType = json.requireString("entity_type"),
                    entityId = json.requireString("entity_id"),
                    entityId2 = json.optionalString("entity_id2"),
                    operation = operation,
                    payload = payload,
                )
            }
        val next = rows.lastOrNull()?.serverVersion ?: after
        return@withContext PullPage(rows, next, rows.size == limit && next < through)
    }

    class Unauthorized : RuntimeException()

    class ProtocolError(message: String) : RuntimeException(message)

    companion object {
        private val supported =
            setOf("lists", "tags", "tasks", "recurring_series", "subtasks", "task_tags", "task_images")

        fun validateEntity(type: String) {
            if (type !in supported) throw ProtocolError("unsupported entity $type")
        }

        private fun splitIdentity(type: String, entityId: String): Pair<String, String?> {
            validateEntity(type)
            if (type != "task_tags") return entityId to null
            val parts = entityId.split('|', limit = 2)
            if (parts.size != 2 || parts.any { it.isBlank() }) throw ProtocolError("invalid task_tags identity")
            return parts[0] to parts[1]
        }
    }
}

private fun JSONObject.requireString(key: String): String =
    if (!has(key) || isNull(key) || getString(key).isBlank()) {
        throw RemoteDataSource.ProtocolError("missing $key")
    } else getString(key)

private fun JSONObject.optionalString(key: String): String? =
    if (!has(key) || isNull(key) || optString(key).isBlank()) null else getString(key)

private fun JSONObject.requirePositiveLong(key: String): Long {
    if (!has(key) || isNull(key)) throw RemoteDataSource.ProtocolError("missing $key")
    val value = getLong(key)
    if (value <= 0) throw RemoteDataSource.ProtocolError("invalid $key")
    return value
}

private fun JSONObject.requireNonNegativeLong(key: String): Long {
    if (!has(key) || isNull(key)) throw RemoteDataSource.ProtocolError("missing $key")
    val value = getLong(key)
    if (value < 0) throw RemoteDataSource.ProtocolError("invalid $key")
    return value
}
