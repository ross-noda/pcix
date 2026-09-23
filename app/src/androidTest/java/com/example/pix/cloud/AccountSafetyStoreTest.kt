package com.example.pix.cloud

import android.content.Context
import android.graphics.Bitmap
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AccountSafetyStoreTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: PixDatabase
    private lateinit var repo: TaskRepository
    private lateinit var accounts: AccountStore
    private val files = mutableListOf<File>()

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
        repo = TaskRepository(db)
        repo.initialize()
        accounts = AccountStore(context, db)
    }

    @After
    fun tearDown() {
        db.close()
        files.forEach { it.delete() }
    }

    @Test
    fun accountSnapshotRestoresDataImagesOutboxAndOwnerIsolationPayload() = runBlocking {
        val list = ListEntity(name = "A list", color = 2)
        repo.saveList(list)
        val tagId = repo.saveTag("A tag", 3)
        val task = TaskEntity(title = "Offline A", listId = list.id, durationMinutes = 60)
        repo.create(task, setOf(tagId))
        repo.saveSubtask(SubtaskEntity(taskId = task.id, title = "child"))
        val image = ImageStore(context).file(newId() + ".image")
        files += image
        image.outputStream().use {
            Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
                .compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        db.tracked { db.dao().insertImage(TaskImage(taskId = task.id, fileName = image.name)) }
        db.syncDao().saveState(SyncStateEntity("A", "checkpoint-a", 123L))
        val pendingBefore = db.syncDao().pendingCount()
        assertTrue(pendingBefore > 0)

        accounts.protect("A")
        accounts.wipeUserData()
        assertNull(repo.details(task.id))
        assertEquals(0, db.syncDao().pendingCount())

        assertTrue(accounts.restoreProtected("A"))
        val restored = requireNotNull(repo.details(task.id))
        assertEquals(task.id, restored.task.id)
        assertEquals(list.id, restored.task.listId)
        assertEquals(tagId, restored.tags.single().id)
        assertEquals("child", restored.subtasks.single().title)
        assertEquals(60, restored.task.durationMinutes)
        assertTrue(restored.images.isNotEmpty())
        val restoredImage = ImageStore(context).file(restored.images.single().fileName)
        files += restoredImage
        assertTrue(restoredImage.isFile)
        assertTrue(db.syncDao().pendingCount() >= pendingBefore)
        assertEquals("checkpoint-a", db.syncDao().state("A")?.checkpoint)
    }

    @Test
    fun accountSnapshotsNeverExposeADataOrOutboxToB() = runBlocking {
        val a = TaskEntity(title = "A only")
        repo.create(a)
        accounts.protect("A")

        accounts.wipeUserData()
        val b = TaskEntity(title = "B only")
        repo.create(b)
        accounts.protect("B")

        accounts.wipeUserData()
        assertTrue(accounts.restoreProtected("A"))
        assertNotNull(repo.details(a.id))
        assertNull(repo.details(b.id))
        assertTrue(db.syncDao().pending().none { it.entityId == b.id })

        accounts.wipeUserData()
        assertTrue(accounts.restoreProtected("B"))
        assertNull(repo.details(a.id))
        assertNotNull(repo.details(b.id))
        assertTrue(db.syncDao().pending().none { it.entityId == a.id })
    }

    @Test
    fun legacyCloudOnlySnapshotDoesNotMixWithLaterCloudCache() = runBlocking {
        val legacy = TaskEntity(title = "legacy")
        repo.create(legacy)
        accounts.replaceWithCloud()

        val cloud = TaskEntity(title = "cloud")
        repo.create(cloud)

        assertNull(repo.details(legacy.id))
        assertNotNull(repo.details(cloud.id))
        assertTrue(accounts.protectedExists(AccountSafetyStore.LEGACY_OWNER))
    }

    @Test
    fun legacyCloudOnlyChoiceCreatesRestorableSnapshotBeforeWipe() = runBlocking {
        val task = TaskEntity(title = "Legacy keep me")
        repo.create(task)
        val id = task.id

        accounts.replaceWithCloud()
        assertNull(repo.details(id))
        assertTrue(accounts.protectedExists(AccountSafetyStore.LEGACY_OWNER))

        assertTrue(accounts.restoreLegacy())
        assertEquals("Legacy keep me", repo.details(id)?.task?.title)
    }

    @Test
    fun legacyImportKeepsUuidAndCreatesOutboxWithoutReplacingLocalData() = runBlocking {
        db.syncDao().clear()
        val task = TaskEntity(title = "Legacy import")
        repo.create(task)
        db.syncDao().clear()

        accounts.importLegacy()

        assertEquals(task.id, repo.details(task.id)?.task?.id)
        assertTrue(db.syncDao().pending().any { it.entityType == "tasks" && it.entityId == task.id })
        assertTrue(accounts.protectedExists(AccountSafetyStore.LEGACY_OWNER))
    }
}
