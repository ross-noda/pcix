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
    suspend fun pendingMutationCount(): Int = 0
    suspend fun protect(ownerId: String) = Unit
    suspend fun restoreProtected(ownerId: String): Boolean = false
    suspend fun protectLegacy() = Unit
    fun protectedExists(ownerId: String): Boolean = false
    fun deleteProtected(ownerId: String) = Unit
}

class AccountStore(private val context: Context, private val db: PixDatabase) : AccountDataStore {
    private val safety = AccountSafetyStore(context, db)
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
        protectLegacy()
        OutboxRecorder(db).enqueueAll()
        setLegacyPending(false)
    }

    suspend fun replaceWithCloud() {
        protectLegacy()
        wipeUserData()
        setLegacyPending(false)
    }

    override suspend fun pendingMutationCount(): Int = db.syncDao().pendingCount()

    override suspend fun protect(ownerId: String) = safety.save(ownerId)

    override suspend fun restoreProtected(ownerId: String): Boolean = safety.restore(ownerId)

    override suspend fun protectLegacy() = safety.save(AccountSafetyStore.LEGACY_OWNER)

    suspend fun restoreLegacy(): Boolean = safety.restore(AccountSafetyStore.LEGACY_OWNER)

    override fun protectedExists(ownerId: String): Boolean = safety.exists(ownerId)

    override fun deleteProtected(ownerId: String) = safety.delete(ownerId)

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
            db.syncDao().clearAllVersions()
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
