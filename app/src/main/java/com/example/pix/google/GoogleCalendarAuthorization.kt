package com.example.pix.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

internal sealed interface GoogleAuthorizationResult {
    data class Authorized(val accessToken: String) : GoogleAuthorizationResult

    data class Resolution(val sender: IntentSender) : GoogleAuthorizationResult

    data object Unavailable : GoogleAuthorizationResult
}

internal interface GoogleAuthorizationGateway {
    suspend fun authorize(accountEmail: String?, interactive: Boolean): GoogleAuthorizationResult

    fun complete(data: Intent): GoogleAuthorizationResult.Authorized?

    suspend fun revokeCalendarAccess(accountEmail: String)
}

/**
 * Google Identity Services authorization for the Calendar integration only.
 *
 * Access tokens are intentionally never persisted by Pcix. Google Play services owns its token
 * cache; every foreground/background operation asks AuthorizationClient for a currently valid token.
 */
internal class PlayServicesGoogleAuthorizationGateway(context: Context) : GoogleAuthorizationGateway {
    private val client = Identity.getAuthorizationClient(context.applicationContext)

    override suspend fun authorize(
        accountEmail: String?,
        interactive: Boolean,
    ): GoogleAuthorizationResult {
        val builder =
            AuthorizationRequest.builder()
                .setRequestedScopes(if (accountEmail == null) CONNECT_SCOPES else CALENDAR_SCOPES)
        if (accountEmail != null) {
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        } else if (interactive) {
            builder.setPrompt(AuthorizationRequest.Prompt.SELECT_ACCOUNT)
        }
        val result =
            try {
                client.authorize(builder.build()).await()
            } catch (error: ApiException) {
                // A missing/revoked grant or account is a reconnect condition, not a reason for a
                // background retry loop. Only genuinely transient Play-services failures bubble up.
                if (error.statusCode in TRANSIENT_STATUS_CODES) throw error
                return GoogleAuthorizationResult.Unavailable
            }
        if (result.hasResolution()) {
            val sender =
                result.pendingIntent?.intentSender ?: return GoogleAuthorizationResult.Unavailable
            return GoogleAuthorizationResult.Resolution(sender)
        }
        return result.accessToken?.takeIf { it.isNotBlank() }
            ?.let(GoogleAuthorizationResult::Authorized)
            ?: GoogleAuthorizationResult.Unavailable
    }

    override fun complete(data: Intent): GoogleAuthorizationResult.Authorized? =
        client.getAuthorizationResultFromIntent(data).accessToken?.takeIf { it.isNotBlank() }
            ?.let(GoogleAuthorizationResult::Authorized)

    override suspend fun revokeCalendarAccess(accountEmail: String) {
        client
            .revokeAccess(
                RevokeAccessRequest.builder()
                    .setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
                    .setScopes(CALENDAR_SCOPES)
                    .build()
            )
            .await()
    }

    private companion object {
        const val GOOGLE_ACCOUNT_TYPE = "com.google"
        val CALENDAR_SCOPES =
            listOf(
                Scope("https://www.googleapis.com/auth/calendar.calendarlist.readonly"),
                Scope("https://www.googleapis.com/auth/calendar.events.readonly"),
            )
        val CONNECT_SCOPES = CALENDAR_SCOPES + listOf(Scope("openid"), Scope("email"))
        val TRANSIENT_STATUS_CODES =
            setOf(
                CommonStatusCodes.NETWORK_ERROR,
                CommonStatusCodes.TIMEOUT,
                CommonStatusCodes.INTERRUPTED,
                CommonStatusCodes.INTERNAL_ERROR,
            )
    }
}
