package com.example.pix.cloud

import com.example.pix.R

object AuthValidation {
    fun validEmail(email: String) = Regex("""^[^\s@]+@[^\s@]+\.[^\s@]+$""").matches(email.trim())
    fun registration(email: String, password: String, confirmation: String): Int? = when {
        !validEmail(email) -> R.string.auth_invalid_email
        password.length < 6 -> R.string.auth_weak_password
        password != confirmation -> R.string.auth_password_mismatch
        else -> null
    }
}
