package com.example.pix.cloud

import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class CloudHttp(private val config: CloudConfig) {
    private val json = "application/json; charset=utf-8".toMediaType()
    val client: OkHttpClient =
        OkHttpClient.Builder()
            .callTimeout(30, TimeUnit.SECONDS)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()

    data class Response(val code: Int, val body: String, val headers: Map<String, String>) {
        val ok
            get() = code in 200..299
    }

    fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: String? = null,
        extra: Map<String, String> = emptyMap(),
    ): Response {
        if (!config.configured) throw IOException("unconfigured")
        val builder =
            Request.Builder()
                .url(config.origin + path)
                .header("apikey", config.anonKey)
                .header("Accept", "application/json")
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        extra.forEach { (k, v) -> builder.header(k, v) }
        val payload = body?.toRequestBody(json)
        builder.method(method, if (method == "GET" || method == "HEAD") null else payload ?: "".toRequestBody(json))
        client.newCall(builder.build()).execute().use { response ->
            val headers = mutableMapOf<String, String>()
            response.headers.names().forEach { name ->
                response.header(name)?.let { headers[name.lowercase()] = it }
            }
            return Response(response.code, response.body?.string().orEmpty(), headers)
        }
    }

    fun auth(path: String, body: JSONObject, accessToken: String? = null) =
        request("POST", "/auth/v1$path", accessToken ?: config.anonKey, body.toString())
}
