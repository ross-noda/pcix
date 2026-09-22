package com.example.pix.data

import androidx.room.*
import java.util.UUID

const val INBOX_ID = "00000000-0000-0000-0000-000000000001"

fun newId(): String = UUID.randomUUID().toString()

@Entity(tableName = "lists")
data class ListEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    @ColumnInfo(defaultValue = "'📋'") val icon: String = "📋",
    val color: Int = 0,
    val sortOrder: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "tasks",
    foreignKeys =
        [
            ForeignKey(
                entity = ListEntity::class,
                parentColumns = ["id"],
                childColumns = ["listId"],
                onDelete = ForeignKey.RESTRICT,
            )
        ],
    indices =
        [
            Index("listId"),
            Index("dueDay"),
            Index("isCompleted"),
            Index(value = ["seriesId", "originalDay"], unique = true),
        ],
)
data class TaskEntity(
    @PrimaryKey val id: String = newId(),
    val title: String,
    val notes: String = "",
    val listId: String = INBOX_ID,
    val dueDay: Long? = null,
    val minuteOfDay: Int? = null,
    val durationMinutes: Int? = null,
    val priority: Int = 0,
    val matrixUrgent: Boolean? = null,
    val matrixImportant: Boolean? = null,
    val isCompleted: Boolean = false,
    val completedAt: Long? = null,
    val seriesId: String? = null,
    val originalDay: Long? = null,
    @ColumnInfo(defaultValue = "0") val isTemplate: Boolean = false,
    @ColumnInfo(defaultValue = "0") val isSkipped: Boolean = false,
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "tags", indices = [Index(value = ["normalizedName"], unique = true)])
data class TagEntity(
    @PrimaryKey val id: String = newId(),
    val name: String,
    val normalizedName: String,
    val color: Int = 0,
    @ColumnInfo(defaultValue = "0") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "task_tags",
    primaryKeys = ["taskId", "tagId"],
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            ),
            ForeignKey(
                entity = TagEntity::class,
                parentColumns = ["id"],
                childColumns = ["tagId"],
                onDelete = ForeignKey.CASCADE,
            ),
        ],
    indices = [Index("tagId")],
)
data class TaskTagCrossRef(val taskId: String, val tagId: String)

@Entity(
    tableName = "subtasks",
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("taskId")],
)
data class SubtaskEntity(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val title: String,
    val isCompleted: Boolean = false,
    val sortOrder: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

data class TaskWithDetails(
    @Embedded val task: TaskEntity,
    @Relation(parentColumn = "seriesId", entityColumn = "id") val series: RecurringSeriesEntity?,
    @Relation(parentColumn = "listId", entityColumn = "id") val list: ListEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy =
            Junction(TaskTagCrossRef::class, parentColumn = "taskId", entityColumn = "tagId"),
    )
    val tags: List<TagEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId") val subtasks: List<SubtaskEntity>,
    @Relation(parentColumn = "id", entityColumn = "taskId")
    val images: List<TaskImage> = emptyList(),
)

data class ListWithCount(@Embedded val list: ListEntity, val activeCount: Int)

data class TagWithCount(@Embedded val tag: TagEntity, val activeCount: Int)

data class CalendarMark(val dueDay: Long, val color: Int, val count: Int)

@Entity(
    tableName = "reminder_receipts",
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
)
data class ReminderReceipt(@PrimaryKey val taskId: String, val triggerAt: Long)

@Entity(
    tableName = "recurring_series",
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["templateTaskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("templateTaskId")],
)
data class RecurringSeriesEntity(
    @PrimaryKey val id: String = newId(),
    val rule: String,
    val anchorDay: Long,
    val templateTaskId: String,
    val endBefore: Long? = null,
    @ColumnInfo(defaultValue = "0") val updatedAt: Long = System.currentTimeMillis(),
)

enum class RecurrenceScope {
    ONLY_THIS,
    THIS_AND_FUTURE,
}

@Entity(
    tableName = "task_images",
    foreignKeys =
        [
            ForeignKey(
                entity = TaskEntity::class,
                parentColumns = ["id"],
                childColumns = ["taskId"],
                onDelete = ForeignKey.CASCADE,
            )
        ],
    indices = [Index("taskId")],
)
data class TaskImage(
    @PrimaryKey val id: String = newId(),
    val taskId: String,
    val fileName: String,
    val createdAt: Long = System.currentTimeMillis(),
)
