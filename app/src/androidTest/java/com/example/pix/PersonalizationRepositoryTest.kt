package com.example.pix

import android.graphics.Bitmap
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class PersonalizationRepositoryTest {
    private lateinit var db: PixDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setup() = runBlocking {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        repo = TaskRepository(db)
        repo.initialize()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun listIconCanChangeIncludingInboxWithoutRenamingIt() = runBlocking {
        repo.setListIcon(INBOX_ID, "🏠")
        assertEquals("🏠", repo.lists.first().single().list.icon)
        assertEquals("Inbox", repo.lists.first().single().list.name)
    }

    @Test
    fun importedImageIsOwnedAndDuplicateRetainsReference() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = java.io.File(context.cacheDir, "test-image.png")
        val bitmap = Bitmap.createBitmap(48, 32, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.MAGENTA)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val store = ImageStore(context)
        val file = store.import(Uri.fromFile(source))
        source.delete()
        assertTrue(store.file(file).exists())
        val task = TaskEntity(title = "Image test")
        repo.create(task)
        val attachment = TaskImage(taskId = task.id, fileName = file)
        repo.addImage(attachment)
        repo.duplicate(task.id)
        val copy =
            repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single {
                it.task.id != task.id
            }
        assertEquals(file, copy.images.single().fileName)
        repo.removeImage(attachment)
        assertTrue(repo.imageReferenced(file))
        repo.delete(copy.task.id)
        assertFalse(repo.imageReferenced(file))
        store.file(file).delete()
        Unit
    }

    @Test
    fun imagesRespectRecurringOccurrenceAndFutureScopes() = runBlocking {
        val task = TaskEntity(title = "Recurring image", dueDay = LocalDate.now().toEpochDay())
        repo.create(task)
        val rule = RecurrenceRule(Frequency.DAILY).encode()
        repo.editRecurring(task, emptySet(), rule, RecurrenceScope.THIS_AND_FUTURE)
        repo.addImage(
            TaskImage(taskId = task.id, fileName = "only.image"),
            RecurrenceScope.ONLY_THIS,
        )
        repo.complete(task.id, true)
        val next = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single()
        assertTrue(next.images.isEmpty())
        repo.addImage(
            TaskImage(taskId = next.task.id, fileName = "future.image"),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.complete(next.task.id, true)
        assertEquals(
            "future.image",
            repo
                .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                .first()
                .single()
                .images
                .single()
                .fileName,
        )
    }
}
