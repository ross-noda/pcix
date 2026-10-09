package com.example.pix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.pix.PixApplication
import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModel(application: Application, private val saved: SavedStateHandle) : AndroidViewModel(application) {
    private val repository = (application as PixApplication).habits
    val date = saved.getStateFlow("habitDay", LocalDate.now().toEpochDay())
    val group = saved.getStateFlow<String?>("habitGroup", null)
    private val todayFlow = MutableStateFlow(LocalDate.now().toEpochDay())
    val today = todayFlow.asStateFlow()
    val habits = repository.habits.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val groups = repository.groups.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val rules = repository.rules.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val logs = date.flatMapLatest(repository::day).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val selected = MutableStateFlow<String?>(null)
    val history = selected.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else repository.history(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val savingState = MutableStateFlow(false)
    val saving = savingState.asStateFlow()
    val errors = Channel<Unit>(Channel.BUFFERED)
    init { viewModelScope.launch { while (isActive) { val now = LocalDate.now().toEpochDay(); if (now != todayFlow.value) { if (date.value == todayFlow.value) selectDate(now); todayFlow.value = now }; delay(1000) } } }
    private val csvBusyState = MutableStateFlow(false)
    val csvBusy = csvBusyState.asStateFlow()
    private val csvPreviewState = MutableStateFlow<HabitRepository.CsvPreview?>(null)
    val csvPreview = csvPreviewState.asStateFlow()
    private val csvResultState = MutableStateFlow<HabitRepository.CsvResult?>(null)
    val csvResult = csvResultState.asStateFlow()
    private val csvErrorState = MutableStateFlow<Int?>(null)
    val csvError = csvErrorState.asStateFlow()
    private val csvReasonState = MutableStateFlow(HabitCsv.Reason.FORMAT)
    val csvReason = csvReasonState.asStateFlow()
    private val csvExportedState = MutableStateFlow(false)
    val csvExported = csvExportedState.asStateFlow()
    private fun csvOperation(stage: String, block: suspend () -> Unit) {
        if (csvBusyState.value) return
        csvBusyState.value = true
        csvErrorState.value = null; csvResultState.value = null; csvExportedState.value = false
        viewModelScope.launch {
            try { withContext(Dispatchers.IO) { block() } }
            catch (e: CancellationException) { throw e }
            catch (e: HabitCsv.Invalid) {
                android.util.Log.w("PcixCsv", "$stage rejected: record=${e.row}, reason=${e.reason}, cause=${e.cause?.javaClass?.simpleName}")
                csvReasonState.value = e.reason; csvErrorState.value = e.row
            }
            catch (e: Exception) {
                android.util.Log.w("PcixCsv", "$stage failed: ${e.javaClass.simpleName}")
                csvErrorState.value = -1
            }
            finally { csvBusyState.value = false }
        }
    }
    fun prepareCsv(uri: android.net.Uri) = csvOperation("read") {
        csvPreviewState.value = null
        val stream = requireNotNull(getApplication<Application>().contentResolver.openInputStream(uri))
        java.io.InputStreamReader(stream, Charsets.UTF_8.newDecoder()).use {
            val preview = repository.prepareCsv(it)
            android.util.Log.i("PcixCsv", "read succeeded: habits=${preview.document.habits.size}, logs=${preview.document.logCount}")
            csvPreviewState.value = preview
        }
    }
    fun exportCsv(uri: android.net.Uri) = csvOperation("export") {
        requireNotNull(getApplication<Application>().contentResolver.openOutputStream(uri, "wt"))
            .bufferedWriter(Charsets.UTF_8).use { repository.exportCsv(it) }
        csvExportedState.value = true
    }
    fun importCsv(replaceExisting: Boolean) {
        val pending = csvPreviewState.value ?: return
        csvOperation("import") {
            val result = repository.importCsv(pending.document, replaceExisting)
            android.util.Log.i("PcixCsv", "import succeeded: habits=${result.newHabits}, written=${result.writtenLogs}, kept=${result.keptLogs}")
            csvResultState.value = result
            csvPreviewState.value = null
        }
    }
    fun cancelCsv() { if (!csvBusyState.value) csvPreviewState.value = null }
    fun selectDate(day: Long) { saved["habitDay"] = day }
    fun selectGroup(id: String?) { saved["habitGroup"] = id }
    fun selectHabit(id: String?) { selected.value = id }
    private fun run(block: suspend () -> Unit) { viewModelScope.launch { try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { errors.send(Unit) } } }
    fun record(id: String, day: Long, count: Int? = null, skip: Boolean = false, delta: Int? = null) = run { repository.record(id, day, delta, count, skip) }
    fun save(habit: HabitEntity, rule: HabitRuleEntity, done: () -> Unit) {
        if (savingState.value) return
        savingState.value = true
        run { try { repository.save(habit, rule); done() } finally { savingState.value = false } }
    }
    fun update(habit: HabitEntity, done: () -> Unit = {}) = run { repository.update(habit); done() }
    fun reorder(source: String, target: String) = run { repository.reorder(source, target) }
    fun delete(id: String, done: () -> Unit) = run { repository.delete(id); done() }
    fun saveGroup(group: HabitGroupEntity) = run { repository.group(group) }
    fun deleteGroup(id: String) = run { repository.deleteGroup(id); if (group.value == id) selectGroup(null) }
}
