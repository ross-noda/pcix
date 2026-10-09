package com.example.pix

import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import com.example.pix.cloud.HabitCodec
import java.io.StringReader
import java.io.StringWriter
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitCsvTest {
    private val header = "Habit;Date;Total log;Unit;Status;Habit ID\r\n"
    private val day = LocalDate.parse("2026-10-02").toEpochDay()
    private fun read(body: String) = HabitCsv.read(StringReader(header + body))
    @Test fun readsBomQuotedNamesNewlinesAndDoubledQuotes() {
        val csv = "\uFEFF" + header + "\"A; B\n\"\"C\"\"\";2026-10-02;2.0;rep;Failed;foreign-id\r\n"
        val habit = HabitCsv.read(StringReader(csv)).habits.single()
        assertEquals("A; B\n\"C\"", habit.name)
        assertEquals(2, habit.entries.single().count)
        assertEquals("Failed", habit.entries.single().status)
    }
    @Test fun stableIdentityAndDuplicateRowsAreIdempotent() {
        val row = "Water;2026-10-02;3;rep;Completed;foreign-id\n"
        val doc = read(row + row)
        assertEquals(1, doc.logCount)
        assertEquals(doc.habits.single().id, read(row).habits.single().id)
        assertNotEquals(doc.habits.single().id, HabitCsv.localId("different-id"))
        val uuid = "11111111-1111-1111-1111-111111111111"
        assertEquals(uuid, HabitCsv.localId(uuid))
    }
    @Test fun rejectsConflictsInvalidNumbersUnknownStateAndMalformedQuotes() {
        listOf(
            "Water;2026-10-02;1.5;rep;Completed;id",
            "Water;2026-10-02;-1;rep;Completed;id",
            "Water;2026-10-02;1000001;rep;Completed;id",
            "Water;2026-02-30;1;rep;Completed;id",
            "Water;2026-10-02;1;rep;Unexpected;id",
            "\"Water;2026-10-02;1;rep;Completed;id",
            "Water;2026-10-02;1;rep;Completed;id\nWater;2026-10-02;2;rep;Completed;id",
            "Water;2026-10-02;1;rep;Completed;id\nOther;2026-10-01;2;rep;Completed;id",
        ).forEach { body -> assertTrue(body, runCatching { read(body) }.exceptionOrNull() is HabitCsv.Invalid) }
    }
    @Test fun futureRecordsArePreservedAndInvalidFieldsAreIdentified() {
        val future = LocalDate.now().plusYears(10)
        assertEquals(future.toEpochDay(), read("Water;$future;1;rep;Completed;id").habits.single().entries.single().day)
        val cases = mapOf(
            "Water;2026-02-30;1;rep;Completed;id" to HabitCsv.Reason.DATE,
            "Water;2026-10-02;1.5;rep;Completed;id" to HabitCsv.Reason.COUNT,
            "Water;2026-10-02;1;rep;Unexpected;id" to HabitCsv.Reason.STATUS,
        )
        cases.forEach { (row, reason) ->
            val error = runCatching { read(row) }.exceptionOrNull() as HabitCsv.Invalid
            assertEquals(2, error.row); assertEquals(reason, error.reason)
        }
    }
    @Test fun exportPreservesEmptyStatusOriginalIdUnitsAndHabitsWithoutLogs() {
        val h = HabitEntity(name = "Water; \"Daily\"", csvId = "external-id", unit = "rep")
        val empty = HabitEntity(name = "No history")
        val rules = listOf(HabitRuleEntity(habitId = h.id, effectiveDay = day, quantity = true, target = 3))
        val logs = listOf(HabitLogEntity("l", h.id, day, count = 2, sourceStatus = ""))
        val out = StringWriter(); HabitCsv.write(out, listOf(h, empty), rules, logs)
        assertTrue(out.toString().startsWith(header))
        val doc = HabitCsv.read(StringReader(out.toString()))
        assertEquals(2, doc.habits.size)
        val imported = doc.habits.first { it.sourceId == "external-id" }
        assertEquals(h.name, imported.name); assertEquals("", imported.entries.single().status)
        assertEquals(2, imported.entries.single().count)
        assertTrue(doc.habits.first { it.sourceId == empty.id }.entries.isEmpty())
    }
    @Test fun originalStatusesOverrideCountsWithoutInventingMissingDates() {
        val rule = HabitRuleEntity(habitId = "h", effectiveDay = day-10, quantity = true, enabled = false)
        val logs = listOf(
            HabitLogEntity("a", "h", day-4, 5, sourceStatus = "Failed"),
            HabitLogEntity("b", "h", day-3, 0, sourceStatus = "Completed"),
            HabitLogEntity("c", "h", day-2, 2, sourceStatus = ""),
            HabitLogEntity("d", "h", day-1, 1, sourceStatus = "Inprogress"),
        )
        val stats = HabitRules.stats(listOf(rule), logs, day)
        assertEquals(1, stats.completed); assertEquals(2, stats.failed)
        assertEquals(33, stats.consistency); assertEquals(8, stats.total.toInt())
        assertFalse(HabitRules.complete(rule, logs[0])); assertTrue(HabitRules.complete(rule, logs[1]))
    }
    @Test fun cloudCodecKeepsBlankDistinctFromAbsent() {
        val log = HabitLogEntity("a", "h", day, 1, sourceStatus = "")
        assertEquals(log, HabitCodec.parseHabitLog(HabitCodec.habitLog(log)))
        assertNull(HabitCodec.parseHabitLog(HabitCodec.habitLog(log.copy(sourceStatus = null))).sourceStatus)
        val habit = HabitEntity(name = "X", csvId = "foreign-id", unit = "rep")
        assertEquals(habit, HabitCodec.parseHabit(HabitCodec.habit(habit)))
    }
    @Test fun suppliedAttachmentCanBeParsedWithoutChangingAnyRecord() {
        val path = System.getenv("PIX_HABIT_CSV_FIXTURE")
        org.junit.Assume.assumeTrue(path != null)
        val doc = java.io.File(requireNotNull(path)).reader(Charsets.UTF_8).use { HabitCsv.read(it) }
        assertEquals(17, doc.habits.size); assertEquals(2201, doc.logCount)
        assertEquals(mapOf("Completed" to 1075, "Failed" to 1092, "" to 28, "Inprogress" to 6), doc.habits.flatMap { it.entries }.groupingBy { it.status }.eachCount())
        val habits = doc.habits.map { HabitEntity(id = it.id, name = it.name, csvId = it.sourceId, unit = it.unit) }
        val logs = doc.habits.flatMap { h -> h.entries.map { HabitLogEntity("${h.id}:${it.day}", h.id, it.day, it.count, sourceStatus = it.status) } }
        val out = StringWriter(); HabitCsv.write(out, habits, emptyList(), logs)
        val roundTrip = HabitCsv.read(StringReader(out.toString()))
        assertEquals(doc.habits.associateBy { it.sourceId }, roundTrip.habits.associateBy { it.sourceId })
    }
}
