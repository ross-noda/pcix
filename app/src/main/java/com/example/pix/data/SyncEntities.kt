package com.example.pix.data

import androidx.room.*

/**
 * Persistent local final-state mutation. `createdAt` orders queued snapshots; conflict authority is
 * the server-issued `server_version` persisted in [SyncEntityVersionEntity]. `updated_at` remains
 * user-data metadata in UPSERT payloads and is never used to resolve multi-device conflicts.
 */
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
    @ColumnInfo(defaultValue = "'Idle'") val status: String = "Idle",
)

@Entity(
    tableName = "sync_entity_versions",
    primaryKeys = ["accountId", "entityType", "entityId"],
    indices = [Index("accountId", "serverVersion")],
)
data class SyncEntityVersionEntity(
    val accountId: String,
    val entityType: String,
    val entityId: String,
    val serverVersion: Long,
    val deleted: Boolean = false,
)

@Entity(tableName = "google_calendar_accounts", indices = [Index(value = ["email"])])
data class GoogleCalendarAccountEntity(
    @PrimaryKey val id: String,
    val email: String,
)

@Entity(
    tableName = "google_calendars",
    primaryKeys = ["accountId", "id"],
    foreignKeys =
        [
            ForeignKey(
                entity = GoogleCalendarAccountEntity::class,
                parentColumns = ["id"],
                childColumns = ["accountId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("accountId")],
)
data class GoogleCalendarEntity(
    val accountId: String,
    val id: String,
    val summary: String,
    val colorArgb: Int = 0xFF5B8BB0.toInt(),
    val timeZone: String? = null,
    val enabled: Boolean = true,
    val accessRole: String? = null,
)

@Entity(
    tableName = "google_events",
    primaryKeys = ["accountId", "calendarId", "eventId"],
    foreignKeys =
        [
            ForeignKey(
                entity = GoogleCalendarEntity::class,
                parentColumns = ["accountId", "id"],
                childColumns = ["accountId", "calendarId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [
        Index(value = ["accountId", "calendarId"]),
        Index("startDay"),
        Index("endDay"),
        Index(value = ["accountId", "calendarId", "recurringEventId", "originalStartDay", "originalStartMinute"]),
    ],
)
data class GoogleEventEntity(
    val accountId: String,
    val calendarId: String,
    val eventId: String,
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
    val originalStartDay: Long? = null,
    val originalStartMinute: Int? = null,
    val colorArgb: Int = 0xFF5B8BB0.toInt(),
) {
    @get:Ignore
    val stableKey: String
        get() = "$accountId\u0000$calendarId\u0000$eventId"
}

@Entity(
    tableName = "google_sync_state",
    primaryKeys = ["accountId", "calendarId"],
    foreignKeys =
        [
            ForeignKey(
                entity = GoogleCalendarEntity::class,
                parentColumns = ["accountId", "id"],
                childColumns = ["accountId", "calendarId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index(value = ["accountId", "calendarId"])],
)
data class GoogleSyncStateEntity(
    val accountId: String,
    val calendarId: String,
    val syncToken: String? = null,
    val lastSyncAt: Long = 0,
)

data class TagLink(val taskId: String, val tagId: String)
