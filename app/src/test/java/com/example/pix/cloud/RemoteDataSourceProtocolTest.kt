package com.example.pix.cloud

import com.example.pix.data.SyncOutboxEntity
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RemoteDataSourceProtocolTest {
    private val config = CloudConfig("https://example.test", "anon", "")

    @Test
    fun pushUsesMutationRpcAndParsesDeterministicAck() = runBlocking {
        val http = FakeHttp()
        http.responses +=
            CloudHttp.Response(
                200,
                """{"mutation_id":"m1","entity_type":"tasks","entity_id":"t1","entity_id2":null,"outcome":"APPLIED","server_version":7,"deleted":false}""",
                emptyMap(),
            )
        val remote = RemoteDataSource(config, http)
        val row =
            SyncOutboxEntity(
                id = "m1",
                entityType = "tasks",
                entityId = "t1",
                operation = OutboxRecorder.UPSERT,
                payload = JSONObject().put("id", "t1").put("title", "x").toString(),
            )

        val ack = remote.push("token", row)

        assertEquals(7L, ack.serverVersion)
        assertFalse(ack.deleted)
        assertEquals("/rest/v1/rpc/pcix_apply_mutation", http.lastPath)
        assertEquals("handling=strict", http.lastExtra["Prefer"])
        val body = JSONObject(requireNotNull(http.lastBody))
        assertEquals("m1", body.getString("p_mutation_id"))
        assertEquals("UPSERT", body.getString("p_operation"))
    }

    @Test
    fun emptyAccountSnapshotMayBeZero() = runBlocking {
        val http = FakeHttp()
        http.responses += CloudHttp.Response(200, """{"through":0}""", emptyMap())
        assertEquals(0L, RemoteDataSource(config, http).snapshot("token"))
    }

    @Test
    fun pullUsesStableMonotonicVersions() = runBlocking {
        val http = FakeHttp()
        http.responses +=
            CloudHttp.Response(
                200,
                """[
                  {"server_version":11,"entity_type":"tasks","entity_id":"a","entity_id2":null,"operation":"UPSERT","payload":{"id":"a","title":"A"}},
                  {"server_version":12,"entity_type":"tasks","entity_id":"b","entity_id2":null,"operation":"DELETE","payload":null}
                ]""",
                emptyMap(),
            )
        val page = RemoteDataSource(config, http).pull("token", after = 10, through = 20, limit = 2)
        assertEquals(listOf(11L, 12L), page.rows.map { it.serverVersion })
        assertEquals(12L, page.nextCursor)
        assertTrue(page.more)
        val body = JSONObject(requireNotNull(http.lastBody))
        assertEquals(10L, body.getLong("p_after"))
        assertEquals(20L, body.getLong("p_through"))
    }

    @Test
    fun malformedOrNonMonotonicRemotePageFailsClosed() = runBlocking {
        val missingPayload = FakeHttp().apply {
            responses +=
                CloudHttp.Response(
                    200,
                    """[{"server_version":2,"entity_type":"tasks","entity_id":"a","operation":"UPSERT","payload":null}]""",
                    emptyMap(),
                )
        }
        assertThrows(RemoteDataSource.ProtocolError::class.java) {
            runBlocking { RemoteDataSource(config, missingPayload).pull("token", 1, 10, 200) }
        }

        val duplicateVersion = FakeHttp().apply {
            responses +=
                CloudHttp.Response(
                    200,
                    """[
                    {"server_version":2,"entity_type":"tasks","entity_id":"a","operation":"DELETE"},
                    {"server_version":2,"entity_type":"tags","entity_id":"b","operation":"DELETE"}
                    ]""",
                    emptyMap(),
                )
        }
        assertThrows(RemoteDataSource.ProtocolError::class.java) {
            runBlocking { RemoteDataSource(config, duplicateVersion).pull("token", 1, 10, 200) }
        }
    }

    @Test
    fun protectedEndpoint401IsPropagated() {
        val http = FakeHttp().apply {
            responses += CloudHttp.Response(401, "{}", emptyMap())
        }
        assertThrows(RemoteDataSource.Unauthorized::class.java) {
            runBlocking { RemoteDataSource(config, http).snapshot("expired") }
        }
    }

    private class FakeHttp : CloudHttp(CloudConfig("https://example.test", "anon", "")) {
        val responses = ArrayDeque<CloudHttp.Response>()
        var lastPath: String? = null
        var lastBody: String? = null
        var lastExtra: Map<String, String> = emptyMap()

        override fun request(
            method: String,
            path: String,
            accessToken: String?,
            body: String?,
            extra: Map<String, String>,
        ): CloudHttp.Response {
            lastPath = path
            lastBody = body
            lastExtra = extra
            return responses.removeFirst()
        }
    }
}
