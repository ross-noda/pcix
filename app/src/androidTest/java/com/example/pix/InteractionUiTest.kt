package com.example.pix

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class InteractionUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (rule.activity.application as PixApplication).repository

    private fun label(id: Int) = rule.activity.getString(id)

    private fun waitFor(check: suspend () -> Boolean) {
        rule.waitUntil(6000) { runBlocking { check() } }
    }

    private fun screenshot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    @After
    fun reset() {
        rule.activity
            .getSharedPreferences("appearance", 0)
            .edit()
            .putBoolean("manualOrder", false)
            .commit()
    }

    private fun openList(list: ListEntity) {
        rule.onNodeWithTag("navigation-organize").performClick()
        rule.onNodeWithTag("organize-list-${list.id}").performScrollTo().performClick()
    }

    @Test
    fun swipeCompleteUndoAndPostponeArePersisted() {
        val list = ListEntity(name = "Gesti ${System.nanoTime()}")
        val task = TaskEntity(title = "Inviare il documento", listId = list.id)
        runBlocking {
            repo.saveList(list)
            repo.create(task)
        }
        openList(list)
        val row = rule.onNodeWithTag("task-row-${task.id}")
        row.performTouchInput { swipeRight() }
        waitFor { repo.details(task.id)!!.task.isCompleted }
        rule.onNodeWithText(label(R.string.undo)).performClick()
        waitFor { !repo.details(task.id)!!.task.isCompleted }
        row.performTouchInput { swipeLeft() }
        screenshot("swipe-actions")
        rule.onNodeWithText(label(R.string.postpone_task)).performClick()
        rule.onAllNodesWithText(label(R.string.tomorrow)).onLast().performClick()
        waitFor { repo.details(task.id)!!.task.dueDay == LocalDate.now().plusDays(1).toEpochDay() }
        row.performTouchInput { longClick() }
        screenshot("task-actions")
        rule.onNodeWithText(label(R.string.delete)).performClick()
        rule.onNodeWithText(label(R.string.cancel)).performClick()
        assertNotNull(runBlocking { repo.details(task.id) })
    }

    @Test
    fun manualTaskDragAndAccessibleListOrder() {
        val list =
            ListEntity(name = "Ordine ${System.nanoTime()}", sortOrder = System.currentTimeMillis())
        val other =
            ListEntity(name = "Seconda ${System.nanoTime()}", sortOrder = list.sortOrder + 1)
        val a = TaskEntity(title = "Prima attività", listId = list.id, sortOrder = 1)
        val b = TaskEntity(title = "Seconda attività", listId = list.id, sortOrder = 2)
        runBlocking {
            repo.saveList(list)
            repo.saveList(other)
            repo.create(a)
            repo.create(b)
        }
        openList(list)
        rule.onNodeWithContentDescription(label(R.string.more_actions)).performClick()
        rule.onNodeWithText(label(R.string.sort_manual)).performClick()
        val handle = rule.onNodeWithTag("reorder-${a.id}")
        val first = handle.fetchSemanticsNode().boundsInRoot.center
        val target = rule.onNodeWithTag("reorder-${b.id}").fetchSemanticsNode().boundsInRoot.center
        handle.performTouchInput {
            down(center)
            advanceEventTime(650)
            moveTo(center + Offset(0f, target.y - first.y), 300)
            up()
        }
        waitFor { repo.details(b.id)!!.task.sortOrder < repo.details(a.id)!!.task.sortOrder }
        screenshot("manual-order")
        rule.onNodeWithTag("navigation-organize").performClick()
        rule.onNodeWithTag("reorder-${other.id}").performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.move_up)).performClick()
        waitFor {
            repo.lists.first().indexOfFirst { it.list.id == other.id } <
                repo.lists.first().indexOfFirst { it.list.id == list.id }
        }
    }
}
