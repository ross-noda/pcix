package com.example.pix.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.pix.PixApplication
import kotlinx.coroutines.launch

class TaskWidgetDateChangeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        WidgetDayWorker.schedule(context, androidx.work.ExistingWorkPolicy.REPLACE)
        val pending = goAsync()
        val app = context.applicationContext as PixApplication
        app.backgroundScope.launch {
            try {
                updateAllPixWidgets(context)
            } finally {
                pending.finish()
            }
        }
    }
}
