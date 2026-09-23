package com.example.pix.cloud

import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class AccountSessionState {
    data object Restoring : AccountSessionState()
    data object SignedOut : AccountSessionState()
    data class PreparingAccount(val user: PcixUser) : AccountSessionState()
    data class LegacyDecision(val user: PcixUser) : AccountSessionState()
    data class PasswordRecovery(val user: PcixUser) : AccountSessionState()
    data class Ready(val user: PcixUser) : AccountSessionState()
    data class Error(val messageRes: Int) : AccountSessionState()
}

class SessionCoordinator internal constructor(
    private val auth: AuthSessionController,
    private val accounts: AccountDataStore,
    private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)
    private val accountMutex = Mutex()
    private val _state = MutableStateFlow<AccountSessionState>(AccountSessionState.Restoring)
    val state: StateFlow<AccountSessionState> = _state

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            _state.value = AccountSessionState.Restoring
            auth.restore()
            auth.state.collectLatest(::handleAuthState)
        }
    }

    private suspend fun handleAuthState(authState: AuthState) {
        when (authState) {
            AuthState.Loading -> _state.value = AccountSessionState.Restoring
            AuthState.Unauthenticated -> _state.value = AccountSessionState.SignedOut
            is AuthState.Error -> _state.value = AccountSessionState.SignedOut
            is AuthState.PasswordRecovery ->
                _state.value = AccountSessionState.PasswordRecovery(authState.user)
            is AuthState.Authenticated -> afterLogin(authState.user.id)
        }
    }

    suspend fun afterLogin(userId: String) {
        accountMutex.withLock {
            when (val current = _state.value) {
                is AccountSessionState.Ready -> if (current.user.id == userId) return
                is AccountSessionState.LegacyDecision -> if (current.user.id == userId) return
                else -> Unit
            }
            val authState = auth.state.value
            val user =
                when (authState) {
                    is AuthState.Authenticated -> authState.user.takeIf { it.id == userId }
                    else -> null
                } ?: return
            _state.value = AccountSessionState.PreparingAccount(user)
            try {
                val owner = accounts.owner()
                when {
                    owner == userId -> publishReadyIfCurrent(user)
                    owner != null && owner != userId -> {
                        // Never expose the previous owner's Room contents to the new account.
                        // Preserve them first (including outbox) so an offline account switch is recoverable.
                        accounts.protect(owner)
                        val restored = accounts.restoreProtected(userId)
                        if (!restored) accounts.wipeUserData()
                        accounts.clearGoogle()
                        accounts.setOwner(userId)
                        publishReadyIfCurrent(user)
                    }
                    accounts.hasLegacyData() -> {
                        if (authenticatedUser(user.id) != null) {
                            _state.value = AccountSessionState.LegacyDecision(user)
                        }
                    }
                    else -> {
                        // A prior logout may have archived this account locally. Restore it before
                        // Home can become Ready, otherwise start from an empty account cache.
                        accounts.restoreProtected(userId)
                        accounts.setOwner(userId)
                        publishReadyIfCurrent(user)
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _state.value = AccountSessionState.Error(com.example.pix.R.string.auth_local_store_error)
            }
        }
    }

    suspend fun onImported() {
        val user = (auth.state.value as? AuthState.Authenticated)?.user ?: return
        accounts.setOwner(user.id)
        publishReadyIfCurrent(user)
    }

    private fun authenticatedUser(userId: String): PcixUser? =
        (auth.state.value as? AuthState.Authenticated)?.user?.takeIf { it.id == userId }

    private fun publishReadyIfCurrent(user: PcixUser) {
        if (authenticatedUser(user.id) != null) {
            _state.value = AccountSessionState.Ready(user)
        }
    }
}
