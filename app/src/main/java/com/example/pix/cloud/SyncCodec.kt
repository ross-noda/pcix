package com.example.pix.cloud

import com.example.pix.data.*
import org.json.JSONArray
import org.json.JSONObject

object SyncCodec {
    fun list(row: ListEntity) =
        JSONObject()
            .put("id", row.id)
            .put("name", row.name)
            .put("icon", row.icon)
            .put("color", row.color)
            .put("sort_order", row.sortOrder)
            .put("created_at", row.createdAt)
            .put("updated_at", row.updatedAt)

    fun tag(row: TagEntity) =
        JSONObject()
            .put("id", row.id)
            .put("name", row.name)
            .put("normalized_name", row.normalizedName)
            .put("color", row.color)
            .put("created_at", row.createdAt)
            .put("updated_at", row.updatedAt)

    fun task(row: TaskEntity) =
        JSONObject()
            .put("id", row.id)
            .put("title", row.title)
            .put("notes", row.notes)
            .put("list_id", row.listId)
            .put("due_day", row.dueDay ?: JSONObject.NULL)
            .put("minute_of_day", row.minuteOfDay ?: JSONObject.NULL)
            .put("duration_minutes", row.durationMinutes ?: JSONObject.NULL)
            .put("priority", row.priority)
            .put("matrix_urgent", row.matrixUrgent ?: JSONObject.NULL)
            .put("matrix_important", row.matrixImportant ?: JSONObject.NULL)
            .put("is_completed", row.isCompleted)
            .put("completed_at", row.completedAt ?: JSONObject.NULL)
            .put("series_id", row.seriesId ?: JSONObject.NULL)
            .put("original_day", row.originalDay ?: JSONObject.NULL)
            .put("is_template", row.isTemplate)
            .put("is_skipped", row.isSkipped)
            .put("sort_order", row.sortOrder)
            .put("created_at", row.createdAt)
            .put("updated_at", row.updatedAt)

    fun subtask(row: SubtaskEntity) =
        JSONObject()
            .put("id", row.id)
            .put("task_id", row.taskId)
            .put("title", row.title)
            .put("is_completed", row.isCompleted)
            .put("sort_order", row.sortOrder)
            .put("created_at", row.createdAt)
            .put("updated_at", row.updatedAt)

    fun series(row: RecurringSeriesEntity) =
        JSONObject()
            .put("id", row.id)
            .put("rule", row.rule)
            .put("anchor_day", row.anchorDay)
            .put("template_task_id", row.templateTaskId)
            .put("end_before", row.endBefore ?: JSONObject.NULL)
            .put("updated_at", row.updatedAt)

    fun image(row: TaskImage) =
        JSONObject()
            .put("id", row.id)
            .put("task_id", row.taskId)
            .put("file_name", row.fileName)
            .put("created_at", row.createdAt)

    fun tagLink(link: TagLink, updatedAt: Long) =
        JSONObject()
            .put("task_id", link.taskId)
            .put("tag_id", link.tagId)
            .put("updated_at", updatedAt)

    fun parseList(row: JSONObject) =
        ListEntity(
            id = row.getString("id"),
            name = row.getString("name"),
            icon = row.optString("icon", "📋"),
            color = row.optInt("color"),
            sortOrder = row.optLong("sort_order"),
            createdAt = row.optLong("created_at"),
            updatedAt = row.optLong("updated_at"),
        )

    fun parseTag(row: JSONObject) =
        TagEntity(
            id = row.getString("id"),
            name = row.getString("name"),
            normalizedName = row.getString("normalized_name"),
            color = row.optInt("color"),
            createdAt = row.optLong("created_at"),
            updatedAt = row.optLong("updated_at"),
        )

    fun parseTask(row: JSONObject) =
        TaskEntity(
            id = row.getString("id"),
            title = row.getString("title"),
            notes = row.optString("notes"),
            listId = row.getString("list_id"),
            dueDay = row.nullableLong("due_day"),
            minuteOfDay = row.nullableInt("minute_of_day"),
            durationMinutes = row.nullableInt("duration_minutes"),
            priority = row.optInt("priority"),
            matrixUrgent = row.nullableBool("matrix_urgent"),
            matrixImportant = row.nullableBool("matrix_important"),
            isCompleted = row.optBoolean("is_completed"),
            completedAt = row.nullableLong("completed_at"),
            seriesId = row.nullableString("series_id"),
            originalDay = row.nullableLong("original_day"),
            isTemplate = row.optBoolean("is_template"),
            isSkipped = row.optBoolean("is_skipped"),
            sortOrder = row.optLong("sort_order"),
            createdAt = row.optLong("created_at"),
            updatedAt = row.optLong("updated_at"),
        )

    fun parseSubtask(row: JSONObject) =
        SubtaskEntity(
            id = row.getString("id"),
            taskId = row.getString("task_id"),
            title = row.getString("title"),
            isCompleted = row.optBoolean("is_completed"),
            sortOrder = row.optLong("sort_order"),
            createdAt = row.optLong("created_at"),
            updatedAt = row.optLong("updated_at"),
        )

    fun parseSeries(row: JSONObject) =
        RecurringSeriesEntity(
            id = row.getString("id"),
            rule = row.getString("rule"),
            anchorDay = row.getLong("anchor_day"),
            templateTaskId = row.getString("template_task_id"),
            endBefore = row.nullableLong("end_before"),
            updatedAt = row.optLong("updated_at"),
        )

    fun parseImage(row: JSONObject) =
        TaskImage(
            id = row.getString("id"),
            taskId = row.getString("task_id"),
            fileName = row.getString("file_name"),
            createdAt = row.optLong("created_at"),
        )

    fun parseArray(raw: String): List<JSONObject> {
        val array = JSONArray(raw)
        return (0 until array.length()).map { array.getJSONObject(it) }
    }
}

internal fun JSONObject.nullableLong(key: String): Long? =
    if (!has(key) || isNull(key)) null else getLong(key)

internal fun JSONObject.nullableInt(key: String): Int? =
    if (!has(key) || isNull(key)) null else getInt(key)

internal fun JSONObject.nullableString(key: String): String? =
    if (!has(key) || isNull(key) || optString(key).isBlank()) null else getString(key)

internal fun JSONObject.nullableBool(key: String): Boolean? =
    if (!has(key) || isNull(key)) null else getBoolean(key)
