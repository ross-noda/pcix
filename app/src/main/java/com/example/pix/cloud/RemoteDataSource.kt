package com.example.pix.cloud

import org.json.JSONArray
import org.json.JSONObject

class RemoteDataSource(private val config: CloudConfig, private val http: CloudHttp) {
    fun configured() = config.configured

    fun upsert(token: String, table: String, userId: String, payload: JSONObject) {
        payload.put("user_id", userId)
        val response =
            http.request(
                "POST",
                "/rest/v1/$table",
                token,
                JSONArray().put(payload).toString(),
                mapOf("Prefer" to "resolution=merge-duplicates,return=minimal"),
            )
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("upsert $table ${response.code}")
    }

    fun tombstone(token: String, type: String, id: String, id2: String?) {
        val body =
            JSONObject()
                .put("p_entity", type)
                .put("p_id", id)
                .put("p_id2", id2 ?: JSONObject.NULL)
        val response = http.request("POST", "/rest/v1/rpc/pcix_tombstone", token, body.toString())
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("tombstone $type ${response.code}")
    }

    fun pull(token: String, table: String, checkpoint: String?, offset: Int, limit: Int = 200):
        PullPage {
        val filter =
            if (checkpoint.isNullOrBlank()) ""
            else "&synced_at=gt.${java.net.URLEncoder.encode(checkpoint, "UTF-8")}"
        val response =
            http.request(
                "GET",
                "/rest/v1/$table?select=*&order=synced_at.asc&limit=$limit&offset=$offset$filter",
                token,
            )
        if (response.code == 401) throw Unauthorized()
        if (!response.ok) throw IllegalStateException("pull $table ${response.code}")
        val rows = SyncCodec.parseArray(response.body.ifBlank { "[]" })
        val newest = rows.mapNotNull { it.optString("synced_at").takeIf { v -> v.isNotBlank() } }.maxOrNull()
        return PullPage(rows, newest, rows.size == limit)
    }

    class Unauthorized : RuntimeException()

    data class PullPage(val rows: List<JSONObject>, val newest: String?, val more: Boolean)
}
