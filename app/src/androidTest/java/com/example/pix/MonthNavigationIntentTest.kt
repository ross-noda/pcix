package com.example.pix

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.pix.widget.MonthWidgetInteractionReceiver
import com.example.pix.widget.monthNavigationIntent
import org.junit.Assert.*
import org.junit.Test

class MonthNavigationIntentTest {
    @Test fun previousNextAndDifferentInstancesHaveDistinctPendingIntentIdentities() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val previous = monthNavigationIntent(context, 10, -1)
        val next = monthNavigationIntent(context, 10, 1)
        val other = monthNavigationIntent(context, 11, 1)
        assertFalse(previous.filterEquals(next))
        assertFalse(next.filterEquals(other))
        assertTrue(next.filterEquals(monthNavigationIntent(context, 10, 1)))
        assertEquals(MonthWidgetInteractionReceiver.PREVIOUS, previous.action)
        assertEquals(MonthWidgetInteractionReceiver.NEXT, next.action)
        assertEquals(10, next.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1))
        assertEquals(ComponentName(context, MonthWidgetInteractionReceiver::class.java), next.component)
        assertTrue(next.flags and Intent.FLAG_RECEIVER_FOREGROUND != 0)
    }
    @Test(expected = IllegalArgumentException::class)
    fun unsupportedNavigationIsRejected() {
        monthNavigationIntent(ApplicationProvider.getApplicationContext(), 10, 2)
    }
}
