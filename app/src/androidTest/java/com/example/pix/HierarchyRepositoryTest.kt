package com.example.pix

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.Markdown
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import java.io.ByteArrayOutputStream
import java.time.ZonedDateTime
import java.util.zip.*
import org.json.JSONObject
import org.json.JSONArray

class HierarchyRepositoryTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db:PixDatabase
    private lateinit var repo:TaskRepository
    @Before fun setup()=runBlocking {db=Room.inMemoryDatabaseBuilder(context,PixDatabase::class.java).build();repo=TaskRepository(db);repo.initialize()}
    @After fun close(){db.close()}
    private suspend fun root(name:String)=TaskEntity(title=name).also {repo.create(it)}
    private suspend fun rejected(block:suspend ()->Unit) {try{block();fail("invalid hierarchy accepted")}catch(_:IllegalArgumentException){}}

    @Test fun linkChangeDetachAndInvalidRelationships()=runBlocking {
        val a=root("A");val b=root("B");val c=root("C")
        repo.linkParent(b.id,a.id);assertEquals(a.id,repo.details(b.id)!!.task.parentTaskId)
        rejected{repo.linkParent(a.id,a.id)}
        rejected{repo.linkParent(c.id,b.id)}
        rejected{repo.linkParent(a.id,c.id)}
        rejected{repo.linkParent(a.id,b.id)}
        rejected{repo.linkParent(c.id,"missing")}
        repo.linkParent(b.id,c.id);assertEquals(c.id,repo.details(b.id)!!.parent!!.id)
        repo.linkParent(b.id,null);assertNull(repo.details(b.id)!!.task.parentTaskId)
    }
    @Test fun completionIndependentAndDeletePreservesChildren()=runBlocking {
        val a=root("A");val b=repo.createChild(a.id,"B")
        repo.complete(a.id,true);assertFalse(repo.details(b.id)!!.task.isCompleted)
        repo.complete(a.id,false);repo.complete(b.id,true);assertFalse(repo.details(a.id)!!.task.isCompleted)
        db.syncDao().clear();repo.delete(a.id)
        assertNull(repo.details(a.id));assertNull(repo.details(b.id)!!.task.parentTaskId)
        val pending=db.syncDao().pending();assertTrue(pending.any {it.entityId==b.id && it.operation=="UPSERT" && JSONObject(it.payload).isNull("parent_task_id")})
    }
    @Test fun markdownPersistsSearchesAndDoesNotCreateChildren()=runBlocking {
        val a=root("Markdown")
        val source="## Head\n- [ ] Unique needle\n**bold**"
        repo.edit(a.copy(notes=source),emptySet())
        assertEquals(source,repo.details(a.id)!!.task.notes)
        val toggled=Markdown.toggle(source,source.indexOf("[ ]")+1)
        repo.edit(a.copy(notes=toggled),emptySet());assertEquals(toggled,repo.details(a.id)!!.task.notes)
        assertTrue(repo.details(a.id)!!.children.isEmpty())
        assertEquals(a.id,repo.observe(TaskFilter(mode="ALL",search="Unique needle"),ZonedDateTime.now()).first().single().task.id)
        repo.edit(a.copy(notes=""),emptySet());assertEquals("",repo.details(a.id)!!.task.notes)
    }
    @Test fun childAppearsOnceInHomeSearchCalendarAndReminders()=runBlocking {
        val a=root("A");val b=repo.createChild(a.id,"B");val now=ZonedDateTime.now();val day=now.toLocalDate().toEpochDay()
        repo.edit(b.copy(dueDay=day,minuteOfDay=600,priority=5,notes="**Calendar needle**"),emptySet())
        assertEquals(listOf(b.id),repo.observe(TaskFilter(mode="TODAY"),now).first().map {it.task.id})
        assertEquals(listOf(b.id),repo.day(day).first().map {it.task.id})
        assertEquals(b.id,db.dao().timedTasks().single().id)
        assertEquals(b.id,repo.observe(TaskFilter(mode="ALL",search="needle"),now).first().single().task.id)
    }
    @Test fun recurringChildKeepsParentWithoutCloningTrees()=runBlocking {
        val a=root("A");val b=repo.createChild(a.id,"B").copy(dueDay=23000)
        repo.editRecurring(b,emptySet(),"FREQ=DAILY",RecurrenceScope.THIS_AND_FUTURE)
        repo.complete(b.id,true)
        val next=repo.observe(TaskFilter(mode="ALL"),ZonedDateTime.now()).first().single {it.task.title=="B"}
        assertEquals(a.id,next.task.parentTaskId);assertTrue(next.children.isEmpty())
        repo.delete(a.id)
        repo.complete(next.task.id,true)
        assertTrue(db.dao().syncTasks().all {it.parentTaskId==null})
    }
    @Test fun backupRoundtripPreservesHierarchyMarkdownAndOrder()=runBlocking {
        val a=root("A");val b=repo.createChild(a.id,"B");repo.edit(b.copy(notes="- [x] packed"),emptySet())
        val backup=BackupRepository(context,db);val out=ByteArrayOutputStream();backup.export(out)
        repo.delete(b.id);repo.delete(a.id)
        backup.restore(backup.prepare(out.toByteArray().inputStream()))
        val restored=repo.details(b.id)!!;assertEquals(a.id,restored.task.parentTaskId);assertEquals("- [x] packed",restored.task.notes)
        assertEquals(b.sortOrder,restored.task.sortOrder)
    }
    @Test fun legacyBackupConvertsSubtaskAndPreservesDeterministicId()=runBlocking {
        val a=root("A");val backup=BackupRepository(context,db);val out=ByteArrayOutputStream();backup.export(out)
        val data=ZipInputStream(out.toByteArray().inputStream()).use {it.nextEntry;JSONObject(it.readBytes().toString(Charsets.UTF_8))}
        data.put("version",1).put("schema",6)
        val tables=data.getJSONObject("tables")
        tables.getJSONArray("tasks").getJSONObject(0).remove("parentTaskId")
        tables.put("subtasks",JSONArray().put(JSONObject().put("id","old").put("taskId",a.id).put("title","Old child").put("isCompleted",1).put("sortOrder",42).put("createdAt",1).put("updatedAt",2)))
        val zip=ByteArrayOutputStream();ZipOutputStream(zip).use {it.putNextEntry(ZipEntry("data.json"));it.write(data.toString().toByteArray());it.closeEntry()}
        backup.restore(backup.prepare(zip.toByteArray().inputStream()))
        val child=repo.details(TaskHierarchy.legacyId("old"))!!.task
        assertEquals(a.id,child.parentTaskId);assertTrue(child.isCompleted);assertEquals(42L,child.sortOrder)
    }
}
