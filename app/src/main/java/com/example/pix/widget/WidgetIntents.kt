package com.example.pix.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.appwidget.action.actionStartActivity
import com.example.pix.MainActivity

internal fun newTaskAction(context: Context, initialDay: Long? = null) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_NEW_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                if (initialDay != null) putExtra(MainActivity.EXTRA_INITIAL_DAY, initialDay)
            }
            .setData(
                Uri.parse(
                    if (initialDay == null) "pix://widget/new"
                    else "pix://widget/new/$initialDay"
                )
            )
    )

internal fun taskAction(context: Context, taskId: String) =
    actionStartActivity(
        Intent(context, MainActivity::class.java)
            .setAction(MainActivity.ACTION_OPEN_TASK)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_TASK_ID, taskId)
            .setData(Uri.parse("pix://widget/task/$taskId"))
    )
