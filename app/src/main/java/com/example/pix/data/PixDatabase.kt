package com.example.pix.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PixDao {
    @Query(
        "SELECT * FROM tasks WHERE isTemplate=0 AND isSkipped=0 AND isCompleted=0 AND dueDay IS :day ORDER BY sortOrder, minuteOfDay IS NULL, minuteOfDay, priority DESC, createdAt, id"
    )
    suspend fun taskDayOrder(day: Long?): List<TaskEntity>

    @Query("UPDATE tasks SET sortOrder=:order, updatedAt=:now WHERE id=:id")
    suspend fun setTaskOrder(id: String, order: Long, now: Long)

    @Query(
        "SELECT * FROM lists WHERE id != '00000000-0000-0000-0000-000000000001' ORDER BY sortOrder, id"
    )
    suspend fun orderedLists(): List<ListEntity>

    @Query("UPDATE lists SET sortOrder=:order, updatedAt=:now WHERE id=:id")
    suspend fun setListOrder(id: String, order: Long, now: Long = System.currentTimeMillis())

    @Query("UPDATE tasks SET listId=:listId, updatedAt=:now WHERE id=:id")
    suspend fun moveTask(id: String, listId: String, now: Long)

    @Query(
        "UPDATE tasks SET dueDay=:day, minuteOfDay=:minute, durationMinutes=:duration, updatedAt=:now WHERE id=:id"
    )
    suspend fun rescheduleTask(id: String, day: Long, minute: Int?, duration: Int?, now: Long)

    @Query("UPDATE subtasks SET sortOrder=:order, updatedAt=:now WHERE id=:id")
    suspend fun setSubtaskOrder(id: String, order: Long, now: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertImage(image: TaskImage)

    @Query("SELECT * FROM task_images WHERE taskId=:id ORDER BY createdAt")
    suspend fun images(id: String): List<TaskImage>

    @Query("DELETE FROM task_images WHERE id=:id") suspend fun deleteImage(id: String)

    @Query("SELECT DISTINCT fileName FROM task_images") suspend fun referencedImages(): List<String>

    @Query("UPDATE lists SET icon=:icon, updatedAt=:now WHERE id=:id")
    suspend fun setListIcon(id: String, icon: String, now: Long = System.currentTimeMillis())

    @Insert suspend fun insertSeries(series: RecurringSeriesEntity)

    @Query("SELECT COUNT(*) FROM tasks WHERE seriesId=:id")
    suspend fun seriesOccurrenceCount(id: String): Int

    @Update suspend fun updateSeries(series: RecurringSeriesEntity)

    @Query("SELECT * FROM recurring_series WHERE id=:id")
    suspend fun series(id: String): RecurringSeriesEntity?

    @Query("UPDATE tasks SET seriesId=:series, originalDay=:day, updatedAt=:now WHERE id=:id")
    suspend fun attachSeries(
        id: String,
        series: String?,
        day: Long?,
        now: Long = System.currentTimeMillis(),
    )

    @Query("SELECT * FROM tasks WHERE seriesId=:series AND originalDay=:day LIMIT 1")
    suspend fun occurrence(series: String, day: Long): TaskEntity?

    @Query("UPDATE tasks SET isSkipped=1, updatedAt=:now WHERE id=:id")
    suspend fun skip(id: String, now: Long = System.currentTimeMillis())

    @Query(
        "DELETE FROM tasks WHERE seriesId=:series AND originalDay>=:day AND id!=:exceptId AND isCompleted=0"
    )
    suspend fun deleteFuture(series: String, day: Long, exceptId: String)

    @Query("DELETE FROM subtasks WHERE taskId=:id") suspend fun clearSubtasks(id: String)

    @Query(
        "SELECT * FROM tasks WHERE isTemplate=0 AND isSkipped=0 AND isCompleted = 0 AND dueDay IS NOT NULL AND minuteOfDay IS NOT NULL"
    )
    suspend fun timedTasks(): List<TaskEntity>

    @Query("SELECT * FROM reminder_receipts WHERE taskId = :id")
    suspend fun receipt(id: String): ReminderReceipt?

    @Upsert suspend fun saveReceipt(receipt: ReminderReceipt)

    @Query(
        "UPDATE tasks SET dueDay=:day, minuteOfDay=:minute, updatedAt=:now WHERE id=:id AND isCompleted=0"
    )
    suspend fun snooze(id: String, day: Long, minute: Int, now: Long)

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun details(id: String): TaskWithDetails?

    @Transaction
    @Query(
        "SELECT * FROM tasks WHERE isTemplate=0 AND isSkipped=0 AND dueDay <= :day AND (dueDay + (COALESCE(minuteOfDay,0) + MAX(COALESCE(durationMinutes,1)-1,0)) / 1440) >= :day ORDER BY isCompleted, minuteOfDay IS NULL, minuteOfDay, priority DESC, createdAt"
    )
    fun observeDay(day: Long): Flow<List<TaskWithDetails>>

    @Query(
        "WITH RECURSIVE days(day) AS (SELECT :start UNION ALL SELECT day+1 FROM days WHERE day+1 < :end) SELECT days.day AS dueDay, lists.color AS color, COUNT(*) AS count FROM days JOIN tasks ON tasks.dueDay <= days.day AND (tasks.dueDay + (COALESCE(tasks.minuteOfDay,0) + MAX(COALESCE(tasks.durationMinutes,1)-1,0))/1440) >= days.day JOIN lists ON lists.id=tasks.listId WHERE tasks.isTemplate=0 AND tasks.isSkipped=0 AND tasks.isCompleted=0 GROUP BY days.day, lists.id ORDER BY days.day, lists.sortOrder"
    )
    fun observeCalendar(start: Long, end: Long): Flow<List<CalendarMark>>

    @Transaction
    @Query(
        """
        SELECT * FROM tasks WHERE isTemplate=0 AND isSkipped=0 AND
        (:listId IS NULL OR listId = :listId) AND
        (:tagId IS NULL OR id IN (SELECT taskId FROM task_tags WHERE tagId = :tagId)) AND
        (:showCompleted OR isCompleted = 0) AND
        (:mode = 'ALL' OR (:mode = 'TODAY' AND dueDay <= :today AND (dueDay + (COALESCE(minuteOfDay,0) + MAX(COALESCE(durationMinutes,1)-1,0)) / 1440) >= :today) OR
        (:mode = 'TOMORROW' AND dueDay <= :today + 1 AND (dueDay + (COALESCE(minuteOfDay,0) + MAX(COALESCE(durationMinutes,1)-1,0)) / 1440) >= :today + 1) OR
        (:mode = 'WEEK' AND (dueDay + (COALESCE(minuteOfDay,0) + MAX(COALESCE(durationMinutes,1)-1,0)) / 1440) >= :today AND dueDay < :weekEnd) OR
        (:mode = 'OVERDUE' AND ((minuteOfDay IS NULL AND dueDay + COALESCE(durationMinutes,1440)/1440 - 1 < :today) OR (minuteOfDay IS NOT NULL AND dueDay*1440 + minuteOfDay + COALESCE(durationMinutes,0) < :today*1440 + :minute)))) AND
        (:search = '' OR title LIKE :search ESCAPE '\' OR notes LIKE :search ESCAPE '\' OR
        listId IN (SELECT id FROM lists WHERE name LIKE :search ESCAPE '\') OR
        id IN (SELECT taskId FROM task_tags JOIN tags ON tags.id = task_tags.tagId WHERE tags.name LIKE :search ESCAPE '\'))
        ORDER BY isCompleted, dueDay IS NULL, dueDay,
        CASE WHEN :manual THEN sortOrder ELSE 0 END,
        minuteOfDay IS NULL, minuteOfDay, priority DESC, createdAt, id
    """
    )
    fun observeTasks(
        mode: String,
        today: Long,
        weekEnd: Long,
        minute: Int,
        listId: String?,
        tagId: String?,
        search: String,
        showCompleted: Boolean,
        manual: Boolean,
    ): Flow<List<TaskWithDetails>>

    @Transaction
    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeTask(id: String): Flow<TaskWithDetails?>

    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun task(id: String): TaskEntity?

    @Query(
        "SELECT lists.*, (SELECT COUNT(*) FROM tasks WHERE listId = lists.id AND isTemplate=0 AND isSkipped=0 AND isCompleted = 0) AS activeCount FROM lists ORDER BY id = '00000000-0000-0000-0000-000000000001' DESC, sortOrder, id"
    )
    fun observeLists(): Flow<List<ListWithCount>>

    @Query(
        "SELECT tags.*, (SELECT COUNT(*) FROM task_tags JOIN tasks ON tasks.id = task_tags.taskId WHERE tagId = tags.id AND isTemplate=0 AND isSkipped=0 AND isCompleted = 0) AS activeCount FROM tags ORDER BY name COLLATE NOCASE"
    )
    fun observeTags(): Flow<List<TagWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertList(list: ListEntity)

    @Update suspend fun updateList(list: ListEntity)

    @Insert suspend fun insertTask(task: TaskEntity)

    @Query(
        "UPDATE tasks SET title=:title, notes=:notes, listId=:listId, dueDay=:day, minuteOfDay=:minute, durationMinutes=:duration, priority=:priority, matrixUrgent=:urgent, matrixImportant=:important, updatedAt=:now WHERE id=:id"
    )
    suspend fun editTask(
        id: String,
        title: String,
        notes: String,
        listId: String,
        day: Long?,
        minute: Int?,
        priority: Int,
        duration: Int?,
        urgent: Boolean?,
        important: Boolean?,
        now: Long,
    )

    @Query(
        "UPDATE tasks SET isCompleted=:completed, completedAt=:completedAt, updatedAt=:now WHERE id=:id"
    )
    suspend fun complete(id: String, completed: Boolean, completedAt: Long?, now: Long)

    @Query("DELETE FROM tasks WHERE id=:id") suspend fun deleteTask(id: String)

    @Query("UPDATE tasks SET listId=:inbox, updatedAt=:now WHERE listId=:id")
    suspend fun moveToInbox(
        id: String,
        inbox: String = INBOX_ID,
        now: Long = System.currentTimeMillis(),
    )

    @Query("DELETE FROM lists WHERE id=:id") suspend fun deleteList(id: String)

    @Query("SELECT * FROM tags WHERE normalizedName=:name")
    suspend fun tagByName(name: String): TagEntity?

    @Insert suspend fun insertTag(tag: TagEntity)

    @Update suspend fun updateTag(tag: TagEntity)

    @Query("DELETE FROM tags WHERE id=:id") suspend fun deleteTag(id: String)

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun attachTag(ref: TaskTagCrossRef)

    @Query("DELETE FROM task_tags WHERE taskId=:taskId AND tagId=:tagId")
    suspend fun detachTag(taskId: String, tagId: String)

    @Query("DELETE FROM task_tags WHERE taskId=:id") suspend fun clearTags(id: String)

    @Upsert suspend fun saveSubtask(subtask: SubtaskEntity)

    @Query("DELETE FROM subtasks WHERE id=:id") suspend fun deleteSubtask(id: String)

    @Query("SELECT * FROM subtasks WHERE taskId=:id ORDER BY isCompleted, sortOrder, id")
    suspend fun subtasks(id: String): List<SubtaskEntity>

    @Query("SELECT tagId FROM task_tags WHERE taskId=:id")
    suspend fun tagIds(id: String): List<String>

    @Query("SELECT id FROM lists") suspend fun listIds(): List<String>

    @Query("SELECT * FROM lists WHERE id=:id") suspend fun listById(id: String): ListEntity?

    @Query("SELECT id, updatedAt FROM lists") suspend fun listStamps(): List<IdStamp>

    @Query("SELECT id, updatedAt FROM tags") suspend fun tagStamps(): List<IdStamp>

    @Query("SELECT id, updatedAt FROM tasks") suspend fun taskStamps(): List<IdStamp>

    @Query("SELECT id, updatedAt FROM subtasks") suspend fun subtaskStamps(): List<IdStamp>

    @Query("SELECT id, updatedAt FROM recurring_series") suspend fun seriesStamps(): List<IdStamp>

    @Query("SELECT * FROM tags WHERE id=:id") suspend fun tagById(id: String): TagEntity?

    @Query("SELECT * FROM subtasks WHERE id=:id") suspend fun subtaskById(id: String): SubtaskEntity?

    @Query("SELECT * FROM task_images WHERE id=:id") suspend fun imageById(id: String): TaskImage?

    @Query("SELECT taskId AS taskId, tagId AS tagId FROM task_tags")
    suspend fun tagLinks(): List<TagLink>

    @Query("SELECT id FROM task_images") suspend fun imageIds(): List<String>

    @Query("SELECT COUNT(*) FROM tasks WHERE isTemplate=0 AND isSkipped=0")
    suspend fun visibleTaskCount(): Int

    @Query("SELECT COUNT(*) FROM lists") suspend fun listCount(): Int

    @Query("SELECT COUNT(*) FROM tags") suspend fun tagCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceList(list: ListEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTask(task: TaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun replaceTag(tag: TagEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSubtask(subtask: SubtaskEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun replaceSeries(series: RecurringSeriesEntity)

    @Query("DELETE FROM reminder_receipts") suspend fun clearReceipts()

    @Query("DELETE FROM task_images") suspend fun clearImages()

    @Query("DELETE FROM task_tags") suspend fun clearTaskTags()

    @Query("DELETE FROM subtasks") suspend fun clearAllSubtasks()

    @Query("DELETE FROM recurring_series") suspend fun clearSeries()

    @Query("DELETE FROM tasks") suspend fun clearTasks()

    @Query("DELETE FROM tags") suspend fun clearTags()

    @Query("DELETE FROM lists") suspend fun clearLists()
}

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_outbox ORDER BY createdAt, id")
    suspend fun pending(): List<SyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sync_outbox")
    suspend fun pendingCount(): Int

    @Query("SELECT * FROM sync_outbox WHERE entityType=:type AND entityId=:id")
    suspend fun pendingFor(type: String, id: String): List<SyncOutboxEntity>

    @Insert suspend fun insert(row: SyncOutboxEntity)

    @Query("DELETE FROM sync_outbox WHERE id=:id") suspend fun remove(id: String)

    @Query("DELETE FROM sync_outbox WHERE entityType=:type AND entityId=:id AND operation=:op")
    suspend fun removeMatching(type: String, id: String, op: String)

    @Query("UPDATE sync_outbox SET attemptCount=attemptCount+1, lastAttemptAt=:now WHERE id=:id")
    suspend fun attempted(id: String, now: Long)

    @Query("DELETE FROM sync_outbox") suspend fun clear()

    @Query("SELECT * FROM sync_state WHERE accountId=:id")
    suspend fun state(id: String): SyncStateEntity?

    @Query("SELECT * FROM sync_state")
    suspend fun states(): List<SyncStateEntity>

    @Upsert suspend fun saveState(state: SyncStateEntity)

    @Query("DELETE FROM sync_state") suspend fun clearState()
}

@Dao
interface GoogleDao {
    @Query("SELECT * FROM google_calendars ORDER BY summary")
    fun observeCalendars(): Flow<List<GoogleCalendarEntity>>

    @Query("SELECT * FROM google_calendars ORDER BY summary")
    suspend fun calendars(): List<GoogleCalendarEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveCalendar(calendar: GoogleCalendarEntity)

    @Query("UPDATE google_calendars SET enabled=:enabled WHERE id=:id")
    suspend fun setEnabled(id: String, enabled: Boolean)

    @Query("DELETE FROM google_calendars") suspend fun clearCalendars()

    @Query(
        "SELECT * FROM google_events WHERE cancelled=0 AND calendarId IN (SELECT id FROM google_calendars WHERE enabled=1) AND startDay < :end AND endDay > :start"
    )
    fun observeEvents(start: Long, end: Long): Flow<List<GoogleEventEntity>>

    @Query("SELECT * FROM google_events WHERE calendarId=:calendarId")
    suspend fun events(calendarId: String): List<GoogleEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveEvent(event: GoogleEventEntity)

    @Query("DELETE FROM google_events WHERE id=:id") suspend fun deleteEvent(id: String)

    @Query("DELETE FROM google_events WHERE calendarId=:calendarId")
    suspend fun clearEvents(calendarId: String)

    @Query("DELETE FROM google_events") suspend fun clearAllEvents()

    @Query("SELECT * FROM google_sync_state WHERE calendarId=:id")
    suspend fun syncState(id: String): GoogleSyncStateEntity?

    @Upsert suspend fun saveSyncState(state: GoogleSyncStateEntity)

    @Query("DELETE FROM google_sync_state") suspend fun clearSyncState()
}

@Database(
    entities =
        [
            TaskEntity::class,
            TaskImage::class,
            ListEntity::class,
            TagEntity::class,
            TaskTagCrossRef::class,
            SubtaskEntity::class,
            ReminderReceipt::class,
            RecurringSeriesEntity::class,
            SyncOutboxEntity::class,
            SyncStateEntity::class,
            GoogleCalendarEntity::class,
            GoogleEventEntity::class,
            GoogleSyncStateEntity::class,
        ],
    version = 6,
    exportSchema = true,
)
abstract class PixDatabase : RoomDatabase() {
    abstract fun dao(): PixDao

    abstract fun syncDao(): SyncDao

    abstract fun googleDao(): GoogleDao

    companion object {
        val MIGRATION_5_6 =
            object : androidx.room.migration.Migration(5, 6) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE lists ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE lists ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE tags ADD COLUMN createdAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE tags ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "ALTER TABLE recurring_series ADD COLUMN updatedAt INTEGER NOT NULL DEFAULT 0"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_outbox (id TEXT NOT NULL, entityType TEXT NOT NULL, entityId TEXT NOT NULL, operation TEXT NOT NULL, payload TEXT NOT NULL, createdAt INTEGER NOT NULL, attemptCount INTEGER NOT NULL, lastAttemptAt INTEGER, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_sync_outbox_entityType_entityId ON sync_outbox(entityType, entityId)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS sync_state (accountId TEXT NOT NULL, checkpoint TEXT, lastSuccessAt INTEGER NOT NULL, PRIMARY KEY(accountId))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS google_calendars (id TEXT NOT NULL, summary TEXT NOT NULL, colorArgb INTEGER NOT NULL, timeZone TEXT, enabled INTEGER NOT NULL, accessRole TEXT, PRIMARY KEY(id))"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS google_events (id TEXT NOT NULL, calendarId TEXT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, location TEXT NOT NULL, startDay INTEGER NOT NULL, endDay INTEGER NOT NULL, startMinute INTEGER, endMinute INTEGER, allDay INTEGER NOT NULL, status TEXT NOT NULL, cancelled INTEGER NOT NULL, updatedAt INTEGER NOT NULL, recurringEventId TEXT, colorArgb INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(calendarId) REFERENCES google_calendars(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_google_events_calendarId ON google_events(calendarId)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_google_events_startDay ON google_events(startDay)"
                    )
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS index_google_events_endDay ON google_events(endDay)"
                    )
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS google_sync_state (calendarId TEXT NOT NULL, syncToken TEXT, lastSyncAt INTEGER NOT NULL, PRIMARY KEY(calendarId))"
                    )
                }
            }

        val MIGRATION_4_5 =
            object : androidx.room.migration.Migration(4, 5) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN durationMinutes INTEGER")
                }
            }
        val MIGRATION_3_4 =
            object : androidx.room.migration.Migration(3, 4) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN matrixUrgent INTEGER")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN matrixImportant INTEGER")
                    db.execSQL("ALTER TABLE lists ADD COLUMN icon TEXT NOT NULL DEFAULT '📋'")
                    db.execSQL(
                        "CREATE TABLE task_images (id TEXT NOT NULL, taskId TEXT NOT NULL, fileName TEXT NOT NULL, createdAt INTEGER NOT NULL, PRIMARY KEY(id), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL("CREATE INDEX index_task_images_taskId ON task_images(taskId)")
                }
            }

        val MIGRATION_2_3 =
            object : androidx.room.migration.Migration(2, 3) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL("ALTER TABLE tasks ADD COLUMN seriesId TEXT")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN originalDay INTEGER")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN isTemplate INTEGER NOT NULL DEFAULT 0")
                    db.execSQL("ALTER TABLE tasks ADD COLUMN isSkipped INTEGER NOT NULL DEFAULT 0")
                    db.execSQL(
                        "CREATE UNIQUE INDEX index_tasks_seriesId_originalDay ON tasks(seriesId, originalDay)"
                    )
                    db.execSQL(
                        "CREATE TABLE recurring_series (id TEXT NOT NULL, rule TEXT NOT NULL, anchorDay INTEGER NOT NULL, templateTaskId TEXT NOT NULL, endBefore INTEGER, PRIMARY KEY(id), FOREIGN KEY(templateTaskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                    db.execSQL(
                        "CREATE INDEX index_recurring_series_templateTaskId ON recurring_series(templateTaskId)"
                    )
                }
            }

        val MIGRATION_1_2 =
            object : androidx.room.migration.Migration(1, 2) {
                override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE IF NOT EXISTS reminder_receipts (taskId TEXT NOT NULL, triggerAt INTEGER NOT NULL, PRIMARY KEY(taskId), FOREIGN KEY(taskId) REFERENCES tasks(id) ON UPDATE NO ACTION ON DELETE CASCADE)"
                    )
                }
            }

        fun create(context: Context): PixDatabase =
            Room.databaseBuilder(context, PixDatabase::class.java, "pix.db")
                .addMigrations(
                    MIGRATION_1_2,
                    MIGRATION_2_3,
                    MIGRATION_3_4,
                    MIGRATION_4_5,
                    MIGRATION_5_6,
                )
                .build()
    }
}
