package com.example.pix.widget

import com.example.pix.data.*
import org.junit.Assert.*
import org.junit.Test

class HabitWidgetTest {
    @Test fun completionFillRespectsTargetsAndImportedStates() {
        val rule = HabitRuleEntity(habitId = "h", effectiveDay = 20000, quantity = true, target = 2)
        val log = HabitLogEntity("l", "h", 20000, 1)
        assertEquals(.5f, habitWidgetProgress(rule, log), .001f)
        assertEquals(1f, habitWidgetProgress(rule, log.copy(count = 3)), .001f)
        assertEquals(0f, habitWidgetProgress(rule, log.copy(skipped = true)), .001f)
        assertEquals(0f, habitWidgetProgress(rule, log.copy(sourceStatus = "Failed")), .001f)
        assertEquals(1f, habitWidgetProgress(rule, log.copy(count = 0, sourceStatus = "Completed")), .001f)
        assertTrue(habitWidgetProgress(rule, log.copy(count = 3, sourceStatus = "Inprogress")) < 1f)
    }
    @Test fun snapshotUsesTodayScheduleManualOrderAndCompletedPartition() {
        val day = 20000L
        val habits = listOf("done","counter","pending","archived","later").mapIndexed { i,id -> HabitEntity(id=id,name=id,sortOrder=i.toLong(),active=id!="archived") }
        val rules = habits.map { HabitRuleEntity(habitId=it.id,effectiveDay=day,startDay=if(it.id=="later")day+1 else day,quantity=it.id=="counter") }
        val logs = listOf(HabitLogEntity("a","done",day,1),HabitLogEntity("b","counter",day,2),HabitLogEntity("c","pending",day-1,1))
        val rows = habitWidgetRows(habits.reversed(),rules,logs,day)
        assertEquals(listOf("counter","pending","done"),rows.map { it.habit.id })
        assertNull(rows[1].log)
        assertEquals(2,rows[0].log?.count)
    }
}
