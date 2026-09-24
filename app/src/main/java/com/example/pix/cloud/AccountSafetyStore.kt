package com.example.pix.cloud

import android.content.Context
import com.example.pix.data.BackupRepository
import com.example.pix.data.PixDatabase
import com.example.pix.data.SyncOutboxEntity
import com.example.pix.data.SyncStateEntity
import com.example.pix.data.SyncEntityVersionEntity
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Private, per-account recovery snapshots used before any destructive local replacement. */
class AccountSafetyStore(
    private val context: Context,
    private val db: PixDatabase,
) {
    companion object {
        const val LEGACY_OWNER = "__legacy__"
    }

    private val backups = BackupRepository(context, db)
    private val root = File(context.noBackupFilesDir, "account-snapshots")

    suspend fun save(ownerId: String) = withContext(Dispatchers.IO) {
        root.mkdirs()
        val target = dir(ownerId)
        val temp = File(root, target.name + ".tmp-${System.nanoTime()}").apply { mkdirs() }
        try {
            File(temp, "data.zip").outputStream().buffered().use { backups.export(it) }
            val pending = db.syncDao().pending()
            val states = db.syncDao().states()
            val versions = db.syncDao().versions(ownerId)
            val sync = JSONObject()
                .put("owner", ownerId)
                .put("outbox", JSONArray().apply { pending.forEach { put(it.toJson()) } })
                .put("state", JSONArray().apply { states.forEach { put(it.toJson()) } })
                .put("versions", JSONArray().apply { versions.forEach { put(it.toJson()) } })
            File(temp, "sync.json").writeText(sync.toString())
            File(temp, "complete").writeText("1")
            if (target.exists()) target.deleteRecursively()
            check(temp.renameTo(target)) { "Unable to commit account safety snapshot" }
        } catch (error: Throwable) {
            temp.deleteRecursively()
            throw error
        }
    }

    suspend fun restore(ownerId: String): Boolean = withContext(Dispatchers.IO) {
        val source = dir(ownerId)
        val archive = File(source, "data.zip")
        val syncFile = File(source, "sync.json")
        if (!File(source, "complete").isFile || !archive.isFile || !syncFile.isFile) {
            return@withContext false
        }
        val prepared = archive.inputStream().buffered().use { backups.prepare(it) }
        backups.restore(prepared)
        val sync = JSONObject(syncFile.readText())
        require(sync.getString("owner") == ownerId)
        db.syncDao().clear()
        db.syncDao().clearState()
        db.syncDao().clearAllVersions()
        sync.getJSONArray("outbox").objects().forEach { db.syncDao().insert(it.toOutbox()) }
        sync.getJSONArray("state").objects().forEach { db.syncDao().saveState(it.toState()) }
        sync.optJSONArray("versions")?.objects()?.forEach { db.syncDao().saveVersion(it.toVersion()) }
        // Backup restore may rename local image files. Re-enqueue current entities so UPSERT
        // payloads exactly describe the restored Room state; DELETE rows remain preserved.
        OutboxRecorder(db).enqueueAll()
        true
    }

    fun exists(ownerId: String): Boolean =
        File(dir(ownerId), "complete").isFile && File(dir(ownerId), "data.zip").isFile

    fun delete(ownerId: String) {
        dir(ownerId).deleteRecursively()
    }

    private fun dir(ownerId: String): File = File(root, hash(ownerId))

    private fun hash(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun SyncOutboxEntity.toJson() = JSONObject()
        .put("id", id)
        .put("entityType", entityType)
        .put("entityId", entityId)
        .put("operation", operation)
        .put("payload", payload)
        .put("createdAt", createdAt)
        .put("attemptCount", attemptCount)
        .put("lastAttemptAt", lastAttemptAt ?: JSONObject.NULL)

    private fun SyncStateEntity.toJson() = JSONObject()
        .put("accountId", accountId)
        .put("checkpoint", checkpoint ?: JSONObject.NULL)
        .put("lastSuccessAt", lastSuccessAt)
        .put("status", status)

    private fun SyncEntityVersionEntity.toJson() = JSONObject()
        .put("accountId", accountId)
        .put("entityType", entityType)
        .put("entityId", entityId)
        .put("serverVersion", serverVersion)
        .put("deleted", deleted)

    private fun JSONObject.toOutbox() = SyncOutboxEntity(
        id = getString("id"),
        entityType = getString("entityType"),
        entityId = getString("entityId"),
        operation = getString("operation"),
        payload = getString("payload"),
        createdAt = getLong("createdAt"),
        attemptCount = getInt("attemptCount"),
        lastAttemptAt = if (isNull("lastAttemptAt")) null else getLong("lastAttemptAt"),
    )

    private fun JSONObject.toState() = SyncStateEntity(
        accountId = getString("accountId"),
        checkpoint = if (isNull("checkpoint")) null else getString("checkpoint"),
        lastSuccessAt = getLong("lastSuccessAt"),
        status = optString("status", "Idle"),
    )

    private fun JSONObject.toVersion() = SyncEntityVersionEntity(
        accountId = getString("accountId"),
        entityType = getString("entityType"),
        entityId = getString("entityId"),
        serverVersion = getLong("serverVersion"),
        deleted = optBoolean("deleted", false),
    )

    private fun JSONArray.objects(): Sequence<JSONObject> =
        (0 until length()).asSequence().map { getJSONObject(it) }
}
