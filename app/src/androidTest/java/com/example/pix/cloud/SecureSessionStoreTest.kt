package com.example.pix.cloud

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test

class SecureSessionStoreTest {
    @Test
    fun sessionTokensAreEncryptedAtRestAndRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        store.clear()
        val session =
            AuthSession(
                accessToken = "access-token-plain-marker",
                refreshToken = "refresh-token-plain-marker",
                expiresAt = 4_102_444_800L,
                user = PcixUser("secure-user", "secure@example.com"),
            )

        store.write(session)

        val raw =
            context
                .getSharedPreferences("pcix.auth.secure", Context.MODE_PRIVATE)
                .all
                .values
                .joinToString("|")
        assertFalse(raw.contains(session.accessToken))
        assertFalse(raw.contains(session.refreshToken))
        assertEquals(session, store.read())
        store.clear()
    }


    @Test
    fun legacyPlaintextSessionIsMigratedAndRemoved() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val securePrefs = context.getSharedPreferences("pcix.auth.secure", Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences("pcix.auth", Context.MODE_PRIVATE)
        securePrefs.edit().clear().commit()
        legacyPrefs.edit().clear().commit()
        val session =
            AuthSession(
                accessToken = "legacy-access-marker",
                refreshToken = "legacy-refresh-marker",
                expiresAt = 4_102_444_800L,
                user = PcixUser("legacy-user", "legacy@example.com"),
            )
        legacyPrefs.edit().putString("session", AuthSessionCodec.encode(session)).commit()

        val restored = SecureSessionStore(context).read()

        assertEquals(session, restored)
        assertFalse(legacyPrefs.contains("session"))
        val encrypted = securePrefs.all.values.joinToString("|")
        assertFalse(encrypted.contains(session.accessToken))
        assertFalse(encrypted.contains(session.refreshToken))
        SecureSessionStore(context).clear()
    }
    @Test
    fun recoveryPkceVerifierIsEncryptedAtRest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = SecureSessionStore(context)
        store.clear()
        val verifier = "pkce-verifier-plain-marker-012345678901234567890123"

        store.writeRecoveryVerifier(verifier)

        val raw =
            context
                .getSharedPreferences("pcix.auth.secure", Context.MODE_PRIVATE)
                .all
                .values
                .joinToString("|")
        assertFalse(raw.contains(verifier))
        assertEquals(verifier, store.recoveryVerifier())
        store.clearRecoveryVerifier()
        assertNull(store.recoveryVerifier())
    }

}
