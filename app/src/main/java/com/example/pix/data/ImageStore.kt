package com.example.pix.data

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Immutable app-owned image files; the database stores relative names for backup/restore. */
class ImageStore(private val context: Context) {
    val directory
        get() = File(context.filesDir, "task-images").apply { mkdirs() }

    fun file(name: String) = File(directory, File(name).name)

    fun prune(referenced: Set<String>) {
        val cutoff = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
        directory
            .listFiles()
            ?.filter { it.name !in referenced && it.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    suspend fun import(uri: Uri): String =
        withContext(Dispatchers.IO) {
            val output = File(directory, newId() + ".image")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input)
                    output.outputStream().use { out ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            require(total <= 50L * 1024 * 1024)
                            out.write(buffer, 0, n)
                        }
                    }
                }
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(output.path, bounds)
                require(bounds.outWidth > 0 && bounds.outHeight > 0)
                output.name
            } catch (e: Exception) {
                output.delete()
                throw e
            }
        }
}
