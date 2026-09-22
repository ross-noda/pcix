package com.example.pix.cloud

import android.util.Log
import androidx.room.withTransaction
import com.example.pix.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

enum class CloudSyncStatus {
    Idle,
    Syncing,
    Offline,
    Error,
    Unconfigured,
}

class SyncEngine(
    private val db: PixDatabase,
    private val auth: AuthRepository,
    private val remote: RemoteDataSource,
    private val onReminders: () -> Unit,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status
    private val _lastSuccess = MutableStateFlow(0L)
    val lastSuccess: StateFlow<Long> = _lastSuccess

    suspend fun synchronize(): Boolean =
        mutex.withLock {
            if (!remote.configured()) {
                _status.value = CloudSyncStatus.Unconfigured
                return false
            }
            val user = (auth.state.value as? AuthState.Authenticated)?.user ?: auth.session()?.user
            if (user == null) {
                _status.value = CloudSyncStatus.Idle
                return false
            }
            _status.value = CloudSyncStatus.Syncing
            return try {
                var token = auth.accessToken()
                if (token == null) {
                    if ((auth.state.value as? AuthState.Authenticated)?.offline == true) {
                        throw java.io.IOException("auth refresh unavailable")
                    }
                    throw RemoteDataSource.Unauthorized()
                }
                try {
                    push(token, user.id)
                    pull(token, user.id)
                } catch (_: RemoteDataSource.Unauthorized) {
                    token = auth.recoverFromUnauthorized() ?: throw RemoteDataSource.Unauthorized()
                    try {
                        push(token, user.id)
                        pull(token, user.id)
                    } catch (second: RemoteDataSource.Unauthorized) {
                        auth.invalidateSession()
                        throw second
                    }
                }
                val now = System.currentTimeMillis()
                _lastSuccess.value = now
                _status.value = CloudSyncStatus.Idle
                onReminders()
                true
            } catch (_: java.io.IOException) {
                Log.w("PcixSync", "offline or network error")
                _status.value = CloudSyncStatus.Offline
                false
            } catch (_: RemoteDataSource.Unauthorized) {
                Log.w("PcixSync", "session rejected")
                _status.value = CloudSyncStatus.Error
                false
            } catch (error: Exception) {
                Log.w("PcixSync", "sync failed: ${error.javaClass.simpleName}")
                _status.value = CloudSyncStatus.Error
                false
            }
        }

    private suspend fun push(token: String, userId: String) {
        val pending = db.syncDao().pending()
        for (row in pending) {
            try {
                if (row.operation == "DELETE") {
                    val parts = row.entityId.split('|', limit = 2)
                    remote.tombstone(token, row.entityType, parts[0], parts.getOrNull(1))
                } else {
                    remote.upsert(token, row.entityType, userId, JSONObject(row.payload))
                }
                db.syncDao().remove(row.id)
            } catch (unauthorized: RemoteDataSource.Unauthorized) {
                throw unauthorized
            } catch (io: java.io.IOException) {
                db.syncDao().attempted(row.id, System.currentTimeMillis())
                throw io
            } catch (error: Exception) {
                Log.w("PcixSync", "outbox retry ${row.entityType}")
                db.syncDao().attempted(row.id, System.currentTimeMillis())
                throw error
            }
        }
    }

    private suspend fun pull(token: String, accountId: String) {
        val tables =
            listOf(
                "lists",
                "tags",
                "tasks",
                "recurring_series",
                "subtasks",
                "task_tags",
                "task_images",
            )
        val state = db.syncDao().state(accountId)
        var checkpoint = state?.checkpoint
        var newest = checkpoint
        for (table in tables) {
            var offset = 0
            do {
                val page = remote.pull(token, table, checkpoint, offset)
                apply(table, page.rows)
                newest = maxOfNullable(newest, page.newest)
                offset += page.rows.size
                if (!page.more) break
            } while (page.rows.isNotEmpty())
        }
        db.syncDao()
            .saveState(
                SyncStateEntity(accountId, newest, System.currentTimeMillis())
            )
        _lastSuccess.value = System.currentTimeMillis()
    }

    private suspend fun apply(table: String, rows: List<JSONObject>) {
        if (rows.isEmpty()) return
        db.withTransaction {
            rows.forEach { row -> applyRow(table, row) }
        }
    }

    private suspend fun applyRow(table: String, row: JSONObject) {
        val deleted = !row.isNull("deleted_at")
        val updated = row.optLong("updated_at")
        val id =
            if (table == "task_tags") OutboxRecorder.linkId(row.getString("task_id"), row.getString("tag_id"))
            else row.getString("id")
        val pending = db.syncDao().pendingFor(table, id)
        val pendingDelete = pending.any { it.operation == "DELETE" }
        val pendingUpsert =
            pending.filter { it.operation == "UPSERT" }.maxOfOrNull {
                runCatching { JSONObject(it.payload).optLong("updated_at") }.getOrDefault(0)
            }
        val localUpdated = localStamp(table, id)
        when (
            ConflictPolicy.applyRemote(deleted, updated, localUpdated, pendingDelete, pendingUpsert)
        ) {
            RemoteAction.SKIP -> Unit
            RemoteAction.DELETE -> deleteLocal(table, row)
            RemoteAction.UPSERT ->
                runCatching { upsertLocal(table, row) }
                    .onFailure { Log.w("PcixSync", "skip malformed $table") }
        }
    }

    private suspend fun localStamp(table: String, id: String): Long? =
        when (table) {
            "lists" -> db.dao().listById(id)?.updatedAt
            "tags" -> db.dao().tagById(id)?.updatedAt
            "tasks" -> db.dao().task(id)?.updatedAt
            "subtasks" -> db.dao().subtaskById(id)?.updatedAt
            "recurring_series" -> db.dao().series(id)?.updatedAt
            "task_images" -> db.dao().imageById(id)?.createdAt
            "task_tags" -> if (db.dao().tagLinks().any { OutboxRecorder.linkId(it.taskId, it.tagId) == id }) 1 else null
            else -> null
        }

    private suspend fun deleteLocal(table: String, row: JSONObject) {
        when (table) {
            "lists" -> {
                val id = row.getString("id")
                if (id != INBOX_ID) {
                    db.dao().moveToInbox(id)
                    db.dao().deleteList(id)
                }
            }
            "tags" -> db.dao().deleteTag(row.getString("id"))
            "tasks" -> db.dao().deleteTask(row.getString("id"))
            "subtasks" -> db.dao().deleteSubtask(row.getString("id"))
            "recurring_series" -> db.dao().deleteTask(row.optString("template_task_id"))
            "task_images" -> db.dao().deleteImage(row.getString("id"))
            "task_tags" -> db.dao().detachTag(row.getString("task_id"), row.getString("tag_id"))
        }
    }

    private suspend fun upsertLocal(table: String, row: JSONObject) {
        when (table) {
            "lists" -> db.dao().replaceList(SyncCodec.parseList(row))
            "tags" -> db.dao().replaceTag(SyncCodec.parseTag(row))
            "tasks" -> db.dao().replaceTask(SyncCodec.parseTask(row))
            "subtasks" -> db.dao().replaceSubtask(SyncCodec.parseSubtask(row))
            "recurring_series" -> db.dao().replaceSeries(SyncCodec.parseSeries(row))
            "task_images" -> db.dao().insertImage(SyncCodec.parseImage(row))
            "task_tags" ->
                db.dao().attachTag(TaskTagCrossRef(row.getString("task_id"), row.getString("tag_id")))
        }
    }

    suspend fun pendingCount() = db.syncDao().pending().size

    private fun maxOfNullable(a: String?, b: String?): String? =
        listOfNotNull(a, b).maxOrNull()
}
