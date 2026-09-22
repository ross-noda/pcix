package com.example.pix.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.example.pix.PixApplication
import com.example.pix.data.*
import com.example.pix.domain.TaskRules
import java.time.ZonedDateTime
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*

data class TaskContent(
    val loading: Boolean = true,
    val tasks: List<TaskWithDetails> = emptyList(),
    val failed: Boolean = false,
)

data class EditorDraft(
    val task: TaskEntity,
    val tags: Set<String>,
    val recurrenceRule: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class TasksViewModel(app: Application, private val savedState: SavedStateHandle) :
    AndroidViewModel(app) {
    private val repository = (app as PixApplication).repository
    private val backups = BackupRepository(app, (app as PixApplication).database)
    val backupBusy = MutableStateFlow(false)
    val pendingBackup = MutableStateFlow<BackupRepository.Prepared?>(null)
    val backupMessage = MutableStateFlow<Int?>(null)

    fun cancelBackup() {
        pendingBackup.value?.close()
        pendingBackup.value = null
    }

    private fun backupAction(success: Int?, block: suspend () -> Unit) {
        if (backupBusy.value) return
        backupBusy.value = true
        backupMessage.value = null
        viewModelScope.launch {
            try {
                val pendingWrites = CompletableDeferred<Unit>()
                writes.send { pendingWrites.complete(Unit) }
                pendingWrites.await()
                block()
                backupMessage.value = success
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                backupMessage.value = com.example.pix.R.string.backup_error
            } finally {
                backupBusy.value = false
            }
        }
    }

    fun exportBackup(uri: android.net.Uri) =
        backupAction(com.example.pix.R.string.backup_exported) {
            withContext(Dispatchers.IO) {
                getApplication<Application>().contentResolver.openOutputStream(uri, "wt").use {
                    backups.export(requireNotNull(it))
                }
            }
        }

    fun prepareBackup(uri: android.net.Uri) =
        backupAction(null) {
            cancelBackup()
            pendingBackup.value =
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver.openInputStream(uri).use {
                        backups.prepare(requireNotNull(it))
                    }
                }
        }

    fun restoreBackup() {
        val prepared = pendingBackup.value ?: return
        pendingBackup.value = null
        backupAction(com.example.pix.R.string.backup_restored) {
            backups.restore(prepared)
            runCatching { com.example.pix.cloud.OutboxRecorder((getApplication() as PixApplication).database).enqueueAll() }
            filter.value = TaskFilter(mode = startupView.value, showCompleted = true)
            runCatching { com.example.pix.reminders.ReminderWork.reconcile(getApplication()) }
        }
    }

    override fun onCleared() {
        cancelBackup()
    }

    val calendarDate =
        savedState.getStateFlow("calendarDay", java.time.LocalDate.now().toEpochDay())
    val calendarContent =
        calendarDate
            .flatMapLatest { day ->
                repository
                    .day(day)
                    .map { TaskContent(loading = false, tasks = it) }
                    .catch { emit(TaskContent(loading = false, failed = true)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskContent())
    val calendarMarks =
        calendarDate
            .map { java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(it)) }
            .distinctUntilChanged()
            .flatMapLatest { month ->
                val days = com.example.pix.domain.CalendarRules.days(month)
                repository.calendar(days.first().toEpochDay(), days.last().plusDays(1).toEpochDay())
            }
            .catch {
                errorsChannel.emit(Unit)
                emit(emptyList())
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val calendarGoogle =
        calendarDate
            .map { java.time.YearMonth.from(java.time.LocalDate.ofEpochDay(it)) }
            .distinctUntilChanged()
            .flatMapLatest { month ->
                val days = com.example.pix.domain.CalendarRules.days(month)
                (getApplication<Application>() as PixApplication)
                    .google
                    .events(days.first().toEpochDay(), days.last().plusDays(1).toEpochDay())
            }
            .catch { emit(emptyList()) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectDate(day: Long) {
        savedState["calendarDay"] = day
    }

    val preferences = app.getSharedPreferences("appearance", 0)
    val textSize = MutableStateFlow(preferences.getInt("textSize", 1).coerceIn(0, 2))
    val fontStyle = MutableStateFlow(preferences.getInt("fontStyle", 0).coerceIn(0, 2))

    fun setTextSize(value: Int) {
        textSize.value = value.coerceIn(0, 2)
        preferences.edit().putInt("textSize", textSize.value).apply()
    }

    fun setFontStyle(value: Int) {
        fontStyle.value = value.coerceIn(0, 2)
        preferences.edit().putInt("fontStyle", fontStyle.value).apply()
    }

    val startupView = MutableStateFlow(preferences.getString("startupView", "ALL") ?: "ALL")
    val accent = MutableStateFlow(preferences.getInt("accent", 0xFF5275FF.toInt()))
    val dimmedLists =
        MutableStateFlow(preferences.getStringSet("dimmedLists", emptySet())!!.toSet())

    fun setStartupView(value: String) {
        startupView.value = value
        preferences.edit().putString("startupView", value).apply()
    }

    fun setAccent(value: Int) {
        accent.value = value
        preferences.edit().putInt("accent", value).apply()
    }

    fun setDimmedLists(value: Set<String>) {
        dimmedLists.value = value
        preferences.edit().putStringSet("dimmedLists", value).apply()
    }

    val manualOrder = MutableStateFlow(preferences.getBoolean("manualOrder", false))

    fun setManualOrder(value: Boolean) {
        manualOrder.value = value
        preferences.edit().putBoolean("manualOrder", value).apply()
    }

    val filter = MutableStateFlow(TaskFilter(mode = startupView.value, showCompleted = true))
    val search = MutableStateFlow("")
    private val refresh = MutableStateFlow(0)
    private val clock = flow {
        while (true) {
            emit(ZonedDateTime.now())
            delay(1000)
        }
    }
    val now =
        clock.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ZonedDateTime.now())
    private val errorsChannel = MutableSharedFlow<Unit>(extraBufferCapacity = 8)
    val errors = errorsChannel.asSharedFlow()
    val matrixContent =
        combine(now.map { it.toLocalDate() }.distinctUntilChanged(), refresh) { date, _ -> date }
            .flatMapLatest {
                repository
                    .observe(
                        TaskFilter(mode = "ALL"),
                        it.atStartOfDay(java.time.ZoneId.systemDefault()),
                    )
                    .map { rows -> TaskContent(loading = false, tasks = rows) }
                    .catch { emit(TaskContent(loading = false, failed = true)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskContent())
    val lists =
        repository.lists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val tags =
        repository.tags.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val content =
        combine(
                filter,
                search.debounce(250),
                now.map { it.withSecond(0).withNano(0) }.distinctUntilChanged(),
                refresh,
                manualOrder,
            ) { f, query, time, _, manual ->
                f.copy(search = query, manual = manual && query.isBlank()) to time
            }
            .flatMapLatest { (f, time) ->
                repository
                    .observe(f, time)
                    .map { TaskContent(loading = false, tasks = it) }
                    .catch { emit(TaskContent(loading = false, failed = true)) }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TaskContent())
    private val selectedId = MutableStateFlow<String?>(null)
    val selected =
        selectedId
            .flatMapLatest { id -> if (id == null) flowOf(null) else repository.task(id) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val draft = MutableStateFlow<EditorDraft?>(null)
    val isNewDraft = MutableStateFlow(false)
    val scopeRequest = MutableStateFlow(false)
    val editScope = MutableStateFlow<RecurrenceScope?>(null)
    private var pendingScopedAction: (() -> Unit)? = null

    private fun scoped(action: () -> Unit) {
        if (draft.value?.task?.seriesId != null && editScope.value == null) {
            pendingScopedAction = action
            scopeRequest.value = true
        } else action()
    }

    fun chooseScope(scope: RecurrenceScope) {
        editScope.value = scope
        scopeRequest.value = false
        val action = pendingScopedAction
        pendingScopedAction = null
        if (action != null) action() else draft.value?.let { edit(it) }
    }

    fun cancelScope() {
        scopeRequest.value = false
        pendingScopedAction = null
    }

    private suspend fun persist(
        value: EditorDraft,
        session: Long,
        version: Long,
        scope: RecurrenceScope,
    ) {
        if (repository.details(value.task.id) == null) repository.create(value.task, value.tags)
        val actual = repository.editRecurring(value.task, value.tags, value.recurrenceRule, scope)
        if (editorSession == session) {
            isNewDraft.value = false
            savedVersion = maxOf(savedVersion, version)
            draft.value?.let { latest ->
                draft.value =
                    latest.copy(
                        task =
                            latest.task.copy(
                                seriesId = actual.seriesId,
                                originalDay = actual.originalDay,
                            )
                    )
            }
        }
    }

    private var saveJob: Job? = null
    private var editorSession = 0L
    private var editVersion = 0L
    private var savedVersion = 0L
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)
    private val matrixPreferences = app.getSharedPreferences("matrix", 0)
    val matrixConfig =
        MutableStateFlow(
            com.example.pix.domain.MatrixConfig(
                layout = matrixPreferences.getInt("layout", 0).coerceIn(0, 2),
                columnSplit = matrixPreferences.getFloat("columns", .5f).coerceIn(.3f, .7f),
                rowSplit = matrixPreferences.getFloat("rows", .5f).coerceIn(.3f, .7f),
                cornerRadius = matrixPreferences.getFloat("corners", 20f).coerceIn(0f, 32f),
                urgentDays = matrixPreferences.getInt("urgent", 0).coerceIn(0, 30),
                importantPriority =
                    matrixPreferences.getInt("important", 3).takeIf { it in listOf(1, 3, 5) } ?: 3,
            )
        )

    fun setMatrixConfig(value: com.example.pix.domain.MatrixConfig) {
        matrixConfig.value = value
        matrixPreferences
            .edit()
            .putInt("layout", value.layout)
            .putFloat("columns", value.columnSplit)
            .putFloat("rows", value.rowSplit)
            .putFloat("corners", value.cornerRadius)
            .putInt("urgent", value.urgentDays)
            .putInt("important", value.importantPriority)
            .apply()
    }

    val calendarWeekly = MutableStateFlow(preferences.getBoolean("calendarWeekly", false))

    fun setCalendarWeekly(value: Boolean) {
        calendarWeekly.value = value
        preferences.edit().putBoolean("calendarWeekly", value).apply()
    }

    val theme = MutableStateFlow(preferences.getInt("theme", 2))

    init {
        viewModelScope.launch {
            for (write in writes) {
                try {
                    write()
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    errorsChannel.emit(Unit)
                }
            }
        }
        action { repository.initialize() }
    }

    private fun action(block: suspend () -> Unit) {
        writes.trySend(block)
    }

    fun openTask(id: String) = action { repository.details(id)?.let { open(it) } }

    fun retry() {
        refresh.value++
    }

    fun setTheme(value: Int) {
        theme.value = value
        preferences.edit().putInt("theme", value).apply()
    }

    fun create(task: TaskEntity, tagIds: Set<String>, done: () -> Unit) = action {
        repository.create(task, tagIds)
        done()
    }

    fun complete(task: TaskEntity, completed: Boolean = !task.isCompleted, done: () -> Unit = {}) =
        action {
            repository.complete(task.id, completed)
            done()
        }

    fun openNew(task: TaskEntity, tags: Set<String>) {
        flush()
        editorSession++
        selectedId.value = task.id
        draft.value = EditorDraft(task, tags)
        isNewDraft.value = true
        editScope.value = null
        scopeRequest.value = false
        editVersion = 0L
        savedVersion = 0L
        if (TaskRules.validTitle(task.title)) edit(draft.value!!)
    }

    fun open(detail: TaskWithDetails) {
        isNewDraft.value = false
        flush()
        editorSession++
        selectedId.value = detail.task.id
        draft.value =
            EditorDraft(detail.task, detail.tags.map { it.id }.toSet(), detail.series?.rule)
        editScope.value = null
        scopeRequest.value = false
        editVersion = 0L
        savedVersion = 0L
    }

    fun edit(value: EditorDraft) {
        draft.value = value
        val session = editorSession
        val version = ++editVersion
        saveJob?.cancel()
        if (!TaskRules.validTitle(value.task.title)) return
        if (value.task.seriesId != null && editScope.value == null) {
            scopeRequest.value = true
            return
        }
        val chosen = editScope.value ?: RecurrenceScope.THIS_AND_FUTURE
        saveJob =
            viewModelScope.launch {
                delay(500)
                action { persist(value, session, version, chosen) }
            }
    }

    fun flush() {
        saveJob?.cancel()
        if (editVersion == savedVersion) return
        val value = draft.value ?: return
        if (!TaskRules.validTitle(value.task.title)) return
        if (value.task.seriesId != null && editScope.value == null) {
            scopeRequest.value = true
            return
        }
        val session = editorSession
        val version = editVersion
        val chosen = editScope.value ?: RecurrenceScope.THIS_AND_FUTURE
        action { persist(value, session, version, chosen) }
    }

    fun close() {
        if (draft.value?.let { !TaskRules.validTitle(it.task.title) } == true) {
            if (isNewDraft.value) {
                saveJob?.cancel()
                draft.value = null
                selectedId.value = null
                isNewDraft.value = false
            }
            return
        }
        if (
            draft.value?.task?.seriesId != null &&
                editVersion != savedVersion &&
                editScope.value == null
        ) {
            scopeRequest.value = true
            return
        }
        flush()
        draft.value = null
        selectedId.value = null
    }

    fun deleteTask(id: String, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) {
        saveJob?.cancel()
        draft.value = null
        selectedId.value = null
        action { repository.delete(id, scope) }
    }

    fun duplicate(id: String) {
        flush()
        action { repository.duplicate(id) }
    }

    fun saveList(value: ListEntity, done: () -> Unit) = action {
        repository.saveList(value)
        done()
    }

    fun reorderTask(id: String, target: String) = action { repository.reorderTask(id, target) }

    fun reorderList(id: String, target: String) = action { repository.reorderList(id, target) }

    fun reorderSubtask(id: String, target: String, taskId: String) = scoped {
        action {
            repository.reorderSubtask(
                id,
                target,
                taskId,
                editScope.value ?: RecurrenceScope.ONLY_THIS,
            )
        }
    }

    fun moveTask(id: String, list: String, scope: RecurrenceScope) = action {
        repository.moveTask(id, list, scope)
    }

    fun postponeTask(id: String, day: Long, minute: Int?, scope: RecurrenceScope) = action {
        repository.postponeTask(id, day, minute, scope)
    }

    fun setListIcon(id: String, icon: String) = action { repository.setListIcon(id, icon) }

    fun deleteList(id: String) = action { repository.deleteList(id) }

    fun saveTag(name: String, color: Int, id: String?, done: () -> Unit) = action {
        repository.saveTag(name, color, id)
        done()
    }

    fun createAndAttachTag(name: String, color: Int, done: () -> Unit) = action {
        val id = repository.saveTag(name, color)
        draft.value?.let { edit(it.copy(tags = it.tags + id)) }
        done()
    }

    fun deleteTag(id: String) = action { repository.deleteTag(id) }

    fun attachImage(uri: android.net.Uri) = scoped {
        flush()
        val id = draft.value?.task?.id ?: return@scoped
        val scope = editScope.value ?: RecurrenceScope.ONLY_THIS
        action {
            val store = ImageStore(getApplication())
            val file = store.import(uri)
            try {
                repository.addImage(TaskImage(taskId = id, fileName = file), scope)
            } catch (e: Exception) {
                store.file(file).delete()
                throw e
            }
        }
    }

    fun removeImage(image: TaskImage) = scoped {
        val scope = editScope.value ?: RecurrenceScope.ONLY_THIS
        action {
            repository.removeImage(image, scope)
            if (!repository.imageReferenced(image.fileName))
                ImageStore(getApplication()).file(image.fileName).delete()
        }
    }

    fun saveSubtask(value: SubtaskEntity) = scoped {
        flush()
        action { repository.saveSubtask(value, editScope.value ?: RecurrenceScope.ONLY_THIS) }
    }

    fun deleteSubtask(id: String) = scoped {
        val taskId = draft.value?.task?.id
        action {
            repository.deleteSubtask(id, taskId, editScope.value ?: RecurrenceScope.ONLY_THIS)
        }
    }

    fun moveSubtask(value: SubtaskEntity, direction: Int) = scoped {
        action {
            repository.moveSubtask(
                value.id,
                value.taskId,
                direction,
                editScope.value ?: RecurrenceScope.ONLY_THIS,
            )
        }
    }
}
