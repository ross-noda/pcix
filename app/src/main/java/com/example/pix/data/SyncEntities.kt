package com.example.pix.data

import androidx.room.*

@Entity(tableName = "sync_outbox", indices = [Index("entityType", "entityId")])
data class SyncOutboxEntity(
    @PrimaryKey val id: String = newId(),
    val entityType: String,
    val entityId: String,
    val operation: String,
    val payload: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptCount: Int = 0,
    val lastAttemptAt: Long? = null,
)

@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val accountId: String,
    val checkpoint: String? = null,
    val lastSuccessAt: Long = 0,
)

@Entity(tableName = "google_calendars")
data class GoogleCalendarEntity(
    @PrimaryKey val id: String,
    val summary: String,
    val colorArgb: Int = 0xFF5B8BB0.toInt(),
    val timeZone: String? = null,
    val enabled: Boolean = true,
    val accessRole: String? = null,
)

@Entity(
    tableName = "google_events",
    foreignKeys =
        [
            ForeignKey(
                entity = GoogleCalendarEntity::class,
                parentColumns = ["id"],
                childColumns = ["calendarId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("calendarId"), Index("startDay"), Index("endDay")],
)
data class GoogleEventEntity(
    @PrimaryKey val id: String,
    val calendarId: String,
    val title: String,
    val description: String = "",
    val location: String = "",
    val startDay: Long,
    val endDay: Long,
    val startMinute: Int? = null,
    val endMinute: Int? = null,
    val allDay: Boolean = false,
    val status: String = "confirmed",
    val cancelled: Boolean = false,
    val updatedAt: Long = 0,
    val recurringEventId: String? = null,
    val colorArgb: Int = 0xFF5B8BB0.toInt(),
)

@Entity(tableName = "google_sync_state")
data class GoogleSyncStateEntity(
    @PrimaryKey val calendarId: String,
    val syncToken: String? = null,
    val lastSyncAt: Long = 0,
)

data class IdStamp(val id: String, val updatedAt: Long)

data class TagLink(val taskId: String, val tagId: String)
