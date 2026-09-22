package com.example.pix.domain

import java.text.Normalizer
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.Locale

object TaskRules {
    fun normalizedTag(name: String): String =
        Normalizer.normalize(name.trim(), Normalizer.Form.NFC).lowercase(Locale.ROOT)

    fun searchPattern(text: String): String =
        if (text.isBlank()) ""
        else "%" + text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

    fun validTitle(title: String): Boolean = title.isNotBlank() && title.trim().length <= 200

    fun isOverdue(day: Long?, minute: Int?, now: ZonedDateTime): Boolean {
        if (day == null) return false
        val today = now.toLocalDate().toEpochDay()
        return day < today ||
            (day == today && minute != null && minute < now.hour * 60 + now.minute)
    }

    fun inNextSevenDays(day: Long?, today: LocalDate): Boolean =
        day != null && day >= today.toEpochDay() && day < today.plusDays(7).toEpochDay()
}
