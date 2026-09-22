package com.example.pix.cloud

import com.example.pix.R
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AuthSessionTest {
    private val config = CloudConfig("https://example.supabase.co", "anon", "google-client")
    private val user = PcixUser("user-a", "a@example.com")

    @Test
    fun restoreValidSessionDoesNotRefresh() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 2_000))
        val api = FakeAuthApi()
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.Authenticated(user), repo.state.value)
        assertTrue(api.authCalls.isEmpty())
        assertEquals("access-old", repo.accessToken())
    }

    @Test
    fun expiredSessionKeepsKnownLocalAccountWhenRefreshIsOffline() = runBlocking {
        val stored = session(expiresAt = 900)
        val store = FakeSessionStore(stored)
        val api = FakeAuthApi().apply { authFailure = IOException("offline") }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.Authenticated(user, offline = true), repo.state.value)
        assertEquals(stored, store.value)
    }

    @Test
    fun expiredSessionRefreshesAndRotatesStoredTokens() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 900))
        val api = FakeAuthApi().apply {
            authResponses.add(tokenResponse("access-new", "refresh-new", 5_000))
        }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.Authenticated(user), repo.state.value)
        assertEquals("access-new", store.value!!.accessToken)
        assertEquals("refresh-new", store.value!!.refreshToken)
        assertEquals("/token?grant_type=refresh_token", api.authCalls.single().path)
    }

    @Test
    fun transientRefreshFailureKeepsExpiredSessionForOfflineLocalUse() = runBlocking {
        val stored = session(expiresAt = 900)
        val store = FakeSessionStore(stored)
        val api = FakeAuthApi().apply { authResponses.add(response(503, "temporarily unavailable")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.Authenticated(user, offline = true), repo.state.value)
        assertEquals(stored, store.value)
    }

    @Test
    fun rejectedRefreshClearsExpiredSession() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 900))
        val api = FakeAuthApi().apply { authResponses.add(response(400, "invalid refresh token")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.Unauthenticated, repo.state.value)
        assertNull(store.value)
    }

    @Test
    fun api401ForcesRefreshAndARejectedRefreshInvalidatesSession() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply { authResponses.add(response(400, "refresh token revoked")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        val token = repo.recoverFromUnauthorized()

        assertNull(token)
        assertNull(store.value)
        assertEquals(AuthState.Unauthenticated, repo.state.value)
    }

    @Test
    fun api401CanRecoverWithRotatedSession() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply {
            authResponses.add(tokenResponse("access-rotated", "refresh-rotated", 6_000))
        }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        assertEquals("access-rotated", repo.recoverFromUnauthorized())
        assertEquals(AuthState.Authenticated(user), repo.state.value)
    }

    @Test
    fun api401WithNetworkFailureDuringForcedRefreshInvalidatesSession() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply { authFailure = IOException("offline") }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        assertNull(repo.recoverFromUnauthorized())
        assertNull(store.value)
        assertEquals(AuthState.Unauthenticated, repo.state.value)
    }

    @Test
    fun emailPasswordSignInPersistsReturnedSession() = runBlocking {
        val store = FakeSessionStore()
        val api = FakeAuthApi().apply {
            authResponses.add(tokenResponse("access-email", "refresh-email", 7_000))
        }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        assertTrue(repo.signIn("a@example.com", "password").isSuccess)

        assertEquals("access-email", store.value!!.accessToken)
        val call = api.authCalls.single()
        assertEquals("/token?grant_type=password", call.path)
        assertEquals("a@example.com", JSONObject(call.body).getString("email"))
    }

    @Test
    fun googleSignInSendsGoogleIdTokenAndRawNonceToSupabase() = runBlocking {
        val store = FakeSessionStore()
        val api = FakeAuthApi().apply {
            authResponses.add(tokenResponse("access-google", "refresh-google", 7_000))
        }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        assertTrue(repo.signInGoogle("google-id-token", "raw-nonce").isSuccess)

        val call = api.authCalls.single()
        val body = JSONObject(call.body)
        assertEquals("/token?grant_type=id_token", call.path)
        assertEquals("google", body.getString("provider"))
        assertEquals("google-id-token", body.getString("id_token"))
        assertEquals("raw-nonce", body.getString("nonce"))
        assertEquals(AuthState.Authenticated(user), repo.state.value)
    }

    @Test
    fun signupAwaitingEmailConfirmationDoesNotCreateLocalSession() = runBlocking {
        val api = FakeAuthApi().apply {
            authResponses.add(
                response(
                    200,
                    JSONObject()
                        .put("user", JSONObject().put("id", user.id).put("email", user.email))
                        .put("access_token", JSONObject.NULL)
                        .put("refresh_token", JSONObject.NULL)
                        .toString(),
                )
            )
        }
        val store = FakeSessionStore()
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })

        assertTrue(repo.signUp("a@example.com", "password").isSuccess)
        assertNull(store.value)
        assertEquals(AuthState.Unauthenticated, repo.state.value)
    }

    @Test
    fun passwordRecoveryRequestUsesDedicatedAndroidRedirectAndPkce() = runBlocking {
        val verifier = "0123456789012345678901234567890123456789012"
        val api = FakeAuthApi().apply { authResponses.add(response(200, "{}")) }
        val store = FakeSessionStore()
        val repo = AuthRepository(config, api, store, { 1_000 }, { verifier })

        assertTrue(repo.recover("a@example.com").isSuccess)

        val call = api.authCalls.single()
        assertEquals(
            "/recover?redirect_to=com.example.pix%3A%2F%2Fauth%2Frecovery",
            call.path,
        )
        val body = JSONObject(call.body)
        assertEquals("a@example.com", body.getString("email"))
        assertEquals("s256", body.getString("code_challenge_method"))
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        val expected = Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
        assertEquals(expected, body.getString("code_challenge"))
        assertFalse(body.has("code_verifier"))
        assertEquals(verifier, store.verifier)
    }

    @Test
    fun failedRecoveryRequestDoesNotLeavePkceVerifierBehind() = runBlocking {
        val verifier = "0123456789012345678901234567890123456789012"
        val api = FakeAuthApi().apply { authResponses.add(response(429, "rate limit")) }
        val store = FakeSessionStore()
        val repo = AuthRepository(config, api, store, { 1_000 }, { verifier })

        assertTrue(repo.recover("a@example.com").isFailure)
        assertNull(store.verifier)
    }

    @Test
    fun recoveryDeepLinkExchangesPkceCodeAndCreatesRecoverySession() = runBlocking {
        val verifier = "0123456789012345678901234567890123456789012"
        val api = FakeAuthApi().apply {
            authResponses.add(tokenResponse("recovery-access", "recovery-refresh", 5_000))
        }
        val store = FakeSessionStore().apply { this.verifier = verifier }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        val link = "com.example.pix://auth/recovery?code=recovery-auth-code"

        assertTrue(repo.handleDeeplink(link).isSuccess)

        assertEquals(AuthState.PasswordRecovery(user), repo.state.value)
        assertTrue(store.value!!.recoveryPending)
        assertNull(store.verifier)
        val call = api.authCalls.single()
        assertEquals("/token?grant_type=pkce", call.path)
        assertEquals("recovery-auth-code", JSONObject(call.body).getString("auth_code"))
        assertEquals(verifier, JSONObject(call.body).getString("code_verifier"))
        assertTrue(api.requestCalls.isEmpty())
    }

    @Test
    fun implicitRecoveryLinkWithTokensIsRejected() = runBlocking {
        val store = FakeSessionStore()
        val repo = AuthRepository(config, FakeAuthApi(), store, nowSeconds = { 1_000 })
        val link =
            "com.example.pix://auth/recovery#access_token=recovery-access" +
                "&refresh_token=recovery-refresh&expires_in=3600&type=recovery"

        assertTrue(repo.handleDeeplink(link).isFailure)

        assertNull(store.value)
        assertTrue(repo.state.value is AuthState.Error)
    }

    @Test
    fun recoveryPendingSurvivesProcessRestore() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000).copy(recoveryPending = true))
        val repo = AuthRepository(config, FakeAuthApi(), store, nowSeconds = { 1_000 })

        repo.restore()

        assertEquals(AuthState.PasswordRecovery(user), repo.state.value)
    }

    @Test
    fun changingPasswordCompletesRecoveryAndKeepsSessionCoherent() = runBlocking {
        val recovering = session(expiresAt = 5_000).copy(recoveryPending = true)
        val store = FakeSessionStore(recovering)
        val api = FakeAuthApi().apply { requestResponses.add(response(200, "{}")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()
        assertEquals(AuthState.PasswordRecovery(user), repo.state.value)

        val result = repo.updatePassword("new-password")

        assertTrue(result.isSuccess)
        assertFalse(store.value!!.recoveryPending)
        assertEquals(AuthState.Authenticated(user), repo.state.value)
        val call = api.requestCalls.single()
        assertEquals("PUT", call.method)
        assertEquals("/auth/v1/user", call.path)
        assertEquals("new-password", JSONObject(call.body!!).getString("password"))
    }

    @Test
    fun passwordChangeCanSendCurrentPasswordAndReauthenticationNonce() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply { requestResponses.add(response(200, "{}")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        val result =
            repo.updatePassword(
                newPassword = "new-password",
                currentPassword = "old-password",
                nonce = "123456",
            )

        assertTrue(result.isSuccess)
        val body = JSONObject(api.requestCalls.single().body!!)
        assertEquals("new-password", body.getString("password"))
        assertEquals("old-password", body.getString("current_password"))
        assertEquals("123456", body.getString("nonce"))
    }

    @Test
    fun reauthenticationRequestUsesProtectedEndpoint() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply { requestResponses.add(response(200, "{}")) }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        assertTrue(repo.requestPasswordReauthentication().isSuccess)

        val call = api.requestCalls.single()
        assertEquals("POST", call.method)
        assertEquals("/auth/v1/reauthenticate", call.path)
        assertEquals("access-old", call.accessToken)
        assertEquals(AuthState.Authenticated(user), repo.state.value)
    }

    @Test
    fun second401WhileChangingPasswordInvalidatesSession() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val api = FakeAuthApi().apply {
            requestResponses.add(response(401, "expired"))
            authResponses.add(tokenResponse("access-rotated", "refresh-rotated", 6_000))
            requestResponses.add(response(401, "revoked"))
        }
        val repo = AuthRepository(config, api, store, nowSeconds = { 1_000 })
        repo.restore()

        val result = repo.updatePassword("new-password")

        assertTrue(result.isFailure)
        assertNull(store.value)
        assertEquals(AuthState.Unauthenticated, repo.state.value)
        assertEquals(2, api.requestCalls.size)
    }

    @Test
    fun weakPasswordDoesNotReplaceAuthenticatedState() = runBlocking {
        val store = FakeSessionStore(session(expiresAt = 5_000))
        val repo = AuthRepository(config, FakeAuthApi(), store, nowSeconds = { 1_000 })
        repo.restore()

        val result = repo.updatePassword("123")

        assertTrue(result.isFailure)
        assertEquals(R.string.auth_weak_password, (result.exceptionOrNull() as AuthException).messageRes)
        assertEquals(AuthState.Authenticated(user), repo.state.value)
    }

    private fun session(expiresAt: Long) =
        AuthSession("access-old", "refresh-old", expiresAt, user)

    private fun tokenResponse(access: String, refresh: String, expiresAt: Long) =
        response(
            200,
            JSONObject()
                .put("access_token", access)
                .put("refresh_token", refresh)
                .put("expires_at", expiresAt)
                .put("user", JSONObject().put("id", user.id).put("email", user.email))
                .toString(),
        )

    private fun response(code: Int, body: String) = CloudHttp.Response(code, body, emptyMap())
}

private class FakeSessionStore(initial: AuthSession? = null) : SessionStore {
    var value: AuthSession? = initial
    var verifier: String? = null
    override fun read() = value
    override fun write(session: AuthSession) { value = session }
    override fun clear() { value = null; verifier = null }
    override fun recoveryVerifier() = verifier
    override fun writeRecoveryVerifier(verifier: String) { this.verifier = verifier }
    override fun clearRecoveryVerifier() { verifier = null }
}

private class FakeAuthApi : AuthApi {
    data class AuthCall(val path: String, val body: String, val accessToken: String?)
    data class RequestCall(val method: String, val path: String, val accessToken: String?, val body: String?)

    val authCalls = mutableListOf<AuthCall>()
    val requestCalls = mutableListOf<RequestCall>()
    val authResponses = ArrayDeque<CloudHttp.Response>()
    val requestResponses = ArrayDeque<CloudHttp.Response>()
    var authFailure: IOException? = null

    override fun auth(path: String, body: JSONObject, accessToken: String?): CloudHttp.Response {
        authCalls += AuthCall(path, body.toString(), accessToken)
        authFailure?.let { throw it }
        return authResponses.removeFirstOrNull() ?: error("No auth response queued")
    }

    override fun request(
        method: String,
        path: String,
        accessToken: String?,
        body: String?,
    ): CloudHttp.Response {
        requestCalls += RequestCall(method, path, accessToken, body)
        return requestResponses.removeFirstOrNull() ?: error("No request response queued")
    }
}

class SessionCoordinatorGateTest {
    @Test
    fun accountIsNotReadyUntilOwnershipPreparationCompletes() = runBlocking {
        val auth = FakeAuthController(AuthState.Authenticated(PcixUser("user-b", "b@example.com")))
        val enteredLegacyCheck = CompletableDeferred<Unit>()
        val releaseLegacyCheck = CompletableDeferred<Unit>()
        val accounts =
            object : AccountDataStore {
                override fun owner(): String? = null
                override fun setOwner(id: String?) = Unit
                override suspend fun hasLegacyData(): Boolean {
                    enteredLegacyCheck.complete(Unit)
                    releaseLegacyCheck.await()
                    return false
                }
                override suspend fun wipeUserData() = Unit
                override suspend fun clearGoogle() = Unit
            }
        val coordinator =
            SessionCoordinator(auth, accounts, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        val job = launch { coordinator.afterLogin("user-b") }
        enteredLegacyCheck.await()

        assertTrue(coordinator.state.value is AccountSessionState.PreparingAccount)
        assertFalse(coordinator.state.value is AccountSessionState.Ready)

        releaseLegacyCheck.complete(Unit)
        job.join()
        assertTrue(coordinator.state.value is AccountSessionState.Ready)
    }


    @Test
    fun sessionInvalidatedDuringAccountPreparationNeverBecomesReady() = runBlocking {
        val auth = FakeAuthController(AuthState.Authenticated(PcixUser("user-b", "b@example.com")))
        val wipeStarted = CompletableDeferred<Unit>()
        val allowWipe = CompletableDeferred<Unit>()
        var owner: String? = "user-a"
        val accounts =
            object : AccountDataStore {
                override fun owner(): String? = owner
                override fun setOwner(id: String?) { owner = id }
                override suspend fun hasLegacyData() = false
                override suspend fun wipeUserData() {
                    wipeStarted.complete(Unit)
                    allowWipe.await()
                }
                override suspend fun clearGoogle() = Unit
            }
        val coordinator =
            SessionCoordinator(auth, accounts, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        coordinator.start()
        wipeStarted.await()
        assertTrue(coordinator.state.value is AccountSessionState.PreparingAccount)

        auth.emit(AuthState.Unauthenticated)

        assertTrue(coordinator.state.value is AccountSessionState.SignedOut)
        allowWipe.complete(Unit)
        kotlinx.coroutines.yield()
        assertFalse(coordinator.state.value is AccountSessionState.Ready)
        assertEquals("user-a", owner)
    }

    @Test
    fun differentAccountCannotBecomeReadyBeforeOldLocalDataIsWiped() = runBlocking {
        val auth = FakeAuthController(AuthState.Authenticated(PcixUser("user-b", "b@example.com")))
        val wipeStarted = CompletableDeferred<Unit>()
        val allowWipe = CompletableDeferred<Unit>()
        var owner: String? = "user-a"
        val accounts =
            object : AccountDataStore {
                override fun owner(): String? = owner
                override fun setOwner(id: String?) { owner = id }
                override suspend fun hasLegacyData() = false
                override suspend fun wipeUserData() {
                    wipeStarted.complete(Unit)
                    allowWipe.await()
                }
                override suspend fun clearGoogle() = Unit
            }
        val coordinator =
            SessionCoordinator(auth, accounts, CoroutineScope(SupervisorJob() + Dispatchers.Unconfined))

        val job = launch { coordinator.afterLogin("user-b") }
        wipeStarted.await()
        assertTrue(coordinator.state.value is AccountSessionState.PreparingAccount)
        assertEquals("user-a", owner)

        allowWipe.complete(Unit)
        job.join()
        assertEquals("user-b", owner)
        assertTrue(coordinator.state.value is AccountSessionState.Ready)
    }
}

private class FakeAuthController(initial: AuthState) : AuthSessionController {
    private val mutable = MutableStateFlow(initial)
    override val state = mutable
    override suspend fun restore() = Unit
    fun emit(value: AuthState) { mutable.value = value }
}
