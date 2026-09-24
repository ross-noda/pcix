package com.example.pix.cloud

import android.content.Context
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject


sealed class DeleteAccountConfirmation {
    data object Deleted : DeleteAccountConfirmation()
    data object AlreadyDeleted : DeleteAccountConfirmation()
}

interface AuthSessionController {
    val state: StateFlow<AuthState>
    suspend fun restore()
}

internal interface AuthApi {
    fun auth(path: String, body: JSONObject, accessToken: String? = null): CloudHttp.Response
    fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: String? = null,
    ): CloudHttp.Response
}

private class CloudAuthApi(private val http: CloudHttp) : AuthApi {
    override fun auth(path: String, body: JSONObject, accessToken: String?) =
        http.auth(path, body, accessToken)

    override fun request(method: String, path: String, accessToken: String?, body: String?) =
        http.request(method, path, accessToken, body)
}

class AuthRepository internal constructor(
    private val config: CloudConfig,
    private val api: AuthApi,
    private val store: SessionStore,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    private val newPkceVerifier: () -> String = { generatePkceVerifier() },
) : AuthSessionController {
    constructor(context: Context, config: CloudConfig, http: CloudHttp) :
        this(config, CloudAuthApi(http), SecureSessionStore(context))

    private val mutex = Mutex()
    private val _state = MutableStateFlow<AuthState>(AuthState.Loading)
    override val state: StateFlow<AuthState> = _state

    fun session(): AuthSession? = store.read()

    override suspend fun restore() {
        mutex.withLock {
            val stored = store.read()
            if (stored == null) {
                _state.value = AuthState.Unauthenticated
                return
            }
            if (!stored.expired(nowSeconds())) {
                publish(stored)
                return
            }
            try {
                val refreshed = refreshLocked(stored)
                if (refreshed == null) invalidateLocked()
            } catch (_: IOException) {
                // Keep the known local account available offline; API access remains unavailable
                // until a refresh succeeds.
                publish(stored, offline = true)
            }
        }
    }

    suspend fun signUp(email: String, password: String): Result<Unit> =
        runAuth(setErrorState = true) {
            val response =
                call {
                    api.auth(
                        "/signup",
                        JSONObject().put("email", email.trim()).put("password", password),
                    )
                }
            if (!response.ok) return@runAuth responseFailure(response)
            val json = JSONObject(response.body.ifBlank { "{}" })
            val access = json.opt("access_token") as? String
            val refresh = json.opt("refresh_token") as? String
            if (json.optJSONObject("user") != null && !access.isNullOrBlank() && !refresh.isNullOrBlank()) {
                persist(parseToken(json))
            } else {
                _state.value = AuthState.Unauthenticated
            }
            Result.success(Unit)
        }

    suspend fun signIn(email: String, password: String): Result<Unit> =
        runAuth(setErrorState = true) {
            val response =
                call {
                    api.auth(
                        "/token?grant_type=password",
                        JSONObject().put("email", email.trim()).put("password", password),
                    )
                }
            if (!response.ok) return@runAuth responseFailure(response)
            persist(parseToken(JSONObject(response.body)))
            Result.success(Unit)
        }

    suspend fun signInGoogle(idToken: String, nonce: String): Result<Unit> =
        runAuth(setErrorState = true) {
            val response =
                call {
                    api.auth(
                        "/token?grant_type=id_token",
                        JSONObject()
                            .put("provider", "google")
                            .put("id_token", idToken)
                            .put("nonce", nonce),
                    )
                }
            if (!response.ok) return@runAuth responseFailure(response)
            persist(parseToken(JSONObject(response.body)))
            Result.success(Unit)
        }

    suspend fun recover(email: String): Result<Unit> =
        runAuth(setErrorState = true) {
            val verifier = newPkceVerifier()
            store.writeRecoveryVerifier(verifier)
            val response =
                call {
                    api.auth(
                        "/recover?redirect_to=${URLEncoder.encode(RECOVERY_REDIRECT, "UTF-8")}",
                        JSONObject()
                            .put("email", email.trim())
                            .put("code_challenge", pkceChallenge(verifier))
                            .put("code_challenge_method", "s256"),
                    )
                }
            if (!response.ok) {
                store.clearRecoveryVerifier()
                return@runAuth responseFailure(response)
            }
            Result.success(Unit)
        }

    suspend fun handleDeeplink(fragmentOrQuery: String): Result<Unit> =
        runAuth(setErrorState = true) {
            if (!isRecoveryRedirect(fragmentOrQuery)) {
                return@runAuth invalidRecovery("unexpected redirect")
            }
            val params = parseParams(fragmentOrQuery)
            val remoteError = params["error_description"] ?: params["error"]
            if (!remoteError.isNullOrBlank()) {
                store.clearRecoveryVerifier()
                return@runAuth Result.failure(AuthException(com.example.pix.R.string.auth_generic, remoteError))
            }

            // Current mobile flow: PKCE keeps access/refresh tokens out of the deep link.
            params["code"]?.takeIf { it.isNotBlank() }?.let { code ->
                val verifier =
                    store.recoveryVerifier()
                        ?: return@runAuth invalidRecovery("missing PKCE verifier")
                val tokenResponse =
                    call {
                        api.auth(
                            "/token?grant_type=pkce",
                            JSONObject()
                                .put("auth_code", code)
                                .put("code_verifier", verifier),
                        )
                    }
                if (!tokenResponse.ok) {
                    if (tokenResponse.code in 400..499) store.clearRecoveryVerifier()
                    return@runAuth responseFailure(tokenResponse)
                }
                val recoverySession =
                    parseToken(JSONObject(tokenResponse.body)).copy(recoveryPending = true)
                store.clearRecoveryVerifier()
                persist(recoverySession)
                return@runAuth Result.success(Unit)
            }

            store.clearRecoveryVerifier()
            return@runAuth invalidRecovery("missing PKCE auth code")
        }

    suspend fun updatePassword(
        newPassword: String,
        currentPassword: String? = null,
        nonce: String? = null,
    ): Result<Unit> {
        if (newPassword.length < 6) {
            return Result.failure(AuthException(com.example.pix.R.string.auth_weak_password))
        }
        var token = accessToken()
            ?: return Result.failure(noTokenFailure())
        val payload =
            JSONObject().put("password", newPassword).apply {
                currentPassword?.takeIf { it.isNotBlank() }?.let { put("current_password", it) }
                nonce?.takeIf { it.isNotBlank() }?.let { put("nonce", it) }
            }
        return try {
            var response =
                call {
                    api.request(
                        "PUT",
                        "/auth/v1/user",
                        token,
                        payload.toString(),
                    )
                }
            if (response.code == 401) {
                token = recoverFromUnauthorized()
                    ?: return Result.failure(
                        AuthException(com.example.pix.R.string.auth_session_expired)
                    )
                response =
                    call {
                        api.request(
                            "PUT",
                            "/auth/v1/user",
                            token,
                            payload.toString(),
                        )
                    }
            }
            if (response.code == 401) {
                invalidateSession()
                return Result.failure(AuthException(com.example.pix.R.string.auth_session_expired))
            }
            if (!response.ok) return responseFailure(response, setState = false)
            mutex.withLock {
                val stored = store.read()
                if (stored == null) {
                    _state.value = AuthState.Unauthenticated
                    return@withLock
                }
                val completed = stored.copy(recoveryPending = false)
                store.write(completed)
                _state.value = AuthState.Authenticated(completed.user)
            }
            Result.success(Unit)
        } catch (_: IOException) {
            Result.failure(AuthException(com.example.pix.R.string.auth_offline))
        }
    }

    suspend fun requestPasswordReauthentication(): Result<Unit> {
        var token = accessToken() ?: return Result.failure(noTokenFailure())
        return try {
            var response = call { api.request("POST", "/auth/v1/reauthenticate", token, "{}") }
            if (response.code == 401) {
                token =
                    recoverFromUnauthorized()
                        ?: return Result.failure(
                            AuthException(com.example.pix.R.string.auth_session_expired)
                        )
                response = call { api.request("POST", "/auth/v1/reauthenticate", token, "{}") }
            }
            if (response.code == 401) {
                invalidateSession()
                return Result.failure(AuthException(com.example.pix.R.string.auth_session_expired))
            }
            if (!response.ok) return responseFailure(response, setState = false)
            Result.success(Unit)
        } catch (_: IOException) {
            Result.failure(AuthException(com.example.pix.R.string.auth_offline))
        }
    }

    suspend fun accessToken(): String? =
        mutex.withLock {
            val stored = store.read() ?: return null
            if (!stored.expired(nowSeconds())) return stored.accessToken
            try {
                val refreshed = refreshLocked(stored)
                if (refreshed == null) {
                    invalidateLocked()
                    null
                } else refreshed.accessToken
            } catch (_: IOException) {
                publish(stored, offline = true)
                null
            }
        }

    suspend fun refreshIfNeeded() = accessToken()

    /**
     * Called after a protected API returned 401. A refresh token rejected by Auth invalidates the
     * session; a network/server failure while refreshing is only Offline and must not sign the user
     * out. IOException is intentionally propagated so SyncEngine can persist Offline.
     */
    suspend fun recoverFromUnauthorized(): String? =
        mutex.withLock {
            val stored = store.read() ?: run {
                invalidateLocked()
                return null
            }
            val refreshed = refreshLocked(stored)
            refreshed?.accessToken ?: run {
                invalidateLocked()
                null
            }
        }

    suspend fun invalidateSession() {
        mutex.withLock { invalidateLocked() }
    }

    suspend fun signOut() {
        mutex.withLock {
            val token = store.read()?.accessToken
            if (token != null && config.configured) {
                runCatching { call { api.request("POST", "/auth/v1/logout?scope=local", token, "{}") } }
            }
            invalidateLocked()
        }
    }

    suspend fun deleteAccount(): Result<DeleteAccountConfirmation> {
        var token = accessToken()
            ?: return Result.failure(noTokenFailure())
        return try {
            var response = call { api.request("POST", "/functions/v1/delete-account", token, "{}") }
            if (response.code == 401) {
                token = recoverFromUnauthorized()
                    ?: return Result.failure(AuthException(com.example.pix.R.string.auth_session_expired))
                response = call { api.request("POST", "/functions/v1/delete-account", token, "{}") }
            }
            if (response.code == 401) {
                invalidateSession()
                return Result.failure(AuthException(com.example.pix.R.string.auth_session_expired))
            }
            if (response.ok) return Result.success(DeleteAccountConfirmation.Deleted)
            if (response.code == 404 && response.body.contains("already_deleted")) {
                return Result.success(DeleteAccountConfirmation.AlreadyDeleted)
            }
            Result.failure(
                AuthException(
                    AuthErrors.map(response.code, response.body),
                    response.body.take(200),
                )
            )
        } catch (_: IOException) {
            Result.failure(AuthException(com.example.pix.R.string.auth_offline))
        }
    }

    private suspend fun refreshLocked(stored: AuthSession): AuthSession? {
        if (!config.configured || stored.refreshToken.isBlank()) return null
        val response =
            call {
                api.auth(
                    "/token?grant_type=refresh_token",
                    JSONObject().put("refresh_token", stored.refreshToken),
                )
            }
        if (!response.ok) {
            // A rejected refresh token means the session is no longer usable. Server/rate-limit
            // failures are transient: keep the device-bound account available locally and retry
            // later instead of destroying a potentially valid session.
            if (response.code in 400..499 && response.code != 429) return null
            throw IOException("auth refresh temporarily unavailable (${response.code})")
        }
        val next = parseToken(JSONObject(response.body)).copy(recoveryPending = stored.recoveryPending)
        persist(next)
        return next
    }

    private fun persist(session: AuthSession) {
        store.write(session)
        publish(session)
    }

    private fun publish(session: AuthSession, offline: Boolean = false) {
        _state.value =
            if (session.recoveryPending) AuthState.PasswordRecovery(session.user)
            else AuthState.Authenticated(session.user, offline)
    }

    private fun invalidateLocked() {
        store.clear()
        _state.value = AuthState.Unauthenticated
    }

    private fun parseToken(json: JSONObject): AuthSession {
        val user = json.getJSONObject("user")
        val expires =
            if (json.has("expires_at")) json.getLong("expires_at")
            else nowSeconds() + json.optLong("expires_in", 3600)
        return AuthSession(
            accessToken = json.getString("access_token"),
            refreshToken = json.getString("refresh_token"),
            expiresAt = expires,
            user = PcixUser(user.getString("id"), user.optString("email")),
        )
    }

    private fun parseParams(raw: String): Map<String, String> {
        val query = raw.substringAfter('?', missingDelimiterValue = "").substringBefore('#')
        val fragment = raw.substringAfter('#', missingDelimiterValue = "")
        return sequenceOf(query, fragment)
            .filter { it.isNotBlank() }
            .flatMap { it.split('&').asSequence() }
            .mapNotNull {
                val i = it.indexOf('=')
                if (i <= 0) null
                else it.substring(0, i) to URLDecoder.decode(it.substring(i + 1), "UTF-8")
            }
            .toMap()
    }

    private fun isRecoveryRedirect(raw: String): Boolean =
        raw == RECOVERY_REDIRECT ||
            raw.startsWith("$RECOVERY_REDIRECT?") ||
            raw.startsWith("$RECOVERY_REDIRECT#")

    private fun invalidRecovery(detail: String): Result<Unit> {
        val error = AuthException(com.example.pix.R.string.auth_invalid_recovery_link, detail)
        _state.value = AuthState.Error(error.messageRes, detail)
        return Result.failure(error)
    }

    private fun pkceChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }

    private fun noTokenFailure(): AuthException =
        if ((_state.value as? AuthState.Authenticated)?.offline == true) {
            AuthException(com.example.pix.R.string.auth_offline)
        } else {
            AuthException(com.example.pix.R.string.auth_session_expired)
        }

    private fun responseFailure(
        response: CloudHttp.Response,
        setState: Boolean = true,
    ): Result<Unit> {
        val messageRes = AuthErrors.map(response.code, response.body)
        if (setState) _state.value = AuthState.Error(messageRes, response.body.take(200))
        return Result.failure(AuthException(messageRes, response.body.take(200)))
    }

    private suspend fun runAuth(
        setErrorState: Boolean,
        block: suspend () -> Result<Unit>,
    ): Result<Unit> =
        mutex.withLock {
            if (!config.configured) {
                val failure = AuthException(com.example.pix.R.string.cloud_missing_config, "unconfigured")
                if (setErrorState) {
                    _state.value = AuthState.Error(com.example.pix.R.string.cloud_missing_config)
                }
                return Result.failure(failure)
            }
            try {
                block()
            } catch (_: IOException) {
                if (setErrorState) _state.value = AuthState.Error(com.example.pix.R.string.auth_offline)
                Result.failure(AuthException(com.example.pix.R.string.auth_offline))
            }
        }

    private suspend fun <T> call(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private fun Long?.orDefault(default: Long): Long = this ?: default

    companion object {
        const val RECOVERY_REDIRECT = "com.example.pix://auth/recovery"

        private fun generatePkceVerifier(): String {
            val bytes = ByteArray(32)
            SecureRandom().nextBytes(bytes)
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        }
    }
}
