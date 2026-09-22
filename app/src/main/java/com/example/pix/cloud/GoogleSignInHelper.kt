package com.example.pix.cloud

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.NoCredentialException
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import java.security.MessageDigest
import android.util.Base64
import java.security.SecureRandom

class GoogleSignInHelper(private val config: CloudConfig) {
    data class Token(val idToken: String, val nonce: String)

    suspend fun token(activity: Activity): Token {
        if (!config.googleConfigured) error("google")
        val nonce = secureNonce()
        val hashed = sha256(nonce)
        val manager = CredentialManager.create(activity)
        val request = request(hashed, filterAuthorized = true, button = false)
        val credential =
            try {
                manager.getCredential(activity, request).credential
            } catch (_: NoCredentialException) {
                manager.getCredential(activity, request(hashed, filterAuthorized = false, button = true)).credential
            }
        val google =
            if (credential is CustomCredential &&
                    credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            )
                GoogleIdTokenCredential.createFrom(credential.data)
            else error("credential")
        return Token(google.idToken, nonce)
    }

    private fun request(hashedNonce: String, filterAuthorized: Boolean, button: Boolean):
        GetCredentialRequest {
        val builder = GetCredentialRequest.Builder()
        if (button) {
            builder.addCredentialOption(
                GetSignInWithGoogleOption.Builder(config.googleWebClientId)
                    .setNonce(hashedNonce)
                    .build()
            )
        } else {
            builder.addCredentialOption(
                GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(filterAuthorized)
                    .setServerClientId(config.googleWebClientId)
                    .setAutoSelectEnabled(filterAuthorized)
                    .setNonce(hashedNonce)
                    .build()
            )
        }
        return builder.build()
    }


    private fun secureNonce(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    }

    private fun sha256(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
