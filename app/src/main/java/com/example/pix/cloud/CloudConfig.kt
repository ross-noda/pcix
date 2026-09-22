package com.example.pix.cloud

import com.example.pix.BuildConfig

data class CloudConfig(val supabaseUrl: String, val anonKey: String, val googleWebClientId: String) {
    val configured: Boolean
        get() = supabaseUrl.startsWith("https://") && anonKey.isNotBlank()

    val googleConfigured: Boolean
        get() = googleWebClientId.isNotBlank()

    val origin: String
        get() = supabaseUrl.trimEnd('/')

    companion object {
        fun fromBuild() =
            CloudConfig(
                BuildConfig.SUPABASE_URL.trim(),
                BuildConfig.SUPABASE_ANON_KEY.trim(),
                BuildConfig.GOOGLE_WEB_CLIENT_ID.trim(),
            )
    }
}
