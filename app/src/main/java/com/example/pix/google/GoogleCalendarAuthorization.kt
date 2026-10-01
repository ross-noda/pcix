package com.example.pix.google

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
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
                .setRequestedScopes(if (interactive) CONNECT_SCOPES else CALENDAR_SCOPES)
        if (accountEmail != null) {
            builder.setAccount(Account(accountEmail, GOOGLE_ACCOUNT_TYPE))
        }
        val result = client.authorize(builder.build()).await()
        if (result.hasResolution()) {
            val sender =
                result.pendingIntent?.intentSender ?: return GoogleAuthorizationResult.Unavailable
            return GoogleAuthorizationResult.Resolution(sender)
        }
        return authorized(result)
    }

    override fun complete(data: Intent): GoogleAuthorizationResult.Authorized? =
        authorized(client.getAuthorizationResultFromIntent(data))

    private fun authorized(result: com.google.android.gms.auth.api.identity.AuthorizationResult): GoogleAuthorizationResult.Authorized {
        if (result.hasResolution()) throw CalendarPermissionException()
        return validateCalendarGrant(result.accessToken, result.grantedScopes)
    }

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

    }
}

internal class CalendarPermissionException : Exception("Calendar scopes or token missing")

/** Validate both grants even when Google returns a token after partial consent. */
internal fun validateCalendarGrant(token: String?, scopes: List<String>): GoogleAuthorizationResult.Authorized {
    val required = listOf("https://www.googleapis.com/auth/calendar.calendarlist.readonly",
        "https://www.googleapis.com/auth/calendar.events.readonly")
    if (token.isNullOrBlank() || !scopes.containsAll(required)) throw CalendarPermissionException()
    return GoogleAuthorizationResult.Authorized(token)
}
