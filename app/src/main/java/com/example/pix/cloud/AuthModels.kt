package com.example.pix.cloud

sealed class AuthState {
    data object Loading : AuthState()

    data object Unauthenticated : AuthState()

    data class Authenticated(val user: PcixUser, val offline: Boolean = false) : AuthState()

    data class PasswordRecovery(val user: PcixUser) : AuthState()

    data class Error(val messageRes: Int, val detail: String? = null) : AuthState()
}

data class PcixUser(val id: String, val email: String)

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: PcixUser,
    val recoveryPending: Boolean = false,
) {
    fun expired(nowSeconds: Long = System.currentTimeMillis() / 1000) = nowSeconds >= expiresAt - 30
}

class AuthException(val messageRes: Int, detail: String? = null) : IllegalStateException(detail)

object AuthErrors {
    fun map(status: Int, body: String): Int {
        val lower = body.lowercase()
        return when {
            status == 0 -> com.example.pix.R.string.auth_offline
            status == 401 -> com.example.pix.R.string.auth_session_expired
            "reauthentication_needed" in lower -> com.example.pix.R.string.auth_reauthentication_required
            "reauthentication_not_valid" in lower -> com.example.pix.R.string.auth_reauthentication_invalid
            "current_password_required" in lower -> com.example.pix.R.string.auth_current_password_required
            "current_password_mismatch" in lower -> com.example.pix.R.string.auth_current_password_invalid
            "same_password" in lower -> com.example.pix.R.string.auth_same_password
            "invalid login" in lower || status == 400 && "invalid" in lower ->
                com.example.pix.R.string.auth_invalid_credentials
            "already registered" in lower || "user already" in lower ->
                com.example.pix.R.string.auth_account_exists
            status == 422 && "password" in lower -> com.example.pix.R.string.auth_weak_password
            status == 429 -> com.example.pix.R.string.auth_rate_limited
            status in 500..599 -> com.example.pix.R.string.auth_server
            else -> com.example.pix.R.string.auth_generic
        }
    }
}
