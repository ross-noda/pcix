package com.example.pix.ui

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.TaskTiming
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** Sends only the selected task and its images through Android's user-controlled chooser. */
fun taskShareIntent(context: Context, detail: TaskWithDetails): Intent {
    val task = detail.task
    val text = buildString {
        appendLine(task.title)
        appendLine(detail.list.icon + " " + detail.list.name)
        task.dueDay?.let {
            append(LocalDate.ofEpochDay(it))
            task.minuteOfDay?.let { minute ->
                append(" · %02d:%02d".format(minute / 60, minute % 60))
            }
            TaskTiming.end(task)?.let { end ->
                append(
                    " → " +
                        if (task.minuteOfDay == null) end.toLocalDate().minusDays(1).toString()
                        else end.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                )
            }
            appendLine()
        }
        appendLine(
            context.getString(R.string.priority) +
                ": " +
                context.getString(
                    when (task.priority) {
                        5 -> R.string.high
                        3 -> R.string.medium
                        1 -> R.string.low
                        else -> R.string.none
                    }
                )
        )
        if (detail.tags.isNotEmpty()) appendLine(detail.tags.joinToString(" ") { "#" + it.name })
        if (task.notes.isNotBlank()) {
            appendLine()
            appendLine(task.notes)
        }
        detail.subtasks
            .sortedWith(compareBy<SubtaskEntity> { it.isCompleted }.thenBy { it.sortOrder })
            .forEach { appendLine((if (it.isCompleted) "[x] " else "[ ] ") + it.title) }
        detail.series?.let { appendLine(context.getString(R.string.recurrence) + ": " + it.rule) }
    }
    val images =
        detail.images.map { image ->
            FileProvider.getUriForFile(
                context,
                context.packageName + ".sharing",
                ImageStore(context).file(image.fileName),
            )
        }
    val intent =
        Intent(if (images.size > 1) Intent.ACTION_SEND_MULTIPLE else Intent.ACTION_SEND).apply {
            type = if (images.isEmpty()) "text/plain" else "image/*"
            putExtra(Intent.EXTRA_SUBJECT, task.title)
            putExtra(Intent.EXTRA_TEXT, text)
            if (images.size == 1) putExtra(Intent.EXTRA_STREAM, images.first())
            if (images.size > 1) putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(images))
            if (images.isNotEmpty()) {
                clipData =
                    ClipData.newUri(context.contentResolver, task.title, images.first()).also { clip
                        ->
                        images.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
    return intent
}

fun shareTask(context: Context, detail: TaskWithDetails) {
    context.startActivity(
        Intent.createChooser(
            taskShareIntent(context, detail),
            context.getString(R.string.share_task),
        )
    )
}
