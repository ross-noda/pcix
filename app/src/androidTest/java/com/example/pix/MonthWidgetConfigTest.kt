package com.example.pix

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import com.example.pix.widget.*
import org.junit.Assert.*
import org.junit.Test

class MonthWidgetConfigTest {
    @Test fun independentWidgetsPersistSelectionsAndCalendarAccountIdentity() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefix = "month-test-${java.util.UUID.randomUUID()}"
        val context = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences(prefix, mode)
        }
        try {
            val store = MonthWidgetConfigStore(context)
            val first = monthCalendarKey("a", "shared-calendar")
            val second = monthCalendarKey("b", "shared-calendar")
            assertNotEquals(first, second)
            val config = MonthWidgetConfig(showGoogle = true, listIds = setOf("a", "b"), tagIds = setOf("x"), priorities = setOf(5), calendarIds = setOf(first))
            store.write(1, config)
            store.write(2, config.copy(showTasks = false, calendarIds = setOf(second)))
            assertEquals(config, MonthWidgetConfigStore(context).read(1))
            assertFalse(store.read(2).showTasks)
            assertEquals(setOf(second), store.read(2).calendarIds)
            store.remove(1)
            assertEquals(MonthWidgetConfig(), store.read(1))
            assertEquals(setOf(second), store.read(2).calendarIds)
        } finally { base.deleteSharedPreferences(prefix) }
    }
}
