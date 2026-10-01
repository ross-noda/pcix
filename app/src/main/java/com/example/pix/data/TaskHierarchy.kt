package com.example.pix.data

import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.pix.cloud.SyncCodec
import java.util.UUID
import org.json.JSONObject

/** Single-level relationships. Legacy IDs are deterministic across Room, backups and PostgreSQL. */
object TaskHierarchy {
    fun legacyId(id: String): String = UUID.nameUUIDFromBytes(("pcix-subtask:" + id).toByteArray(Charsets.UTF_8)).toString()

    suspend fun validate(dao: PixDao, task: TaskEntity) {
        val parentId = task.parentTaskId ?: return
        require(parentId != task.id) { "A task cannot parent itself" }
        val parent = requireNotNull(dao.task(parentId)) { "Missing parent" }
        require(parent.parentTaskId == null && !parent.isTemplate && !parent.isSkipped) { "Invalid parent" }
        require(dao.childCount(task.id)==0) { "Only one hierarchy level is allowed" }
    }

    fun validateGraph(db: SupportSQLiteDatabase) {
        db.query("SELECT c.id FROM tasks c LEFT JOIN tasks p ON p.id=c.parentTaskId WHERE c.parentTaskId IS NOT NULL AND (p.id IS NULL OR p.id=c.id OR p.parentTaskId IS NOT NULL OR p.isTemplate=1 OR p.isSkipped=1) LIMIT 1").use {
            require(!it.moveToFirst()) { "Invalid task hierarchy" }
        }
    }

    fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE tasks ADD COLUMN parentTaskId TEXT REFERENCES tasks(id) ON DELETE SET NULL DEFERRABLE INITIALLY DEFERRED")
        db.execSQL("CREATE INDEX index_tasks_parentTaskId ON tasks(parentTaskId)")
        val converted = mutableListOf<TaskEntity>()
        db.query("SELECT s.*,p.listId AS parentList,p.isTemplate AS parentTemplate,p.isSkipped AS parentSkipped FROM subtasks s JOIN tasks p ON p.id=s.taskId").use { c ->
            fun text(k: String) = c.getString(c.getColumnIndexOrThrow(k))
            fun number(k: String) = c.getLong(c.getColumnIndexOrThrow(k))
            while(c.moveToNext()) converted += TaskEntity(
                id=legacyId(text("id")), title=text("title"), listId=text("parentList"),
                parentTaskId=if(number("parentTemplate")==0L && number("parentSkipped")==0L) text("taskId") else null,
                isCompleted=number("isCompleted")==1L, completedAt=if(number("isCompleted")==1L) number("updatedAt") else null,
                sortOrder=number("sortOrder"), createdAt=number("createdAt"), updatedAt=number("updatedAt"))
        }
        converted.forEach { t ->
            db.execSQL("INSERT INTO tasks(id,title,notes,listId,parentTaskId,priority,isCompleted,completedAt,isTemplate,isSkipped,sortOrder,createdAt,updatedAt) VALUES(?,?,?, ?,?,0,?,?,0,0,?,?,?)",
                arrayOf(t.id,t.title,t.notes,t.listId,t.parentTaskId,if(t.isCompleted)1 else 0,t.completedAt,t.sortOrder,t.createdAt,t.updatedAt))
        }
        upgradeLegacyOutbox(db, converted)
        db.execSQL("DROP TABLE subtasks")
        validateGraph(db)
    }

    /** Used both during schema migration and when restoring a pre-v9 protected account snapshot. */
    fun upgradeLegacyOutbox(db: SupportSQLiteDatabase, converted: List<TaskEntity>) {
        val deletes=mutableListOf<String>()
        db.query("SELECT entityId FROM sync_outbox WHERE entityType='subtasks' AND operation='DELETE'").use { c -> while(c.moveToNext()) deletes += legacyId(c.getString(0)) }
        db.execSQL("DELETE FROM sync_outbox WHERE entityType='subtasks'")
        converted.forEach { t -> queue(db,t.id,"UPSERT",SyncCodec.task(t).toString()) }
        deletes.forEach { queue(db,it,"DELETE","{}") }
        db.execSQL("UPDATE sync_state SET checkpoint=NULL")
        db.execSQL("DELETE FROM sync_entity_versions WHERE entityType='subtasks'")
    }

    private fun queue(db: SupportSQLiteDatabase,id: String,operation: String,payload: String) {
        db.execSQL("DELETE FROM sync_outbox WHERE entityType='tasks' AND entityId=?",arrayOf(id))
        db.execSQL("INSERT INTO sync_outbox(id,entityType,entityId,operation,payload,createdAt,attemptCount,lastAttemptAt) VALUES(?,'tasks',?,?,?,?,0,NULL)",
            arrayOf(newId(),id,operation,payload,System.currentTimeMillis()))
    }
}
