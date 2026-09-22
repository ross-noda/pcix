package com.example.pix

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.ui.taskShareIntent
import org.junit.Assert.*
import org.junit.Test

class TaskSharingTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun detail() =
        TaskWithDetails(
            TaskEntity(
                title = "Un progetto",
                notes = "Note",
                dueDay = 20714,
                minuteOfDay = 540,
                durationMinutes = 90,
                priority = 1,
            ),
            null,
            ListEntity(id = INBOX_ID, name = "Inbox"),
            listOf(TagEntity(name = "Focus", normalizedName = "focus")),
            listOf(SubtaskEntity(taskId = "t", title = "Finito", isCompleted = true)),
        )

    @Test
    fun textShareIncludesIntervalTagsAndChecklist() {
        val intent = taskShareIntent(context, detail())
        assertEquals(Intent.ACTION_SEND, intent.action)
        assertEquals("text/plain", intent.type)
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)!!
        assertTrue(text.contains("09:00"))
        assertTrue(text.contains("10:30"))
        assertTrue(text.contains("#Focus"))
        assertTrue(text.contains("[x] Finito"))
        assertFalse(intent.hasExtra(Intent.EXTRA_STREAM))
    }

    @Test
    fun imagesUseContentUrisAndTemporaryReadGrants() {
        val intent =
            taskShareIntent(
                context,
                detail()
                    .copy(
                        images =
                            listOf(
                                TaskImage(taskId = "t", fileName = "one.image"),
                                TaskImage(taskId = "t", fileName = "two.image"),
                            )
                    ),
            )
        assertEquals(Intent.ACTION_SEND_MULTIPLE, intent.action)
        assertEquals(2, intent.clipData!!.itemCount)
        assertEquals("content", intent.clipData!!.getItemAt(0).uri.scheme)
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
    }
}
