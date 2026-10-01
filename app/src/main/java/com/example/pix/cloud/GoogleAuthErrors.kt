package com.example.pix.cloud
import androidx.credentials.exceptions.*
import com.example.pix.R
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import java.io.IOException
object GoogleAuthErrors {
    fun message(error: Throwable): Int = when (error) {
        is AuthException -> error.messageRes
        is GetCredentialCancellationException -> R.string.auth_google_cancelled
        is GetCredentialProviderConfigurationException, is GetCredentialUnsupportedException -> R.string.auth_google_configuration
        is NoCredentialException -> R.string.auth_google_no_account
        is GoogleIdTokenParsingException -> R.string.auth_google_token
        is IOException -> R.string.auth_offline
        else -> R.string.auth_google_failed
    }
}
