package com.example.pix.data

import com.example.pix.cloud.tracked
import androidx.room.withTransaction
import com.example.pix.domain.HabitRules
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.util.UUID

class HabitRepository(private val db: PixDatabase, private val changed: () -> Unit = {},
    private val accountMutex: Mutex = Mutex(), private val canMutate: () -> Boolean = { true }) {
    private val dao = db.habitDao()
    val habits = dao.observeHabits()
    val groups = dao.observeGroups()
    val rules = dao.observeRules()
    fun day(day: Long) = dao.observeDay(day)
    fun history(id: String) = dao.observeHistory(id)
    private suspend fun <T> mutate(block: suspend () -> T): T = accountMutex.withLock {
        check(canMutate())
        db.tracked(block).also { changed() }
    }
    data class CsvPreview(val document: HabitCsv.Document, val newHabits: Int, val newLogs: Int, val existingLogs: Int, val futureLogs: Int = 0)
    data class CsvResult(val newHabits: Int, val writtenLogs: Int, val keptLogs: Int)
    suspend fun prepareCsv(reader: java.io.Reader): CsvPreview {
        val document = HabitCsv.read(reader)
        return db.withTransaction {
            val ids = dao.habits().map { it.id }.toSet()
            val keys = dao.logs().map { it.habitId to it.day }.toSet()
            val existing = document.habits.sumOf { h -> h.entries.count { h.id to it.day in keys } }
            CsvPreview(document, document.habits.count { it.id !in ids }, document.logCount - existing, existing, document.habits.sumOf { h -> h.entries.count { it.day > LocalDate.now().toEpochDay() } })
        }
    }
    suspend fun exportCsv(writer: java.io.Writer) {
        val snapshot = accountMutex.withLock {
            check(canMutate())
            db.withTransaction { Triple(dao.habits(), dao.rules(), dao.logs()) }
        }
        HabitCsv.write(writer, snapshot.first, snapshot.second, snapshot.third)
    }
    suspend fun importCsv(document: HabitCsv.Document, replaceExisting: Boolean): CsvResult = mutate {
        val today = LocalDate.now().toEpochDay()
        val now = System.currentTimeMillis()
        val existing = dao.habits().associateBy { it.id }
        val existingLogs = dao.logs().associateBy { it.habitId to it.day }
        var added = 0; var written = 0; var kept = 0
        val maxOrder = existing.values.maxOfOrNull { it.sortOrder } ?: 0L
        document.habits.forEach { source ->
            val id = source.id
            if (existing[id] == null) {
                added++
                dao.save(HabitEntity(id = id, name = source.name, csvId = source.sourceId, unit = source.unit,
                    sortOrder = maxOrder + added, createdAt = now, updatedAt = now))
                val firstDay = source.entries.minOfOrNull { it.day } ?: today
                // A log export cannot prove the old frequency. Missing historic dates are unknown.
                if (firstDay < today) dao.save(HabitRuleEntity(
                    id = UUID.nameUUIDFromBytes("habit-rule:$id:$firstDay".toByteArray(Charsets.UTF_8)).toString(),
                    habitId = id, effectiveDay = firstDay, quantity = true, enabled = false))
                dao.save(HabitRuleEntity(
                    id = UUID.nameUUIDFromBytes("habit-rule:$id:$today".toByteArray(Charsets.UTF_8)).toString(),
                    habitId = id, effectiveDay = today, quantity = true))
            } else require(existing.getValue(id).unit == source.unit)
            source.entries.forEach { entry ->
                require(entry.day in -719162L..2932896L && entry.count in 0..1000000 && entry.status in HabitCsv.statuses)
                val previous = existingLogs[id to entry.day]
                if (previous != null && !replaceExisting) kept++
                else {
                    val logId = previous?.id ?: UUID.nameUUIDFromBytes("habit:$id:${entry.day}".toByteArray(Charsets.UTF_8)).toString()
                    val row = HabitLogEntity(logId, id, entry.day, entry.count, entry.status == "Skipped",
                        previous?.createdAt ?: now, now, sourceStatus = entry.status)
                    if (previous == null || previous.copy(updatedAt = now) != row) dao.save(row)
                    written++
                }
            }
        }
        CsvResult(added, written, kept)
    }
    suspend fun save(habit: HabitEntity, rule: HabitRuleEntity, today: Long = LocalDate.now().toEpochDay()) = mutate {
        require(habit.name.isNotBlank() && habit.name.length <= 200)
        require(habit.reminderMinute == null || habit.reminderMinute in 0..1439)
        HabitRules.validate(rule)
        val existing = dao.habit(habit.id)
        val revisions = dao.rules().filter { it.habitId == habit.id }
        val previous = HabitRules.at(revisions, today)
        dao.save(habit.copy(name = habit.name.trim(), sortOrder = existing?.sortOrder ?: ((dao.habits().maxOfOrNull { it.sortOrder } ?: -1L) + 1L), updatedAt = System.currentTimeMillis()))
        if (existing != null) revisions.filter { it.effectiveDay > today }.forEach { dao.deleteRule(it.id) }
        val effective = if (existing == null) rule.startDay else today
        val sameDay = revisions.find { it.effectiveDay == effective }
        val next = rule.copy(id = sameDay?.id ?: UUID.nameUUIDFromBytes("habit-rule:${habit.id}:$effective".toByteArray(Charsets.UTF_8)).toString(), habitId = habit.id, effectiveDay = effective, enabled = habit.active, updatedAt = System.currentTimeMillis())
        if (previous == null || previous.copy(id = next.id, effectiveDay = next.effectiveDay, updatedAt = next.updatedAt) != next) dao.save(next)
    }
    suspend fun reorder(source: String, target: String) = mutate {
        val current = dao.habits()
        val before = current.associateBy { it.id }
        val now = System.currentTimeMillis()
        HabitRules.manualOrder(current, source, target).forEach { habit ->
            if (before[habit.id]?.sortOrder != habit.sortOrder) dao.save(habit.copy(updatedAt = now))
        }
    }
    suspend fun update(habit: HabitEntity) = mutate {
        val previous = requireNotNull(dao.habit(habit.id))
        if (previous.active != habit.active) {
            val today = LocalDate.now().toEpochDay()
            val revisions = dao.rules().filter { it.habitId == habit.id }
            val rule = HabitRules.at(revisions, today) ?: revisions.minByOrNull { it.effectiveDay }
            if (rule != null) {
                revisions.filter { it.effectiveDay > today }.forEach { dao.deleteRule(it.id) }
                val key = revisions.find { it.effectiveDay == today }?.id
                    ?: UUID.nameUUIDFromBytes("habit-rule:${habit.id}:$today".toByteArray(Charsets.UTF_8)).toString()
                dao.save(rule.copy(id = key, effectiveDay = today, enabled = habit.active, updatedAt = System.currentTimeMillis()))
            }
        }
        dao.save(habit.copy(updatedAt = System.currentTimeMillis()))
    }
    suspend fun delete(id: String) = mutate { dao.deleteHabit(id) }
    suspend fun group(group: HabitGroupEntity) = mutate { require(group.name.isNotBlank()); dao.save(group.copy(name = group.name.trim(), updatedAt = System.currentTimeMillis())) }
    suspend fun deleteGroup(id: String) = mutate { dao.deleteGroup(id) }
    suspend fun record(id: String, day: Long, delta: Int? = null, count: Int? = null, skipped: Boolean = false, completeOnly: Boolean = false, guard: () -> Boolean = { true }) = mutate {
        check(guard())
        require(day <= LocalDate.now().toEpochDay())
        val habit = requireNotNull(dao.habit(id))
        require(habit.active)
        val previous = dao.log(id, day)
        val revisions = dao.rules().filter { it.habitId == id }
        val rule = requireNotNull(HabitRules.at(revisions, day)
            ?: revisions.minByOrNull { it.effectiveDay }?.takeIf { previous != null }?.copy(quantity = true))
        require(HabitRules.scheduled(rule, day) || previous != null)
        val value = count?.also { require(it in 0..1000000) }
            ?: if (completeOnly && !rule.quantity) 1 else HabitRules.changedCount(rule, previous?.count ?: 0, delta ?: rule.step)
        require(rule.quantity || value in 0..1)
        val key = previous?.id ?: UUID.nameUUIDFromBytes("habit:$id:$day".toByteArray(Charsets.UTF_8)).toString()
        val historicalStatus = if (previous?.sourceStatus != null && !HabitRules.scheduled(rule, day)) {
            when { skipped -> "Skipped"; value >= rule.target -> "Completed"; value > 0 -> "Inprogress"; else -> "Failed" }
        } else null
        dao.save(HabitLogEntity(key, id, day, if (skipped) 0 else value, skipped,
            previous?.createdAt ?: System.currentTimeMillis(), System.currentTimeMillis(), sourceStatus = historicalStatus))
    }
}
