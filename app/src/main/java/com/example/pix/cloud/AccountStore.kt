package com.example.pix.cloud

import android.content.Context
import androidx.room.withTransaction
import com.example.pix.data.*
import com.example.pix.reminders.ReminderWork

interface AccountDataStore {
    fun owner(): String?
    fun setOwner(id: String?)
    suspend fun hasLegacyData(): Boolean
    suspend fun wipeUserData()
    suspend fun clearGoogle()
}

class AccountStore(private val context: Context, private val db: PixDatabase) : AccountDataStore {
    private val prefs = context.getSharedPreferences("pcix.account", Context.MODE_PRIVATE)

    override fun owner(): String? = prefs.getString("owner", null)

    override fun setOwner(id: String?) {
        prefs.edit().putString("owner", id).apply()
    }

    fun legacyDecisionPending(): Boolean = prefs.getBoolean("legacyPending", false)

    fun setLegacyPending(value: Boolean) {
        prefs.edit().putBoolean("legacyPending", value).apply()
    }

    suspend fun counts() =
        LegacyCounts(db.dao().visibleTaskCount(), db.dao().listCount(), db.dao().tagCount())

    override suspend fun hasLegacyData(): Boolean {
        val counts = counts()
        return counts.tasks > 0 || counts.lists > 1 || counts.tags > 0
    }

    suspend fun importLegacy() {
        OutboxRecorder(db).enqueueAll()
        setLegacyPending(false)
    }

    suspend fun replaceWithCloud() {
        wipeUserData()
        setLegacyPending(false)
    }

    override suspend fun wipeUserData() {
        db.withTransaction {
            db.dao().clearReceipts()
            db.dao().clearImages()
            db.dao().clearTaskTags()
            db.dao().clearAllSubtasks()
            db.dao().clearSeries()
            db.dao().clearTasks()
            db.dao().clearTags()
            db.dao().clearLists()
            db.syncDao().clear()
            db.syncDao().clearState()
            db.dao().insertList(ListEntity(id = INBOX_ID, name = "Inbox", color = 8, sortOrder = 0))
        }
        ReminderWork.reconcile(context)
    }

    override suspend fun clearGoogle() {
        db.googleDao().clearAllEvents()
        db.googleDao().clearCalendars()
        db.googleDao().clearSyncState()
    }
}

data class LegacyCounts(val tasks: Int, val lists: Int, val tags: Int)
