package com.example.pix.widget

import android.app.Application
import android.content.ComponentName
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.compose
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.example.pix.data.*
import com.example.pix.domain.DescriptionText.revision
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SingleTaskWidgetTest {
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun glanceRendersEmptyCompletedLongAndMixedContentInBothThemes() = runBlocking {
        val parent = TaskEntity(id = "parent", title = "A very long task title ".repeat(15), notes = "A long note ".repeat(1000) + "\n" + (1..50).joinToString("\n") { "- [ ] Item $it" })
        val children = (1..50).map { TaskEntity(id = "child$it", title = "Child $it", parentTaskId = parent.id, isCompleted = it % 2 == 0) }
        val detail = TaskWithDetails(parent, null, ListEntity(id = INBOX_ID, name = "Inbox", color = 0, sortOrder = 0), emptyList(), children)
        for (theme in listOf(1, 2)) {
            context.getSharedPreferences("appearance", 0).edit().putInt("theme", theme).commit()
            for (fixture in listOf(null, detail.copy(task = parent.copy(notes = ""), children = emptyList()), detail, detail.copy(task = parent.copy(isCompleted = true)))) {
                val widget = object : GlanceAppWidget() {
                    override suspend fun provideGlance(context: android.content.Context, id: GlanceId) {
                        provideContent { SingleTaskContent(context, 31, SingleTaskBinding(parent.id, ""), fixture, false) }
                    }
                }
                val views = widget.compose(context, size = DpSize(150.dp, 240.dp))
                assertNotNull(views.apply(context, android.widget.FrameLayout(context)))
            }
        }
    }

    @Test fun bindingsPersistIndependentlyAndDeletionDoesNotAffectDuplicateTask() {
        val store = SingleTaskWidgetStore(context)
        store.write(31, SingleTaskBinding("a", "owner"))
        store.write(42, SingleTaskBinding("b", "owner"))
        store.write(57, SingleTaskBinding("a", "owner"))
        assertEquals(store.read(31), SingleTaskWidgetStore(context).read(57))
        store.delete(31)
        assertNull(store.read(31))
        assertEquals("a", store.read(57)?.taskId)
        assertEquals("b", store.read(42)?.taskId)
        store.write(42, SingleTaskBinding("c", "other"))
        assertEquals("owner", store.read(57)?.owner)
    }

    @Test fun providerHasTwoByThreeSizeAndBothPreviewsAndConfiguration() {
        val info = context.packageManager.getReceiverInfo(ComponentName(context, SingleTaskWidgetReceiver::class.java), PackageManager.GET_META_DATA)
        info.loadXmlMetaData(context.packageManager, "android.appwidget.provider")!!.use { xml ->
            while (xml.next() != org.xmlpull.v1.XmlPullParser.START_TAG) { }
            val ns = "http://schemas.android.com/apk/res/android"
            assertEquals(2, xml.getAttributeIntValue(ns, "targetCellWidth", 0))
            assertEquals(3, xml.getAttributeIntValue(ns, "targetCellHeight", 0))
            assertTrue(xml.getAttributeResourceValue(ns, "previewLayout", 0) != 0)
            assertTrue(xml.getAttributeResourceValue(ns, "previewImage", 0) != 0)
            assertEquals(SingleTaskWidgetConfigureActivity::class.java.name, xml.getAttributeValue(ns, "configure"))
        }
    }

    @Test fun checklistPersistsWithoutChangingOtherFieldsAndRejectsStaleOrWrongAccountTaps() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TaskRepository(db)
            val notes = "A note\n- [ ] First\n- [x] Second"
            val task = TaskEntity(title = "Parent", notes = notes)
            repository.create(task)
            db.syncDao().clear()
            repository.setChecklistItem(task.id, revision(notes), 1, true)
            assertEquals(1, db.syncDao().pendingFor("tasks", task.id).size)
            val changed = repository.details(task.id)!!.task
            assertEquals("A note\n- [x] First\n- [x] Second", changed.notes)
            assertEquals(task.title, changed.title)
            assertFalse(changed.isCompleted)
            repository.setChecklistItem(task.id, revision(notes), 2, false) // Old rendering: ignored.
            assertEquals(changed.notes, repository.details(task.id)!!.task.notes)
            assertTrue(runCatching { repository.setChecklistItem(task.id, revision(changed.notes), 1, false) { false } }.isFailure)
            repository.setChecklistItem(task.id, revision(changed.notes), 1, false)
            assertEquals(notes, repository.details(task.id)!!.task.notes)
            assertNull(repository.widgetDetails(task.id) { false })
            repository.delete(task.id)
            repository.setChecklistItem(task.id, revision(notes), 1, true)
            assertNull(repository.widgetDetails(task.id) { true })
        } finally { db.close() }
    }

    @Test fun childCompletionAndReopenUseExistingRulesAndRejectWrongParent() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).allowMainThreadQueries().build()
        try {
            val repository = TaskRepository(db)
            val parent = TaskEntity(title = "Parent")
            val other = TaskEntity(title = "Other")
            repository.create(parent); repository.create(other)
            val child = repository.createChild(parent.id, "Child")
            repository.completeWidgetChild(other.id, child.id, true) { true }
            assertFalse(repository.details(child.id)!!.task.isCompleted)
            repository.completeWidgetChild(parent.id, child.id, true) { true }
            assertTrue(repository.details(child.id)!!.task.isCompleted)
            assertFalse(repository.details(parent.id)!!.task.isCompleted)
            repository.completeWidgetChild(parent.id, child.id, false) { true }
            assertFalse(repository.details(child.id)!!.task.isCompleted)
            assertTrue(runCatching { repository.completeWidgetChild(parent.id, child.id, true) { false } }.isFailure)
            repository.complete(parent.id, true)
            assertNotNull(repository.widgetDetails(parent.id) { true })
        } finally { db.close() }
    }
}
