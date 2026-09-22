package com.example.pix.cloud

import androidx.room.withTransaction
import com.example.pix.data.*

class OutboxRecorder(private val db: PixDatabase) {
    private val dao = db.dao()
    private val outbox = db.syncDao()

    data class Snapshot(
        val lists: Map<String, Long>,
        val tags: Map<String, Long>,
        val tasks: Map<String, Long>,
        val subtasks: Map<String, Long>,
        val series: Map<String, Long>,
        val links: Set<String>,
        val images: Set<String>,
    )

    suspend fun snapshot(): Snapshot =
        Snapshot(
            dao.listStamps().associate { it.id to it.updatedAt },
            dao.tagStamps().associate { it.id to it.updatedAt },
            dao.taskStamps().associate { it.id to it.updatedAt },
            dao.subtaskStamps().associate { it.id to it.updatedAt },
            dao.seriesStamps().associate { it.id to it.updatedAt },
            dao.tagLinks().map { linkId(it.taskId, it.tagId) }.toSet(),
            dao.imageIds().toSet(),
        )

    suspend fun record(before: Snapshot) {
        val after = snapshot()
        diff(before.lists, after.lists, "lists") { dao.listById(it)?.let(SyncCodec::list) }
        diff(before.tags, after.tags, "tags") { dao.tagById(it)?.let(SyncCodec::tag) }
        diff(before.tasks, after.tasks, "tasks") { dao.task(it)?.let(SyncCodec::task) }
        diff(before.subtasks, after.subtasks, "subtasks") {
            dao.subtaskById(it)?.let(SyncCodec::subtask)
        }
        diff(before.series, after.series, "recurring_series") {
            dao.series(it)?.let(SyncCodec::series)
        }
        (after.links - before.links).forEach { id ->
            val (taskId, tagId) = id.split('|', limit = 2)
            enqueue(
                "task_tags",
                id,
                "UPSERT",
                SyncCodec.tagLink(TagLink(taskId, tagId), System.currentTimeMillis()).toString(),
            )
        }
        (before.links - after.links).forEach { enqueue("task_tags", it, "DELETE", "{}") }
        (after.images - before.images).forEach { id ->
            dao.imageById(id)?.let { enqueue("task_images", id, "UPSERT", SyncCodec.image(it).toString()) }
        }
        (before.images - after.images).forEach { enqueue("task_images", it, "DELETE", "{}") }
    }

    suspend fun enqueueAll() {
        record(
            Snapshot(
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet(),
                emptySet(),
            )
        )
    }

    private suspend fun diff(
        before: Map<String, Long>,
        after: Map<String, Long>,
        type: String,
        payload: suspend (String) -> org.json.JSONObject?,
    ) {
        after.forEach { (id, stamp) ->
            if (before[id] != stamp) {
                payload(id)?.let { enqueue(type, id, "UPSERT", it.toString()) }
            }
        }
        (before.keys - after.keys).forEach { enqueue(type, it, "DELETE", "{}") }
    }

    suspend fun enqueue(type: String, id: String, operation: String, payload: String) {
        if (operation == "DELETE") {
            outbox.removeMatching(type, id, "UPSERT")
        } else {
            outbox.removeMatching(type, id, "UPSERT")
            outbox.removeMatching(type, id, "DELETE")
        }
        outbox.insert(
            SyncOutboxEntity(entityType = type, entityId = id, operation = operation, payload = payload)
        )
    }

    companion object {
        fun linkId(taskId: String, tagId: String) = "$taskId|$tagId"
    }
}

suspend fun <T> PixDatabase.tracked(block: suspend () -> T): T {
    val recorder = OutboxRecorder(this)
    return withTransaction {
        val before = recorder.snapshot()
        val value = block()
        recorder.record(before)
        value
    }
}
