package com.example.pix.cloud

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.saveChildForTest
import com.example.pix.data.*
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class SyncEngineProtocolTest {
    private val databases = mutableListOf<PixDatabase>()
    private val user = PcixUser("11111111-1111-1111-1111-111111111111", "sync@example.test")

    @After
    fun closeDatabases() {
        databases.forEach { it.close() }
        databases.clear()
    }

    @Test fun twoDevicesPreserveParentChildTagsRecurrenceAndExcludeImages() = runBlocking {
        val server = FakeServer()
        val a = newDb(); val b = newDb()
        val repoA = TaskRepository(a); val repoB = TaskRepository(b)
        val parent = task("full-parent", "Parent").copy(dueDay = 24000)
        val child = task("full-child", "Child").copy(parentTaskId = parent.id)
        val tag = repoA.saveTag("Shared tag", 2)
        repoA.create(parent, setOf(tag)); repoA.create(child)
        val template = parent.copy(id = "full-template", isTemplate = true)
        val series = RecurringSeriesEntity(id = "full-series", rule = "FREQ=DAILY", anchorDay = 24000, templateTaskId = template.id)
        a.tracked {
            a.dao().insertTask(template)
            a.dao().insertSeries(series)
            a.dao().attachSeries(parent.id, series.id, 24000)
        }
        repoA.addImage(TaskImage(id = "local-image", taskId = parent.id, fileName = "local.jpg"))
        val syncA = SyncEngine(a, authenticatedAuth(), server) {}
        val syncB = SyncEngine(b, authenticatedAuth(), server) {}
        assertTrue(syncA.synchronize()); assertTrue(syncB.synchronize())
        assertEquals(parent.id, b.dao().task(child.id)!!.parentTaskId)
        assertEquals(series, b.dao().series(series.id))
        assertEquals(setOf(tag), b.dao().tagIds(parent.id).toSet())
        assertTrue(b.dao().images(parent.id).isEmpty())
        assertFalse(server.pushOrder.any { it.startsWith("task_images:") })
        repoB.edit(b.dao().task(child.id)!!.copy(title = "Edited child"), emptySet())
        assertTrue(syncB.synchronize()); assertTrue(syncA.synchronize())
        assertEquals("Edited child", a.dao().task(child.id)!!.title)
        repoB.complete(child.id, true)
        repoB.complete(parent.id, true)
        assertTrue(syncB.synchronize()); assertTrue(syncA.synchronize())
        assertTrue(a.dao().task(child.id)!!.isCompleted)
        assertTrue(a.dao().task(parent.id)!!.isCompleted)
        assertNotNull(a.dao().occurrence(series.id, 24001))
        server.seedUpsert("task_images", "remote-image", SyncCodec.image(TaskImage(id = "remote-image", taskId = parent.id, fileName = "missing.jpg")))
        assertTrue(syncB.synchronize())
        assertTrue(b.dao().images(parent.id).isEmpty())
    }

    @Test fun stoppedWorkerKeepsOutboxAndDoesNotLeaveSyncingStatus() = runBlocking {
        val db = newDb()
        TaskRepository(db).create(task("stop", "Pending"))
        val pending = db.syncDao().pending().map { it.id }
        val remote = object : SyncRemote by FakeServer() {
            override suspend fun push(token: String, row: SyncOutboxEntity): PushAck =
                throw kotlinx.coroutines.CancellationException("Worker stopped")
        }
        val engine = SyncEngine(db, authenticatedAuth(), remote) {}
        try { engine.synchronize(); fail("Cancellation must propagate") }
        catch (_: kotlinx.coroutines.CancellationException) { }
        assertEquals(CloudSyncStatus.Error, engine.status.value)
        assertEquals(CloudSyncStatus.Error.name, db.syncDao().state(user.id)!!.status)
        assertEquals(0L, engine.lastSuccess.value)
        assertEquals(pending, db.syncDao().pending().map { it.id })
    }

    @Test fun twoLocalDevicesConvergeAfterOfflineCreateUpdatesInBothDirectionsAndDelete() = runBlocking {
        val server = FakeServer()
        val a = newDb(); val b = newDb()
        val repoA = TaskRepository(a); val repoB = TaskRepository(b)
        val syncA = SyncEngine(a, authenticatedAuth(), server) {}
        val syncB = SyncEngine(b, authenticatedAuth(), server) {}
        val tag = repoA.saveTag("Shared", 2)
        val task = task("round-trip", "Created offline").copy(notes = "## Notes\n- [ ] One", durationMinutes = 45, dueDay = 24000, minuteOfDay = 600,
            matrixUrgent = true, matrixImportant = false, sortOrder = 123)
        repoA.create(task, setOf(tag))
        assertNotNull(a.dao().task(task.id))
        assertTrue(a.syncDao().pending().isNotEmpty())
        assertTrue(syncA.synchronize()); assertTrue(syncB.synchronize())
        assertEquals(task.notes, b.dao().task(task.id)!!.notes)
        assertEquals(45, b.dao().task(task.id)!!.durationMinutes)
        assertEquals(false, b.dao().task(task.id)!!.matrixImportant)
        assertEquals(setOf(tag), b.dao().tagIds(task.id).toSet())
        repoA.edit(a.dao().task(task.id)!!.copy(title = "A update"), setOf(tag))
        assertTrue(syncA.synchronize()); assertTrue(syncB.synchronize())
        assertEquals("A update", b.dao().task(task.id)!!.title)
        repoB.edit(b.dao().task(task.id)!!.copy(title = "B update", notes = "- [x] One"), setOf(tag))
        assertTrue(syncB.synchronize()); assertTrue(syncA.synchronize())
        assertEquals("B update", a.dao().task(task.id)!!.title)
        assertEquals("- [x] One", a.dao().task(task.id)!!.notes)
        repoA.delete(task.id)
        assertTrue(syncA.synchronize()); assertTrue(syncB.synchronize())
        assertNull(a.dao().task(task.id)); assertNull(b.dao().task(task.id))
    }

    @Test fun permanentServerFailureKeepsPendingMutationAndLastSuccessAcrossRestart() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        val auth = authenticatedAuth()
        val repo = TaskRepository(db)
        val good = SyncEngine(db, auth, server) {}
        assertTrue(good.synchronize())
        val last = good.lastSuccess.value
        repo.create(task("pending-config", "Keep this offline edit"))
        val pending = db.syncDao().pending().map { it.id }
        val failing = object : SyncRemote by server {
            override suspend fun push(token: String, row: SyncOutboxEntity): PushAck =
                throw SyncHttpFailure("push", 404, "PGRST202")
        }
        val engine = SyncEngine(db, auth, failing) {}
        assertFalse(engine.synchronize())
        assertEquals(CloudSyncStatus.SchemaMissing, engine.status.value)
        assertEquals(last, engine.lastSuccess.value)
        assertEquals(pending, db.syncDao().pending().map { it.id })
        assertEquals("Keep this offline edit", db.dao().task("pending-config")!!.title)
        val restarted = SyncEngine(db, auth, server) {}
        restarted.restoreForAccount(user.id)
        assertEquals(CloudSyncStatus.SchemaMissing, restarted.status.value)
        assertTrue(restarted.synchronize())
        assertTrue(db.syncDao().pending().isEmpty())
    }

    @Test
    fun acknowledgedCanonicalPayloadIsStillAppliedByPull() = runBlocking {
        val db = newDb()
        val repository = TaskRepository(db)
        val server = FakeServer()
        val task = task("canonical", "local")
        repository.create(task)
        server.beforeReturn = { row ->
            if (row.entityType == "tasks") server.changes.last().payload!!.put("title", "canonical server value")
        }
        assertTrue(SyncEngine(db, authenticatedAuth(), server) {}.synchronize())
        assertEquals("canonical server value", db.dao().task(task.id)!!.title)
    }

    @Test
    fun canonicalParentUpdatesPreserveChildrenAndLinks() = runBlocking {
        val db = newDb()
        val repo = TaskRepository(db)
        val task = task("parent-graph", "before")
        repo.create(task)
        val sub = TaskEntity(parentTaskId = task.id, title = "child")
        repo.saveChildForTest(sub)
        val tag = repo.saveTag("linked", 1)
        repo.edit(task, setOf(tag))
        val server = FakeServer()
        val engine = SyncEngine(db, authenticatedAuth(), server) {}
        assertTrue(engine.synchronize())
        server.seedUpsert("tasks", task.id, SyncCodec.task(task.copy(title = "after")))
        server.seedUpsert("tags", tag, SyncCodec.tag(db.dao().tagById(tag)!!.copy(name = "renamed", normalizedName = "renamed")))
        assertTrue(engine.synchronize())
        assertEquals(1, db.dao().children(task.id).size)
        assertEquals(1, db.dao().tagIds(task.id).size)
    }

    @Test
    fun accountPreparationBlocksRemoteAccess() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        TaskRepository(db).create(task("pending", "stay local"))
        val engine = SyncEngine(db, authenticatedAuth(), server, accountReady = { false }) {}
        assertFalse(engine.synchronize())
        assertTrue(server.pushOrder.isEmpty())
        assertTrue(db.syncDao().pending().isNotEmpty())
    }

    @Test
    fun acknowledgedOldMutationCannotDropNewerLocalVersion() = runBlocking {
        val db = newDb()
        val repository = TaskRepository(db)
        val auth = authenticatedAuth()
        val server = FakeServer()
        val task = task("task-stale", "old")
        repository.create(task)

        server.beforeReturn = { row ->
            if (row.entityId == task.id) {
                server.beforeReturn = null
                repository.edit(requireNotNull(db.dao().task(task.id)).copy(title = "newer"), emptySet())
            }
        }
        val engine = SyncEngine(db, auth, server) {}
        assertTrue(engine.synchronize())

        val pending = db.syncDao().pendingFor("tasks", task.id).single()
        assertEquals("newer", JSONObject(pending.payload).getString("title"))
        assertEquals("newer", db.dao().task(task.id)!!.title)

        assertTrue(engine.synchronize())
        assertTrue(db.syncDao().pending().isEmpty())
        assertEquals(2, server.changes.count { it.entityType == "tasks" && it.entityId == task.id })
    }

    @Test
    fun lostResponseRetriesSameMutationWithoutSecondServerWrite() = runBlocking {
        val db = newDb()
        val repository = TaskRepository(db)
        val server = FakeServer().apply { throwAfterApplyOnce = true }
        val engine = SyncEngine(db, authenticatedAuth(), server) {}
        val task = task("task-retry", "retry")
        repository.create(task)
        val mutationId = db.syncDao().pendingFor("tasks", task.id).single().id

        assertFalse(engine.synchronize())
        assertEquals(CloudSyncStatus.Offline, engine.status.value)
        assertNotNull(db.syncDao().pendingById(mutationId))
        assertEquals(1, server.changes.size)

        assertTrue(engine.synchronize())
        assertNull(db.syncDao().pendingById(mutationId))
        assertEquals(1, server.changes.size)
        assertEquals(1, server.receipts.size)
    }

    @Test
    fun interruptedPagedPullDoesNotAdvanceCheckpointAndRetryIsIdempotent() = runBlocking {
        val db = newDb()
        val server = FakeServer(pageSize = 2)
        repeat(3) { index -> server.seedUpsert("tasks", "remote-$index", SyncCodec.task(task("remote-$index", "R$index"))) }
        server.failPullCallOnce = 2
        var reminderReconciliations = 0
        val engine = SyncEngine(db, authenticatedAuth(), server) { reminderReconciliations++ }

        assertFalse(engine.synchronize())
        assertNull(db.syncDao().state(user.id)!!.checkpoint)
        assertNull(db.dao().task("remote-0"))
        assertNull(db.dao().task("remote-1"))
        assertNull(db.dao().task("remote-2"))

        assertTrue(engine.synchronize())
        assertEquals(server.version.toString(), db.syncDao().state(user.id)!!.checkpoint)
        assertEquals(3, listOf("remote-0", "remote-1", "remote-2").count { db.dao().task(it) != null })
        assertTrue(reminderReconciliations >= 1)
        assertEquals(3, db.syncDao().versions(user.id).count { it.entityType == "tasks" })
    }

    @Test
    fun dependencySafePushOrdersListBeforeTaskCreatedInSameLocalTransaction() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        val list =
            ListEntity(
                id = "list-parent",
                name = "Parent",
                color = 2,
                sortOrder = 2,
                createdAt = 20,
                updatedAt = 20,
            )
        db.tracked {
            db.dao().insertList(list)
            db.dao().insertTask(task("task-child", "Child").copy(listId = list.id))
        }

        assertTrue(SyncEngine(db, authenticatedAuth(), server) {}.synchronize())
        assertTrue(server.pushOrder.indexOf("lists:${list.id}") < server.pushOrder.indexOf("tasks:task-child"))
    }

    @Test
    fun malformedRemoteRowRollsBackPageAndDoesNotAdvanceCheckpoint() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        server.version += 1
        server.changes +=
            RemoteChange(
                serverVersion = server.version,
                entityType = "tasks",
                entityId = "malformed",
                entityId2 = null,
                operation = OutboxRecorder.UPSERT,
                payload = JSONObject().put("id", "malformed").put("title", "missing list"),
            )
        val engine = SyncEngine(db, authenticatedAuth(), server) {}

        assertFalse(engine.synchronize())
        assertEquals(CloudSyncStatus.InvalidData, engine.status.value)
        assertNull(db.syncDao().state(user.id)!!.checkpoint)
        assertNull(db.dao().task("malformed"))
        assertNull(db.syncDao().version(user.id, "tasks", "malformed"))
    }

    @Test
    fun remoteTaskCreateRescheduleCompletionAndDeleteReconcileReminders() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        var reconciliations = 0
        val engine = SyncEngine(db, authenticatedAuth(), server) { reconciliations++ }
        val base = task("reminder-task", "Reminder").copy(dueDay = 23000, minuteOfDay = 600)

        server.seedUpsert("tasks", base.id, SyncCodec.task(base))
        assertTrue(engine.synchronize())
        assertEquals(1, reconciliations)
        assertEquals(600, db.dao().task(base.id)!!.minuteOfDay)

        server.seedUpsert("tasks", base.id, SyncCodec.task(base.copy(minuteOfDay = 660, updatedAt = 11)))
        assertTrue(engine.synchronize())
        assertEquals(2, reconciliations)
        assertEquals(660, db.dao().task(base.id)!!.minuteOfDay)

        server.seedUpsert(
            "tasks",
            base.id,
            SyncCodec.task(base.copy(minuteOfDay = 660, isCompleted = true, completedAt = 12, updatedAt = 12)),
        )
        assertTrue(engine.synchronize())
        assertEquals(3, reconciliations)
        assertTrue(db.dao().task(base.id)!!.isCompleted)

        server.seedDelete("tasks", base.id)
        assertTrue(engine.synchronize())
        assertEquals(4, reconciliations)
        assertNull(db.dao().task(base.id))
    }

    @Test
    fun deleteOnOneDeviceCannotBeResurrectedByOfflineUpdateOnAnother() = runBlocking {
        val server = FakeServer()
        val dbA = newDb()
        val dbB = newDb()
        val repoA = TaskRepository(dbA)
        val repoB = TaskRepository(dbB)
        val engineA = SyncEngine(dbA, authenticatedAuth(), server) {}
        val engineB = SyncEngine(dbB, authenticatedAuth(), server) {}
        val id = "two-device-task"

        repoA.create(task(id, "initial"))
        assertTrue(engineA.synchronize())
        assertTrue(engineB.synchronize())
        assertEquals("initial", dbB.dao().task(id)!!.title)

        repoB.delete(id)
        assertTrue(engineB.synchronize())
        repoA.edit(dbA.dao().task(id)!!.copy(title = "offline stale edit"), emptySet())
        assertTrue(engineA.synchronize())

        assertNull(dbA.dao().task(id))
        assertNull(dbB.dao().task(id))
        assertTrue(dbA.syncDao().pendingFor("tasks", id).isEmpty())
        assertTrue(server.tombstones.containsKey("tasks|$id|"))
    }

    @Test
    fun recurringSeriesTombstoneDoesNotDeleteTemplateOrOccurrence() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        val template = task("template-1", "Template").copy(isTemplate = true)
        val occurrence =
            task("occurrence-1", "Occurrence").copy(
                seriesId = "series-1",
                originalDay = 21000,
                dueDay = 21000,
            )
        val series =
            RecurringSeriesEntity(
                id = "series-1",
                rule = "FREQ=DAILY",
                anchorDay = 21000,
                templateTaskId = template.id,
                updatedAt = 10,
            )
        server.seedUpsert("tasks", template.id, SyncCodec.task(template))
        server.seedUpsert("tasks", occurrence.id, SyncCodec.task(occurrence))
        server.seedUpsert("recurring_series", series.id, SyncCodec.series(series))
        server.seedDelete("recurring_series", series.id)

        assertTrue(SyncEngine(db, authenticatedAuth(), server) {}.synchronize())
        assertNull(db.dao().series(series.id))
        assertNotNull(db.dao().task(template.id))
        assertNotNull(db.dao().task(occurrence.id))
    }

    @Test
    fun syncStateAndLastSuccessSurviveEngineRecreation() = runBlocking {
        val db = newDb()
        val server = FakeServer()
        val first = SyncEngine(db, authenticatedAuth(), server) {}
        assertTrue(first.synchronize())
        val success = first.lastSuccess.value
        assertTrue(success > 0)

        val restored = SyncEngine(db, authenticatedAuth(), server) {}
        restored.restoreForAccount(user.id)
        assertEquals(CloudSyncStatus.Idle, restored.status.value)
        assertEquals(success, restored.lastSuccess.value)

        db.syncDao().saveState(db.syncDao().state(user.id)!!.copy(status = CloudSyncStatus.Syncing.name))
        val interrupted = SyncEngine(db, authenticatedAuth(), server) {}
        interrupted.restoreForAccount(user.id)
        assertEquals(CloudSyncStatus.Error, interrupted.status.value)
        assertEquals(CloudSyncStatus.Error.name, db.syncDao().state(user.id)!!.status)
    }

    @Test
    fun repeated401InvalidatesAuthSessionAndPersistsSyncError() = runBlocking {
        val db = newDb()
        val auth = authenticatedAuth(refreshResponse = CloudHttp.Response(400, "invalid refresh", emptyMap()))
        val remote = object : SyncRemote {
            override fun configured() = true
            override suspend fun push(token: String, row: SyncOutboxEntity): PushAck = throw RemoteDataSource.Unauthorized()
            override suspend fun snapshot(token: String): Long = throw RemoteDataSource.Unauthorized()
            override suspend fun pull(token: String, after: Long, through: Long, limit: Int): PullPage = throw RemoteDataSource.Unauthorized()
        }
        val engine = SyncEngine(db, auth, remote) {}

        assertFalse(engine.synchronize())
        assertEquals(AuthState.Unauthenticated, auth.state.value)
        assertEquals(CloudSyncStatus.SessionExpired, engine.status.value)
        assertEquals(CloudSyncStatus.SessionExpired.name, db.syncDao().state(user.id)!!.status)
    }

    @Test
    fun hierarchyOfflinePushOrdersParentBeforeChildAndPullResolvesLaterParent() = runBlocking {
        val db=newDb();val repo=TaskRepository(db);val server=FakeServer(pageSize=1)
        val parent=task("z-parent","Parent");repo.create(parent)
        val child=task("a-child","Child").copy(parentTaskId=parent.id,createdAt=1)
        repo.create(child)
        assertTrue(SyncEngine(db,authenticatedAuth(),server) {}.synchronize())
        assertTrue(server.pushOrder.indexOf("tasks:z-parent")<server.pushOrder.indexOf("tasks:a-child"))
        val fresh=newDb();val late=FakeServer(pageSize=1)
        late.seedUpsert("tasks",child.id,SyncCodec.task(child))
        late.seedUpsert("tasks",parent.id,SyncCodec.task(parent))
        val engine=SyncEngine(fresh,authenticatedAuth(),late) {}
        assertTrue(engine.synchronize())
        assertEquals(parent.id,fresh.dao().task(child.id)!!.parentTaskId)
        late.seedDelete("tasks",parent.id)
        assertTrue(engine.synchronize())
        assertNull(fresh.dao().task(child.id)!!.parentTaskId)
    }

    private suspend fun newDb(): PixDatabase {
        val db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        databases += db
        db.dao().insertList(
            ListEntity(
                id = INBOX_ID,
                name = "Inbox",
                color = 8,
                sortOrder = 0,
                createdAt = 1,
                updatedAt = 1,
            )
        )
        return db
    }

    private suspend fun authenticatedAuth(
        refreshResponse: CloudHttp.Response = CloudHttp.Response(200, "{}", emptyMap())
    ): AuthRepository {
        val store = MemorySessionStore(
            AuthSession(
                accessToken = "access",
                refreshToken = "refresh",
                expiresAt = Long.MAX_VALUE / 1000,
                user = user,
            )
        )
        val api = object : AuthApi {
            override fun auth(path: String, body: JSONObject, accessToken: String?) = refreshResponse
            override fun request(method: String, path: String, accessToken: String?, body: String?) =
                CloudHttp.Response(200, "{}", emptyMap())
        }
        return AuthRepository(
                CloudConfig("https://example.test", "anon", ""),
                api,
                store,
                nowSeconds = { 1 },
            )
            .also { it.restore() }
    }

    private fun task(id: String, title: String) =
        TaskEntity(
            id = id,
            title = title,
            listId = INBOX_ID,
            createdAt = 10,
            updatedAt = 10,
            sortOrder = 10,
        )

    private class MemorySessionStore(initial: AuthSession) : SessionStore {
        private var session: AuthSession? = initial
        private var verifier: String? = null
        override fun read() = session
        override fun write(session: AuthSession) { this.session = session }
        override fun clear() { session = null; verifier = null }
        private var signupCode: String? = null
    private var signupAddress: String? = null
    override fun signupVerifier() = signupCode
    override fun signupEmail() = signupAddress
    override fun writeSignup(verifier: String, email: String) { signupCode = verifier; signupAddress = email }
    override fun clearSignup() { signupCode = null; signupAddress = null }
    override fun recoveryVerifier() = verifier
        override fun writeRecoveryVerifier(verifier: String) { this.verifier = verifier }
        override fun clearRecoveryVerifier() { verifier = null }
    }

    private class FakeServer(private val pageSize: Int = 200) : SyncRemote {
        var version = 0L
        val changes = mutableListOf<RemoteChange>()
        val receipts = mutableMapOf<String, PushAck>()
        val tombstones = mutableMapOf<String, Long>()
        val pushOrder = mutableListOf<String>()
        var throwAfterApplyOnce = false
        var failPullCallOnce: Int? = null
        var pullCalls = 0
        var beforeReturn: (suspend (SyncOutboxEntity) -> Unit)? = null

        override fun configured() = true

        override suspend fun push(token: String, row: SyncOutboxEntity): PushAck {
            receipts[row.id]?.let { return it }
            pushOrder += "${row.entityType}:${row.entityId}"
            val (id, id2) = split(row)
            val key = key(row.entityType, id, id2)
            val tombstone = tombstones[key]
            val ack =
                if (tombstone != null) {
                    PushAck(row.id, row.entityType, id, id2, "TOMBSTONED", tombstone, true)
                } else if (row.operation == OutboxRecorder.DELETE) {
                    val next = ++version
                    tombstones[key] = next
                    changes += RemoteChange(next, row.entityType, id, id2, OutboxRecorder.DELETE, null)
                    PushAck(row.id, row.entityType, id, id2, "DELETED", next, true)
                } else {
                    val next = ++version
                    val payload = JSONObject(row.payload)
                    changes += RemoteChange(next, row.entityType, id, id2, OutboxRecorder.UPSERT, payload)
                    PushAck(row.id, row.entityType, id, id2, "APPLIED", next, false)
                }
            receipts[row.id] = ack
            beforeReturn?.invoke(row)
            if (throwAfterApplyOnce) {
                throwAfterApplyOnce = false
                throw IOException("response lost after commit")
            }
            return ack
        }

        override suspend fun snapshot(token: String) = version

        override suspend fun pull(token: String, after: Long, through: Long, limit: Int): PullPage {
            pullCalls++
            if (failPullCallOnce == pullCalls) {
                failPullCallOnce = null
                throw IOException("interrupted pull")
            }
            val rows =
                changes
                    .asSequence()
                    .filter { it.serverVersion > after && it.serverVersion <= through }
                    .sortedBy { it.serverVersion }
                    .take(minOf(limit, pageSize))
                    .toList()
            val next = rows.lastOrNull()?.serverVersion ?: after
            val more = changes.any { it.serverVersion > next && it.serverVersion <= through }
            return PullPage(rows, next, more)
        }

        fun seedUpsert(type: String, id: String, payload: JSONObject, id2: String? = null) {
            val next = ++version
            changes += RemoteChange(next, type, id, id2, OutboxRecorder.UPSERT, payload)
        }

        fun seedDelete(type: String, id: String, id2: String? = null) {
            val next = ++version
            tombstones[key(type, id, id2)] = next
            changes += RemoteChange(next, type, id, id2, OutboxRecorder.DELETE, null)
        }

        private fun split(row: SyncOutboxEntity): Pair<String, String?> {
            if (row.entityType != "task_tags") return row.entityId to null
            val parts = row.entityId.split('|', limit = 2)
            return parts[0] to parts[1]
        }

        private fun key(type: String, id: String, id2: String?) = "$type|$id|${id2.orEmpty()}"
    }
}
