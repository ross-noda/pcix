package com.example.pix.cloud

import androidx.room.withTransaction
import com.example.pix.data.*

/**
 * Records the final cloud-visible state produced by a local Room mutation.
 *
 * This is deliberately a state-based outbox (`UPSERT` / `DELETE`) rather than a command log. Multiple
 * local updates to the same entity are therefore coalesced to one final row. Replacing a queued row
 * always uses a new outbox id so a sync already in flight can acknowledge only the snapshot it sent;
 * a newer local mutation remains queued.
 */
class OutboxRecorder(private val db: PixDatabase) {
    private val dao = db.dao()
    private val outbox = db.syncDao()

    data class Snapshot(
        val lists: Map<String, ListEntity>,
        val tags: Map<String, TagEntity>,
        val tasks: Map<String, TaskEntity>,
        val series: Map<String, RecurringSeriesEntity>,
        val links: Set<String>,
        val images: Map<String, TaskImage>,
        val habitGroups: Map<String, HabitGroupEntity> = emptyMap(),
        val habits: Map<String, HabitEntity> = emptyMap(),
        val habitRules: Map<String, HabitRuleEntity> = emptyMap(),
        val habitLogs: Map<String, HabitLogEntity> = emptyMap(),

    )

    /**
     * Compare complete cloud payloads, not only updatedAt stamps. This makes outbox capture robust to
     * same-millisecond edits and prevents a forgotten timestamp bump from silently losing a mutation.
     * Entity updatedAt stays in the payload as metadata; cloud conflicts use server_version instead.
     */
    suspend fun snapshot(): Snapshot =
        Snapshot(
            lists = dao.syncLists().associateBy { it.id },
            tags = dao.syncTags().associateBy { it.id },
            tasks = dao.syncTasks().associateBy { it.id },
            series = dao.syncSeries().associateBy { it.id },
            links = dao.tagLinks().map { linkId(it.taskId, it.tagId) }.toSet(),
            habitGroups = db.habitDao().groups().associateBy { it.id },
            habits = db.habitDao().habits().associateBy { it.id },
            habitRules = db.habitDao().rules().associateBy { it.id },
            habitLogs = db.habitDao().logs().associateBy { it.id },
            images = emptyMap(), // Images and their device-local paths never enter cloud payloads.
        )

    suspend fun record(before: Snapshot) {
        val after = snapshot()
        diff(before.lists, after.lists, "lists") { SyncCodec.list(it).toString() }
        diff(before.tags, after.tags, "tags") { SyncCodec.tag(it).toString() }
        diff(before.tasks, after.tasks, "tasks") { SyncCodec.task(it).toString() }
        diff(before.series, after.series, "recurring_series") { SyncCodec.series(it).toString() }

        diff(before.habitGroups, after.habitGroups, "habit_groups") { HabitCodec.habitGroup(it).toString() }
        diff(before.habits, after.habits, "habits") { HabitCodec.habit(it).toString() }
        diff(before.habitRules, after.habitRules, "habit_rules") { HabitCodec.habitRule(it).toString() }
        diff(before.habitLogs, after.habitLogs, "habit_logs") { HabitCodec.habitLog(it).toString() }

        val now = System.currentTimeMillis()
        (after.links - before.links).forEach { id ->
            val (taskId, tagId) = id.split('|', limit = 2)
            val payload = SyncCodec.tagLink(TagLink(taskId, tagId), now)
            // A link has a composite identity, so explicit re-attach cannot mint a new UUID.
            outbox.deletedLinkVersion(id)?.let { payload.put("restore_after_version", it) }
            enqueue(
                "task_tags",
                id,
                UPSERT,
                payload.toString(),
            )
        }
        (before.links - after.links).forEach { enqueue("task_tags", it, DELETE, "{}") }

    }

    suspend fun enqueueAll() {
        val empty =
            Snapshot(
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptyMap(),
                emptySet(),
                emptyMap(),
            )
        if (db.inTransaction()) record(empty) else db.withTransaction { record(empty) }
    }

    private suspend fun <T> diff(
        before: Map<String, T>,
        after: Map<String, T>,
        type: String,
        payload: (T) -> String,
    ) {
        after.forEach { (id, value) ->
            if (before[id] != value) enqueue(type, id, UPSERT, payload(value))
        }
        (before.keys - after.keys).forEach { enqueue(type, it, DELETE, "{}") }
    }

    /**
     * Coalescing rule: keep exactly one pending final-state operation per entity identity.
     *
     * UPSERT -> UPSERT becomes the latest UPSERT. UPSERT -> DELETE becomes DELETE. DELETE -> UPSERT
     * may replace a delete that is still purely local and has never reached the server (for example a
     * local restore before sync). Once the server has committed a tombstone, that UUID is terminal and
     * a later UPSERT is acknowledged as TOMBSTONED; explicit recreation must use a new UUID.
     */
    private suspend fun enqueue(type: String, id: String, operation: String, payload: String) {
        require(operation == UPSERT || operation == DELETE)
        val current = outbox.pendingFor(type, id).singleOrNull()
        if (current?.operation == operation && current.payload == payload) {
            // Same logical version: preserve id/retry metadata instead of manufacturing new work.
            return
        }
        outbox.removeEntity(type, id)
        outbox.insert(
            SyncOutboxEntity(
                entityType = type,
                entityId = id,
                operation = operation,
                payload = payload,
            )
        )
    }

    companion object {
        const val UPSERT = "UPSERT"
        const val DELETE = "DELETE"

        fun linkId(taskId: String, tagId: String) = "$taskId|$tagId"
    }
}

/**
 * Local cloud-relevant mutation boundary. Room data and its outbox delta commit or roll back together.
 */
suspend fun <T> PixDatabase.tracked(block: suspend () -> T): T {
    val recorder = OutboxRecorder(this)
    return withTransaction {
        val before = recorder.snapshot()
        val value = block()
        recorder.record(before)
        value
    }
}
