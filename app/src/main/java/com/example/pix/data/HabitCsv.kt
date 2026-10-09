package com.example.pix.data

import com.example.pix.domain.HabitRules
import java.io.Reader
import java.io.Writer
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/** Six-column semicolon interchange. CSV values are data, never expressions or commands. */
object HabitCsv {
    val header = listOf("Habit", "Date", "Total log", "Unit", "Status", "Habit ID")
    const val MAX_CHARS = 16 * 1024 * 1024
    const val MAX_ROWS = 100000
    val statuses = setOf("", "Completed", "Failed", "Inprogress", "Skipped")
    enum class Reason { FORMAT, IDENTITY, UNIT, DATE, COUNT, STATUS, DUPLICATE }
    class Invalid(val row: Int, val reason: Reason = Reason.FORMAT, cause: Throwable? = null) : IllegalArgumentException("Invalid habit CSV record $row ($reason)", cause)
    data class Entry(val day: Long, val count: Int, val status: String)
    data class Habit(val sourceId: String, val name: String, val unit: String, val entries: List<Entry>) {
        val id: String get() = localId(sourceId)
    }
    data class Document(val habits: List<Habit>) {
        val logCount: Int get() = habits.sumOf { it.entries.size }
    }
    fun localId(sourceId: String): String = runCatching {
        UUID.fromString(sourceId).also { require(it.toString().equals(sourceId, ignoreCase = true)) }.toString()
    }.getOrElse { UUID.nameUUIDFromBytes("pcix-habit-csv:$sourceId".toByteArray(Charsets.UTF_8)).toString() }

    // Explicit CSV import preserves dates independently of the device clock.
    fun read(reader: Reader): Document {
        val text = StringBuilder()
        val buffer = CharArray(8192)
        while (true) {
            val n = reader.read(buffer)
            if (n < 0) break
            if (text.length + n > MAX_CHARS) throw Invalid(0)
            text.append(buffer, 0, n)
        }
        val rows = parse(text.toString().removePrefix("\uFEFF"))
        if (rows.isEmpty() || rows.first().map { it.trim() } != header) throw Invalid(1)
        val metadata = linkedMapOf<String, Pair<String, String>>()
        val logs = linkedMapOf<String, LinkedHashMap<Long, Entry>>()
        rows.drop(1).forEachIndexed { index, row ->
            val number = index + 2
            var reason = Reason.FORMAT
            try {
                require(row.size == 6)
                reason = Reason.IDENTITY
                val name = row[0].trim(); val sourceId = row[5].trim(); val unit = row[3].trim()
                require(name.isNotBlank() && name.length <= 200 && sourceId.isNotBlank() && sourceId.length <= 256)
                reason = Reason.UNIT
                require(unit.isNotBlank() && unit.length <= 40)
                reason = Reason.IDENTITY
                val meta = name to unit
                require(metadata[sourceId] == null || metadata[sourceId] == meta)
                metadata[sourceId] = meta
                val entries = logs.getOrPut(sourceId) { linkedMapOf() }
                // A habit without logs has a definition row, with all daily values blank.
                if (row[1].isBlank()) require(row[2].isBlank() && row[4].isBlank())
                else {
                    reason = Reason.DATE
                    val day = LocalDate.parse(row[1].trim()).toEpochDay()
                    require(day in -719162L..2932896L)
                    reason = Reason.COUNT
                    val count = BigDecimal(row[2].trim()).intValueExact()
                    require(count in 0..1000000)
                    reason = Reason.STATUS
                    val status = statuses.firstOrNull { it.equals(row[4].trim(), ignoreCase = true) }
                    require(status != null)
                    val entry = Entry(day, count, status)
                    reason = Reason.DUPLICATE
                    require(entries[day] == null || entries[day] == entry)
                    entries[day] = entry // Identical duplicate rows are harmless; conflicts reject the file.
                }
            } catch (e: Exception) { throw Invalid(number, reason, e) }
        }
        if (metadata.isEmpty() || metadata.keys.map(::localId).toSet().size != metadata.size) throw Invalid(1)
        return Document(metadata.map { (id, meta) -> Habit(id, meta.first, meta.second, logs.getValue(id).values.sortedBy { it.day }) })
    }

    /** Quoted delimiters/newlines and doubled quotes; reject malformed quoting rather than shift data. */
    private fun parse(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>(); val cell = StringBuilder()
        var quoted = false; var closed = false; var i = 0
        fun addCell() { row.add(cell.toString()); cell.setLength(0); closed = false }
        fun addRow() {
            addCell()
            if (row.any { it.isNotEmpty() }) rows.add(row.toList())
            row = mutableListOf()
            if (rows.size > MAX_ROWS + 1) throw Invalid(rows.size)
        }
        while (i < text.length) {
            val c = text[i++]
            if (quoted) {
                if (c == '"') {
                    if (i < text.length && text[i] == '"') { cell.append('"'); i++ }
                    else { quoted = false; closed = true }
                } else cell.append(c)
            } else when (c) {
                '"' -> { if (cell.isNotEmpty() || closed) throw Invalid(rows.size + 1); quoted = true }
                ';' -> addCell()
                '\r', '\n' -> { if (c == '\r' && i < text.length && text[i] == '\n') i++; addRow() }
                else -> { if (closed) throw Invalid(rows.size + 1); cell.append(c) }
            }
        }
        if (quoted) throw Invalid(rows.size + 1)
        if (row.isNotEmpty() || cell.isNotEmpty() || closed) addRow()
        return rows
    }

    fun write(writer: Writer, habits: List<HabitEntity>, rules: List<HabitRuleEntity>, logs: List<HabitLogEntity>) {
        fun row(values: List<String>) { writer.write(values.joinToString(";") { value ->
            if (value.any { it == ';' || it == '"' || it == '\r' || it == '\n' }) "\"${value.replace("\"", "\"\"")}\"" else value
        }); writer.write("\r\n") }
        row(header)
        val byHabit = logs.groupBy { it.habitId }
        val rulesByHabit = rules.groupBy { it.habitId }
        habits.sortedWith(compareBy({ it.sortOrder }, { it.name }, { it.id })).forEach { habit ->
            val history = byHabit[habit.id].orEmpty().sortedBy { it.day }
            val id = habit.csvId ?: habit.id
            if (history.isEmpty()) row(listOf(habit.name, "", "", habit.unit, "", id))
            history.forEach { log ->
                val rule = HabitRules.at(rulesByHabit[habit.id].orEmpty(), log.day)
                val status = log.sourceStatus ?: when {
                    log.skipped -> "Skipped"
                    rule != null && HabitRules.complete(rule, log) -> "Completed"
                    log.count > 0 -> "Inprogress"
                    else -> "Failed"
                }
                row(listOf(habit.name, LocalDate.ofEpochDay(log.day).toString(), log.count.toString(), habit.unit, status, id))
            }
        }
    }
}
