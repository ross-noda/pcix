package com.example.pix.widget

import android.app.Application
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.pm.PackageManager
import org.robolectric.Shadows.shadowOf
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.example.pix.R
import android.os.Bundle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class WidgetBackgroundAndroidTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = RuntimeEnvironment.getApplication()

    @Test fun choicesSurviveStoreRecreationAndAreIndependentForTenInstances() {
        val store = WidgetBackgroundStore(context)
        (1..10).forEach { assertEquals(0, store.read(it)) }
        (1..10).forEach { store.write(it, it % 3) }
        val reopened = WidgetBackgroundStore(context)
        (1..10).forEach { assertEquals(it % 3, reopened.read(it)) }
        reopened.write(1, 2)
        (2..10).forEach { assertEquals(it % 3, reopened.read(it)) }
    }

    @Test fun activeCompositionUpdatesWithoutResizeAndDoesNotAffectOtherInstance() {
        val store = WidgetBackgroundStore(context)
        var first = Color.Magenta
        var second = Color.Magenta
        compose.setContent {
            first = widgetBackground(context, 1)
            second = widgetBackground(context, 2)
        }
        compose.waitUntil { first == Color.Black && second == Color.Black }
        listOf(1, 2, 0, 2, 1, 0).forEach { mode ->
            compose.runOnIdle { store.write(1, mode) }
            compose.waitUntil { first == widgetBackdrop(mode) }
            compose.runOnIdle { assertEquals(Color.Black, second) }
        }
    }

    @Test fun legacyChoiceMigratesOnceAndNewChoiceWins() {
        val manager = AppWidgetManager.getInstance(context)
        shadowOf(manager).addBoundWidget(1, AppWidgetProviderInfo().apply {
            provider = ComponentName(context, TaskWidgetReceiver::class.java)
        })
        manager.updateAppWidgetOptions(1, Bundle().apply {
            putInt("com.example.pix.widget.BACKGROUND", 2)
        })
        val store = WidgetBackgroundStore(context)
        assertEquals(2, store.read(1))
        store.write(1, 1)
        assertEquals(1, WidgetBackgroundStore(context).read(1))
    }

    @Test fun removingOneInstanceDoesNotClearAnother() {
        val store = WidgetBackgroundStore(context)
        store.write(1, 2)
        store.write(2, 1)
        store.delete(1)
        assertEquals(0, store.read(1))
        assertEquals(1, store.read(2))
    }

    @Test fun allFiveProvidersHaveAnExportedReconfigurationScreen() {
        val providers = listOf(TaskWidgetReceiver::class.java, HabitWidgetReceiver::class.java,
            CalendarWeekWidgetReceiver::class.java, CalendarMonthWidgetReceiver::class.java,
            MatrixWidgetReceiver::class.java)
        providers.forEach { provider ->
            val receiver = context.packageManager.getReceiverInfo(ComponentName(context, provider), PackageManager.GET_META_DATA)
            val xml = receiver.loadXmlMetaData(context.packageManager, "android.appwidget.provider")!!
            xml.use {
                while (it.eventType != org.xmlpull.v1.XmlPullParser.START_TAG) it.next()
                val ns = "http://schemas.android.com/apk/res/android"
                val config = it.getAttributeValue(ns, "configure")
                assertNotNull(provider.simpleName, config)
                val activity = context.packageManager.getActivityInfo(ComponentName(context.packageName, config), 0)
                assertTrue(provider.simpleName, activity.exported)
                assertTrue(provider.simpleName, it.getAttributeIntValue(ns, "widgetFeatures", 0) and 1 != 0)
            }
        }
    }

    @Test fun selectingAChoiceIsADraftUntilSaved() {
        val store = WidgetBackgroundStore(context)
        var draft = -1
        compose.setContent {
            WidgetBackgroundChoices(0) { draft = it }
        }
        compose.onNodeWithText(context.getString(R.string.widget_background_clear)).performClick()
        compose.runOnIdle {
            assertEquals(2, draft)
            assertEquals(0, store.read(1))
            store.write(1, draft)
            assertEquals(2, store.read(1))
        }
    }

    @Test fun allWidgetsUseSameOpaqueAndSemiTransparentBlack() {
        assertEquals(Color.Black, widgetBackdrop(0))
        assertEquals(Color.Black.copy(alpha = .55f), widgetBackdrop(1))
        assertEquals(0f, widgetBackdrop(2).alpha, .001f)
        context.getSharedPreferences("appearance", 0).edit().putInt("theme", 1).commit()
        assertEquals(Color(0xFFEDEDF0), WidgetPalette.forContext(context).primaryText)
    }
}
