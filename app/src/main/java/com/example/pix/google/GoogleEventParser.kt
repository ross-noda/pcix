package com.example.pix.google

import com.example.pix.data.GoogleEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import org.json.JSONObject

object GoogleEventParser {
    fun parse(calendarId: String, colorArgb: Int, json: JSONObject, zone: ZoneId): GoogleEventEntity? {
        val status = json.optString("status", "confirmed")
        val cancelled = status == "cancelled"
        val start = json.optJSONObject("start") ?: JSONObject()
        val end = json.optJSONObject("end") ?: JSONObject()
        val startDate = start.optString("date")
        val allDay = startDate.isNotBlank()
        val (startDay, endDay, startMinute, endMinute) =
            if (allDay) {
                val first = LocalDate.parse(startDate).toEpochDay()
                val lastExclusive =
                    end.optString("date").takeIf { it.isNotBlank() }?.let { LocalDate.parse(it).toEpochDay() }
                        ?: (first + 1)
                Quad(first, lastExclusive, null, null)
            } else {
                val startAt = dateTime(start, zone) ?: return if (cancelled) cancelledStub(calendarId, json, colorArgb) else null
                val endAt = dateTime(end, zone) ?: startAt.plusSeconds(1800)
                val startLocal = startAt.atZone(zone).toLocalDateTime()
                val endLocal = endAt.atZone(zone).toLocalDateTime()
                val first = startLocal.toLocalDate().toEpochDay()
                var last = endLocal.toLocalDate().toEpochDay()
                if (endLocal.toLocalTime() == java.time.LocalTime.MIDNIGHT) {
                    /* exclusive midnight: the end day is not occupied */
                } else last += 1
                if (last <= first) last = first + 1
                Quad(
                    first,
                    last,
                    startLocal.hour * 60 + startLocal.minute,
                    endLocal.hour * 60 + endLocal.minute,
                )
            }
        return GoogleEventEntity(
            id = json.getString("id"),
            calendarId = calendarId,
            title = json.optString("summary").ifBlank { "(Google)" },
            description = json.optString("description"),
            location = json.optString("location"),
            startDay = startDay,
            endDay = endDay,
            startMinute = startMinute,
            endMinute = endMinute,
            allDay = allDay,
            status = status,
            cancelled = cancelled,
            updatedAt = json.optString("updated").takeIf { it.isNotBlank() }?.let {
                runCatching { Instant.parse(it).toEpochMilli() }.getOrNull()
            } ?: 0L,
            recurringEventId = json.optString("recurringEventId").takeIf { it.isNotBlank() },
            colorArgb = colorArgb,
        )
    }

    private fun cancelledStub(calendarId: String, json: JSONObject, colorArgb: Int) =
        GoogleEventEntity(
            id = json.getString("id"),
            calendarId = calendarId,
            title = json.optString("summary"),
            startDay = 0,
            endDay = 0,
            cancelled = true,
            status = "cancelled",
            colorArgb = colorArgb,
        )

    private fun dateTime(node: JSONObject, zone: ZoneId): Instant? {
        val value = node.optString("dateTime")
        if (value.isBlank()) return null
        return runCatching { OffsetDateTime.parse(value).toInstant() }.getOrElse {
            runCatching {
                    java.time.LocalDateTime.parse(value.substringBefore('+').substringBefore('Z'))
                        .atZone(ZoneId.of(node.optString("timeZone").ifBlank { zone.id }))
                        .toInstant()
                }
                .getOrNull()
        }
    }

    private data class Quad(
        val startDay: Long,
        val endDay: Long,
        val startMinute: Int?,
        val endMinute: Int?,
    )
}

object GoogleColors {
    fun argb(hex: String?): Int {
        val value = hex?.removePrefix("#") ?: return 0xFF5B8BB0.toInt()
        return runCatching { (0xFFL shl 24 or value.toLong(16)).toInt() }.getOrDefault(0xFF5B8BB0.toInt())
    }
}
