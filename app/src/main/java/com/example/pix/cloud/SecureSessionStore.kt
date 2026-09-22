package com.example.pix.cloud

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import org.json.JSONObject

internal interface SessionStore {
    fun read(): AuthSession?
    fun write(session: AuthSession)
    fun clear()
    fun recoveryVerifier(): String?
    fun writeRecoveryVerifier(verifier: String)
    fun clearRecoveryVerifier()
}

internal object AuthSessionCodec {
    fun encode(session: AuthSession): String =
        JSONObject()
            .put("access_token", session.accessToken)
            .put("refresh_token", session.refreshToken)
            .put("expires_at", session.expiresAt)
            .put("user_id", session.user.id)
            .put("email", session.user.email)
            .put("recovery_pending", session.recoveryPending)
            .toString()

    fun decode(raw: String): AuthSession? =
        runCatching {
                val json = JSONObject(raw)
                AuthSession(
                    accessToken = json.getString("access_token"),
                    refreshToken = json.getString("refresh_token"),
                    expiresAt = json.getLong("expires_at"),
                    user = PcixUser(json.getString("user_id"), json.optString("email")),
                    recoveryPending = json.optBoolean("recovery_pending", false),
                )
            }
            .getOrNull()
}

/** Stores the Supabase session encrypted with a non-exportable Android Keystore AES key. */
internal class SecureSessionStore(context: Context) : SessionStore {
    private val securePrefs =
        context.getSharedPreferences(SECURE_PREFS, Context.MODE_PRIVATE)
    private val legacyPrefs =
        context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    override fun read(): AuthSession? {
        val encrypted = securePrefs.getString(KEY_CIPHERTEXT, null)
        if (encrypted != null) {
            val decoded = decrypt(encrypted)
            if (decoded != null) return AuthSessionCodec.decode(decoded)
            // A restored/corrupt ciphertext is unusable without the original Keystore key.
            securePrefs.edit().remove(KEY_CIPHERTEXT).commit()
        }
        return migrateLegacySession()
    }

    override fun write(session: AuthSession) {
        val encoded = AuthSessionCodec.encode(session)
        val encrypted = encrypt(encoded)
        check(securePrefs.edit().putString(KEY_CIPHERTEXT, encrypted).commit()) {
            "Unable to persist secure auth session"
        }
        legacyPrefs.edit().remove(LEGACY_KEY_SESSION).commit()
    }

    override fun clear() {
        securePrefs.edit().remove(KEY_CIPHERTEXT).remove(KEY_RECOVERY_VERIFIER).commit()
        legacyPrefs.edit().remove(LEGACY_KEY_SESSION).commit()
    }

    override fun recoveryVerifier(): String? {
        val encrypted = securePrefs.getString(KEY_RECOVERY_VERIFIER, null) ?: return null
        return decrypt(encrypted).also {
            if (it == null) securePrefs.edit().remove(KEY_RECOVERY_VERIFIER).commit()
        }
    }

    override fun writeRecoveryVerifier(verifier: String) {
        val encrypted = encrypt(verifier)
        check(securePrefs.edit().putString(KEY_RECOVERY_VERIFIER, encrypted).commit()) {
            "Unable to persist recovery verifier"
        }
    }

    override fun clearRecoveryVerifier() {
        securePrefs.edit().remove(KEY_RECOVERY_VERIFIER).commit()
    }

    private fun migrateLegacySession(): AuthSession? {
        val raw = legacyPrefs.getString(LEGACY_KEY_SESSION, null) ?: return null
        val session = AuthSessionCodec.decode(raw)
        if (session == null) {
            legacyPrefs.edit().remove(LEGACY_KEY_SESSION).commit()
            return null
        }
        // Never keep a refresh token in the legacy plaintext file if Keystore migration fails.
        check(legacyPrefs.edit().remove(LEGACY_KEY_SESSION).commit()) {
            "Unable to remove legacy auth session"
        }
        write(session)
        return session
    }

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        val payload = ByteArray(cipher.iv.size + ciphertext.size)
        System.arraycopy(cipher.iv, 0, payload, 0, cipher.iv.size)
        System.arraycopy(ciphertext, 0, payload, cipher.iv.size, ciphertext.size)
        return Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    private fun decrypt(value: String): String? =
        runCatching {
                val payload = Base64.decode(value, Base64.NO_WRAP)
                require(payload.size > IV_BYTES)
                val iv = payload.copyOfRange(0, IV_BYTES)
                val ciphertext = payload.copyOfRange(IV_BYTES, payload.size)
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(TAG_BITS, iv))
                String(cipher.doFinal(ciphertext), Charsets.UTF_8)
            }
            .getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "pcix.auth.session.aes.v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
        private const val SECURE_PREFS = "pcix.auth.secure"
        private const val KEY_CIPHERTEXT = "session"
        private const val KEY_RECOVERY_VERIFIER = "recovery_verifier"
        private const val LEGACY_PREFS = "pcix.auth"
        private const val LEGACY_KEY_SESSION = "session"
    }
}
