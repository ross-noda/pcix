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
    private val remote: SyncRemote,
    private val onReminders: () -> Unit,
) {
    private val mutex = Mutex()
    private val _status = MutableStateFlow(CloudSyncStatus.Idle)
    val status: StateFlow<CloudSyncStatus> = _status
    private val _lastSuccess = MutableStateFlow(0L)
    val lastSuccess: StateFlow<Long> = _lastSuccess
    private var loadedAccount: String? = null

    /** Hydrates Settings -> Data from Room before a network worker necessarily runs. */
    suspend fun restoreForAccount(accountId: String) =
        mutex.withLock { restoreForAccountLocked(accountId) }

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
            if (loadedAccount != user.id) restoreForAccountLocked(user.id)
            persistStatus(user.id, CloudSyncStatus.Syncing)
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
                val state = db.syncDao().state(user.id) ?: SyncStateEntity(user.id)
                db.syncDao().saveState(state.copy(lastSuccessAt = now, status = CloudSyncStatus.Idle.name))
                _lastSuccess.value = now
                _status.value = CloudSyncStatus.Idle
                true
            } catch (_: java.io.IOException) {
                Log.w("PcixSync", "offline or network error")
                persistStatus(user.id, CloudSyncStatus.Offline)
                false
            } catch (_: RemoteDataSource.Unauthorized) {
                Log.w("PcixSync", "session rejected")
                persistStatus(user.id, CloudSyncStatus.Error)
                false
            } catch (error: Exception) {
                Log.w("PcixSync", "sync failed: ${error.javaClass.simpleName}")
                persistStatus(user.id, CloudSyncStatus.Error)
                false
            }
        }

    private suspend fun restoreForAccountLocked(accountId: String) {
        var state = db.syncDao().state(accountId) ?: SyncStateEntity(accountId)
        // A persisted Syncing means the previous process/worker ended before publishing a terminal
        // state. Mark it Error instead of leaving Settings permanently stuck on "syncing".
        val restored = parseStatus(state.status)
        if (restored == CloudSyncStatus.Syncing) {
            state = state.copy(status = CloudSyncStatus.Error.name)
            db.syncDao().saveState(state)
        }
        loadedAccount = accountId
        _lastSuccess.value = state.lastSuccessAt
        _status.value = parseStatus(state.status)
    }

    private suspend fun persistStatus(accountId: String, status: CloudSyncStatus) {
        val state = db.syncDao().state(accountId) ?: SyncStateEntity(accountId)
        db.syncDao().saveState(state.copy(status = status.name))
        loadedAccount = accountId
        _lastSuccess.value = state.lastSuccessAt
        _status.value = status
    }

    private fun parseStatus(raw: String): CloudSyncStatus =
        runCatching { CloudSyncStatus.valueOf(raw) }
            .getOrDefault(CloudSyncStatus.Error)
            .let { if (it == CloudSyncStatus.Unconfigured) CloudSyncStatus.Error else it }

    private suspend fun push(token: String, accountId: String) {
        // The outbox is final-state based, so cross-entity ordering can be dependency-safe without
        // changing per-identity LWW semantics. Create/update parents before children; send all live
        // final states before deletes; for deletes, detach/delete children before their parents.
        val pending = db.syncDao().pending().sortedWith(outboxOrder)
        for (row in pending) {
            try {
                val ack = remote.push(token, row)
                validateAck(row, ack)
                db.withTransaction {
                    // A newer local mutation may already have replaced this outbox id while the
                    // request was in flight. Removing by id therefore acknowledges only the exact
                    // snapshot that was sent.
                    if (ack.deleted) {
                        deleteLocal(row.entityType, row.entityId)
                        db.syncDao().removeEntity(row.entityType, row.entityId)
                    } else {
                        db.syncDao().remove(row.id)
                    }
                    db.syncDao()
                        .saveVersion(
                            SyncEntityVersionEntity(
                                accountId = accountId,
                                entityType = row.entityType,
                                entityId = row.entityId,
                                serverVersion = ack.serverVersion,
                                deleted = ack.deleted,
                            )
                        )
                }
                if (row.entityType == "tasks" && ack.deleted) onReminders()
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


    private val outboxOrder =
        compareBy<SyncOutboxEntity>(
            { if (it.operation == OutboxRecorder.UPSERT) 0 else 1 },
            {
                if (it.operation == OutboxRecorder.UPSERT) {
                    when (it.entityType) {
                        "lists" -> 0
                        "tags" -> 1
                        "tasks" -> 2
                        "recurring_series" -> 3
                        "subtasks" -> 4
                        "task_tags", "task_images" -> 5
                        else -> 99
                    }
                } else {
                    when (it.entityType) {
                        "task_tags", "task_images", "subtasks", "recurring_series" -> 0
                        "tasks" -> 1
                        "tags" -> 2
                        "lists" -> 3
                        else -> 99
                    }
                }
            },
            { it.createdAt },
            { it.id },
        )

    private fun validateAck(row: SyncOutboxEntity, ack: PushAck) {
        val expectedParts = row.entityId.split('|', limit = 2)
        val expectedId = expectedParts[0]
        val expectedId2 = if (row.entityType == "task_tags") expectedParts.getOrNull(1) else null
        if (
            ack.mutationId != row.id ||
                ack.entityType != row.entityType ||
                ack.entityId != expectedId ||
                ack.entityId2 != expectedId2 ||
                ack.serverVersion <= 0
        ) {
            throw RemoteDataSource.ProtocolError("push ack identity mismatch")
        }
        if (ack.outcome !in setOf("APPLIED", "DELETED", "TOMBSTONED")) {
            throw RemoteDataSource.ProtocolError("unknown push outcome")
        }
        if (ack.outcome == "APPLIED" && ack.deleted) {
            throw RemoteDataSource.ProtocolError("inconsistent push ack")
        }
        if (ack.outcome != "APPLIED" && !ack.deleted) {
            throw RemoteDataSource.ProtocolError("inconsistent tombstone ack")
        }
    }

    private suspend fun pull(token: String, accountId: String) {
        val initial = db.syncDao().state(accountId) ?: SyncStateEntity(accountId)
        val checkpoint = initial.checkpoint?.toLongOrNull() ?: 0L
        val through = remote.snapshot(token)
        if (through < checkpoint) throw RemoteDataSource.ProtocolError("server version moved backwards")

        var cursor = checkpoint
        while (cursor < through) {
            val page = remote.pull(token, cursor, through)
            if (page.rows.isEmpty()) {
                throw RemoteDataSource.ProtocolError("pull ended before snapshot high-water mark")
            }
            if (page.nextCursor <= cursor || page.nextCursor > through) {
                throw RemoteDataSource.ProtocolError("invalid pull cursor")
            }
            val touchedReminders = apply(accountId, page.rows)
            if (touchedReminders) onReminders()
            cursor = page.nextCursor
            if (!page.more && cursor < through) {
                throw RemoteDataSource.ProtocolError("pull page stopped before snapshot high-water mark")
            }
            if (!page.more) break
        }

        // Advance only after every requested page was parsed and committed. If any page throws,
        // this write is never reached; a retry starts from the previous global checkpoint.
        val current = db.syncDao().state(accountId) ?: initial
        db.syncDao().saveState(current.copy(checkpoint = through.toString()))
    }

    private suspend fun apply(accountId: String, rows: List<RemoteChange>): Boolean {
        if (rows.isEmpty()) return false
        var touchedReminders = false
        db.withTransaction {
            rows.forEach { change ->
                if (applyChange(accountId, change) && change.entityType == "tasks") {
                    touchedReminders = true
                }
            }
        }
        return touchedReminders
    }

    /** Returns true when the local canonical row actually changed. */
    private suspend fun applyChange(accountId: String, change: RemoteChange): Boolean {
        RemoteDataSource.validateEntity(change.entityType)
        val localId = localIdentity(change)
        val appliedVersion =
            db.syncDao().version(accountId, change.entityType, localId)?.serverVersion
        val pending = db.syncDao().pendingFor(change.entityType, localId).singleOrNull()
        val action =
            ConflictPolicy.applyRemote(
                remoteDeleted = change.deleted,
                remoteVersion = change.serverVersion,
                appliedVersion = appliedVersion,
                pendingOperation = pending?.operation,
            )
        var changed = false
        when (action) {
            RemoteAction.SKIP -> Unit
            RemoteAction.DELETE -> {
                if (change.entityType == "lists" && change.entityId == INBOX_ID) {
                    throw RemoteDataSource.ProtocolError("remote attempted to delete Inbox")
                }
                deleteLocal(change.entityType, localId)
                // A server tombstone is terminal for this identity. In particular, discard an
                // offline stale UPSERT so it cannot resurrect the record on the next push.
                db.syncDao().removeEntity(change.entityType, localId)
                changed = true
            }
            RemoteAction.UPSERT -> {
                val payload = change.payload ?: throw RemoteDataSource.ProtocolError("missing payload")
                validatePayloadIdentity(change, payload)
                upsertLocal(change.entityType, payload)
                changed = true
            }
        }
        if (appliedVersion == null || appliedVersion < change.serverVersion) {
            db.syncDao()
                .saveVersion(
                    SyncEntityVersionEntity(
                        accountId = accountId,
                        entityType = change.entityType,
                        entityId = localId,
                        serverVersion = change.serverVersion,
                        deleted = change.deleted,
                    )
                )
        }
        return changed
    }

    private fun localIdentity(change: RemoteChange): String =
        if (change.entityType == "task_tags") {
            val tag = change.entityId2 ?: throw RemoteDataSource.ProtocolError("missing task_tags tag id")
            OutboxRecorder.linkId(change.entityId, tag)
        } else change.entityId

    private fun validatePayloadIdentity(change: RemoteChange, row: JSONObject) {
        if (change.entityType == "task_tags") {
            if (
                row.getString("task_id") != change.entityId ||
                    row.getString("tag_id") != change.entityId2
            ) {
                throw RemoteDataSource.ProtocolError("task_tags payload identity mismatch")
            }
        } else if (row.getString("id") != change.entityId) {
            throw RemoteDataSource.ProtocolError("payload identity mismatch")
        }
    }

    private suspend fun deleteLocal(table: String, localId: String) {
        when (table) {
            "lists" -> {
                if (localId != INBOX_ID) {
                    db.dao().moveToInbox(localId)
                    db.dao().deleteList(localId)
                }
            }
            "tags" -> db.dao().deleteTag(localId)
            "tasks" -> db.dao().deleteTask(localId)
            "subtasks" -> db.dao().deleteSubtask(localId)
            // Deleting a series tombstone must not delete its template/task graph. Any task/template
            // deletion required by a scoped recurrence edit arrives as its own task tombstone.
            "recurring_series" -> db.dao().deleteSeries(localId)
            "task_images" -> db.dao().deleteImage(localId)
            "task_tags" -> {
                val parts = localId.split('|', limit = 2)
                if (parts.size != 2) throw RemoteDataSource.ProtocolError("invalid task_tags identity")
                db.dao().detachTag(parts[0], parts[1])
            }
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
}
