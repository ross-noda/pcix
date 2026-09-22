package com.example.pix.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.TaskRules
import com.example.pix.domain.TaskTiming
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
private fun plainFieldColors() =
    TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
    )

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    model: TasksViewModel,
    lists: List<ListWithCount>,
    tags: List<TagWithCount>,
    listId: String,
    day: Long?,
    minute: Int? = null,
    quadrant: Int? = null,
    dismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf("") }
    var selectedList by rememberSaveable { mutableStateOf(listId) }
    var selectedDay by rememberSaveable { mutableStateOf(day) }
    var priority by rememberSaveable { mutableIntStateOf(0) }
    var selectedTags by remember { mutableStateOf(emptySet<String>()) }
    var selector by remember { mutableIntStateOf(-1) }
    var discard by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    fun submit(expand: Boolean = false) {
        if ((expand || TaskRules.validTitle(title)) && !submitting) {
            submitting = true
            val newTask =
                TaskEntity(
                    title = title,
                    listId = selectedList,
                    dueDay = selectedDay,
                    minuteOfDay = if (selectedDay != null) minute else null,
                    matrixUrgent = quadrant?.let { it == 0 || it == 2 },
                    matrixImportant = quadrant?.let { it == 0 || it == 1 },
                    priority = priority,
                )
            if (expand) {
                dismiss()
                model.openNew(newTask, selectedTags)
            } else model.create(newTask, selectedTags, dismiss)
        }
    }
    LaunchedEffect(Unit) { model.errors.collect { submitting = false } }
    ModalBottomSheet(
        onDismissRequest = { if (title.isNotBlank()) discard = true else dismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = null,
    ) {
        Column(
            Modifier.fillMaxWidth()
                .imePadding()
                .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = { submit(true) }) {
                    PixIcon(PixSymbol.EXPAND, stringResource(R.string.expand_editor))
                }
            }
            if (quadrant != null)
                Text(
                    stringResource(quadrantLabels[quadrant]),
                    Modifier.padding(horizontal = 16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            if (minute != null)
                Text(
                    "%02d:%02d".format(java.util.Locale.ROOT, minute / 60, minute % 60),
                    Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            TextField(
                title,
                { if (it.length <= 200) title = it },
                placeholder = { Text(stringResource(R.string.quick_prompt)) },
                colors = plainFieldColors(),
                modifier =
                    Modifier.fillMaxWidth()
                        .heightIn(min = 86.dp)
                        .testTag("quick-add-title")
                        .focusRequester(focus),
                textStyle = MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
            )
            if (selectedDay != null)
                TextButton(onClick = { selectedDay = null }) {
                    Text(
                        LocalDate.ofEpochDay(selectedDay!!)
                            .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                    )
                    Spacer(Modifier.width(8.dp))
                    PixIcon(PixSymbol.CLOSE, modifier = Modifier.size(14.dp))
                }
            when (selector) {
                1 ->
                    ChoiceRow(lists.map { it.list.id to it.list.name }, selectedList) {
                        selectedList = it
                    }
                2 -> PriorityPicker(priority) { priority = it }
                3 -> TagPicker(tags, selectedTags) { selectedTags = it }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = {
                        val initial = selectedDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                        DatePickerDialog(
                                context,
                                { _, y, m, d ->
                                    selectedDay = LocalDate.of(y, m + 1, d).toEpochDay()
                                },
                                initial.year,
                                initial.monthValue - 1,
                                initial.dayOfMonth,
                            )
                            .show()
                    }
                ) {
                    PixIcon(PixSymbol.CALENDAR, stringResource(R.string.date))
                }
                IconButton(onClick = { selector = if (selector == 2) -1 else 2 }) {
                    PixIcon(
                        PixSymbol.FLAG,
                        stringResource(R.string.priority),
                        tint = priorityColor(priority),
                    )
                }
                IconButton(onClick = { selector = if (selector == 3) -1 else 3 }) {
                    PixIcon(PixSymbol.TAG, stringResource(R.string.tags))
                }
                IconButton(onClick = { selector = if (selector == 1) -1 else 1 }) {
                    PixIcon(PixSymbol.INBOX, stringResource(R.string.list))
                }
                Spacer(Modifier.weight(1f))
                FilledIconButton(
                    onClick = { submit() },
                    enabled = TaskRules.validTitle(title) && !submitting,
                    colors =
                        IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                ) {
                    PixIcon(
                        PixSymbol.SEND,
                        stringResource(R.string.send),
                        tint =
                            if (title.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            focus.requestFocus()
            keyboard?.show()
        }
    }
    if (discard)
        AlertDialog(
            onDismissRequest = { discard = false },
            title = { Text(stringResource(R.string.discard_title)) },
            text = { Text(stringResource(R.string.discard_body)) },
            confirmButton = {
                TextButton(onClick = dismiss) { Text(stringResource(R.string.discard)) }
            },
            dismissButton = {
                TextButton(onClick = { discard = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditor(
    model: TasksViewModel,
    draft: EditorDraft,
    lists: List<ListWithCount>,
    tags: List<TagWithCount>,
) {
    val shareContext = LocalContext.current
    val imagePicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) model.attachImage(uri)
        }
    val isNew by model.isNewDraft.collectAsStateWithLifecycle()
    val titleFocusRequester = remember(draft.task.id) { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(isNew, draft.task.id) {
        if (isNew && draft.task.title.isBlank()) {
            kotlinx.coroutines.delay(180)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    var discardNew by remember { mutableStateOf(false) }
    val selected by model.selected.collectAsStateWithLifecycle()
    val live = selected?.takeIf { it.task.id == draft.task.id }
    val task = draft.task
    val scopeRequest by model.scopeRequest.collectAsStateWithLifecycle()
    val editScope by model.editScope.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<String?>(null) }
    var more by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var newSubtask by rememberSaveable(task.id) { mutableStateOf("") }
    fun edit(value: TaskEntity) =
        model.edit(
            draft.copy(
                task = value,
                recurrenceRule = if (value.dueDay == null) null else draft.recurrenceRule,
            )
        )
    fun closeEditor() {
        if (isNew && task.title.isBlank() && task.notes.isNotBlank()) discardNew = true
        else model.close()
    }
    if (discardNew)
        ConfirmDialog(
            stringResource(R.string.discard_title),
            stringResource(R.string.discard_body),
            { discardNew = false },
        ) {
            discardNew = false
            model.close()
        }
    Dialog(
        onDismissRequest = ::closeEditor,
        properties =
            DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(
                Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()
            ) {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = ::closeEditor) {
                        PixIcon(PixSymbol.BACK, stringResource(R.string.back))
                    }
                    TextButton(onClick = { panel = "list" }, modifier = Modifier.weight(1f)) {
                        Text(lists.find { it.list.id == task.listId }?.list?.icon ?: "📋")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            lists.find { it.list.id == task.listId }?.list?.name
                                ?: stringResource(R.string.inbox),
                            maxLines = 1,
                        )
                    }
                    IconButton(onClick = { panel = "priority" }) {
                        PixIcon(
                            PixSymbol.FLAG,
                            stringResource(R.string.priority),
                            tint = priorityColor(task.priority),
                        )
                    }
                    Box {
                        IconButton(enabled = !isNew, onClick = { more = true }) {
                            PixIcon(PixSymbol.MORE, stringResource(R.string.more_actions))
                        }
                        DropdownMenu(more, { more = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.share_task)) },
                                onClick = {
                                    more = false
                                    live?.let { detail ->
                                        runCatching {
                                                shareTask(
                                                    shareContext,
                                                    detail.copy(
                                                        task = task,
                                                        list =
                                                            lists
                                                                .find { it.list.id == task.listId }
                                                                ?.list ?: detail.list,
                                                        tags =
                                                            tags
                                                                .filter { it.tag.id in draft.tags }
                                                                .map { it.tag },
                                                    ),
                                                )
                                            }
                                            .onFailure {
                                                android.widget.Toast.makeText(
                                                        shareContext,
                                                        R.string.share_failed,
                                                        android.widget.Toast.LENGTH_LONG,
                                                    )
                                                    .show()
                                            }
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.duplicate)) },
                                leadingIcon = { PixIcon(PixSymbol.COPY) },
                                onClick = {
                                    more = false
                                    model.duplicate(task.id)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.delete)) },
                                leadingIcon = { PixIcon(PixSymbol.DELETE) },
                                onClick = {
                                    more = false
                                    deleting = true
                                },
                            )
                        }
                    }
                }
                Column(
                    Modifier.weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp)
                ) {
                    TextButton(
                        onClick = { panel = "date" },
                        modifier = Modifier.testTag("edit-date-time"),
                    ) {
                        PixIcon(PixSymbol.CALENDAR)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            task.dueDay?.let {
                                LocalDate.ofEpochDay(it)
                                    .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                            } ?: stringResource(R.string.date_reminder)
                        )
                        task.minuteOfDay?.let {
                            Text(
                                " · " +
                                    LocalTime.of(it / 60, it % 60)
                                        .format(DateTimeFormatter.ofPattern("HH:mm"))
                            )
                        }
                    }
                    if (task.durationMinutes != null)
                        Text(
                            durationEndLabel(task),
                            Modifier.padding(start = 16.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    TextField(
                        task.title,
                        { if (it.length <= 200) edit(task.copy(title = it)) },
                        placeholder = { Text(stringResource(R.string.title)) },
                        textStyle = MaterialTheme.typography.headlineSmall,
                        colors = plainFieldColors(),
                        modifier = Modifier.fillMaxWidth().focusRequester(titleFocusRequester).testTag("detail-title"),
                        isError = !TaskRules.validTitle(task.title),
                        supportingText = {
                            if (!TaskRules.validTitle(task.title))
                                Text(stringResource(R.string.invalid_title))
                        },
                    )
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    ) {
                        Column {
                            EditorToolRow(
                                PixSymbol.REPEAT,
                                stringResource(R.string.recurrence),
                                recurrenceLabel(draft.recurrenceRule),
                            ) {
                                panel = "repeat"
                            }
                            EditorToolRow(
                                PixSymbol.IMAGE,
                                stringResource(R.string.add_image),
                                enabled = TaskRules.validTitle(task.title),
                            ) {
                                imagePicker.launch(arrayOf("image/*"))
                            }
                            EditorToolRow(PixSymbol.LISTS, stringResource(R.string.matrix)) {
                                panel = "matrix"
                            }
                        }
                    }
                    TextField(
                        task.notes,
                        { if (it.length <= 2000) edit(task.copy(notes = it)) },
                        placeholder = { Text(stringResource(R.string.description)) },
                        colors = plainFieldColors(),
                        modifier = Modifier.fillMaxWidth().testTag("detail-notes"),
                        minLines = 2,
                        textStyle = MaterialTheme.typography.bodyMedium,
                    )
                    TaskImages(live?.images.orEmpty(), model)
                    if (task.seriesId != null && editScope != null)
                        Text(
                            stringResource(
                                if (editScope == RecurrenceScope.ONLY_THIS) R.string.only_occurrence
                                else R.string.this_and_future
                            ),
                            Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    if (draft.tags.isNotEmpty())
                        Row(
                            Modifier.horizontalScroll(rememberScrollState())
                                .padding(horizontal = 12.dp)
                        ) {
                            tags
                                .filter { it.tag.id in draft.tags }
                                .forEach { tag ->
                                    InputChip(
                                        selected = true,
                                        onClick = { panel = "tags" },
                                        label = { Text("#${tag.tag.name}") },
                                    )
                                }
                        }
                    val subtaskOrder = remember { ReorderState() }
                    val orderedSubtasks =
                        live
                            ?.subtasks
                            ?.sortedWith(
                                compareBy<SubtaskEntity> { it.isCompleted }
                                    .thenBy { it.sortOrder }
                                    .thenBy { it.id }
                            )
                            .orEmpty()
                    orderedSubtasks.forEach { subtask ->
                        key(subtask.id) {
                            ReorderItem(
                                subtask.id,
                                orderedSubtasks
                                    .filter { it.isCompleted == subtask.isCompleted }
                                    .map { it.id },
                                subtaskOrder,
                                { source, target -> model.reorderSubtask(source, target, task.id) },
                            ) {
                                SubtaskRow(subtask, model)
                            }
                        }
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        PixIcon(PixSymbol.PLUS, modifier = Modifier.padding(start = 12.dp))
                        TextField(
                            newSubtask,
                            { if (it.length <= 200) newSubtask = it },
                            placeholder = { Text(stringResource(R.string.add_subtask)) },
                            colors = plainFieldColors(),
                            singleLine = false,
                            enabled = TaskRules.validTitle(task.title),
                            modifier = Modifier.weight(1f).testTag("new-subtask"),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions =
                                KeyboardActions(
                                    onDone = {
                                        if (newSubtask.isNotBlank()) {
                                            model.saveSubtask(
                                                SubtaskEntity(taskId = task.id, title = newSubtask)
                                            )
                                            newSubtask = ""
                                        }
                                    }
                                ),
                        )
                        if (newSubtask.isNotBlank())
                            IconButton(
                                onClick = {
                                    model.saveSubtask(
                                        SubtaskEntity(taskId = task.id, title = newSubtask)
                                    )
                                    newSubtask = ""
                                }
                            ) {
                                PixIcon(PixSymbol.SEND, stringResource(R.string.add_subtask))
                            }
                    }
                    if (task.minuteOfDay != null && task.dueDay != null)
                        Box(Modifier.padding(16.dp)) { ReminderControls() }
                    Spacer(Modifier.height(24.dp))
                }
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { panel = "tags" }) {
                        PixIcon(PixSymbol.TAG, stringResource(R.string.tags))
                    }
                    IconButton(onClick = { panel = "priority" }) {
                        PixIcon(PixSymbol.FLAG, stringResource(R.string.priority))
                    }
                    IconButton(onClick = { panel = "date" }) {
                        PixIcon(PixSymbol.CLOCK, stringResource(R.string.date_reminder))
                    }
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        enabled = TaskRules.validTitle(task.title),
                        onClick = ::closeEditor,
                        modifier = Modifier.testTag("editor-done"),
                    ) {
                        Text(stringResource(R.string.done))
                    }
                }
            }
        }
    }
    if (panel == "repeat")
        RecurrenceDialog(draft.recurrenceRule, task.dueDay, { panel = null }) { rule ->
            panel = null
            if (task.seriesId != null && rule != draft.recurrenceRule && rule != null)
                model.editScope.value = null
            model.edit(
                draft.copy(
                    task =
                        if (rule != null && task.dueDay == null)
                            task.copy(dueDay = LocalDate.now().toEpochDay())
                        else task,
                    recurrenceRule = rule,
                )
            )
        }
    if (panel != null && panel != "repeat")
        ModalBottomSheet(onDismissRequest = { panel = null }) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(20.dp).imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (panel) {
                    "matrix" -> MatrixTaskFields(task) { edit(it) }
                    "date" -> DateTimeFields(task) { edit(it) }
                    "list" -> {
                        Text(
                            stringResource(R.string.list),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        ChoiceRow(lists.map { it.list.id to it.list.name }, task.listId) {
                            edit(task.copy(listId = it))
                            panel = null
                        }
                    }
                    "priority" -> {
                        Text(
                            stringResource(R.string.priority),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        PriorityPicker(task.priority) {
                            edit(task.copy(priority = it))
                            panel = null
                        }
                    }
                    "tags" -> {
                        Text(
                            stringResource(R.string.tags),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        TagPicker(tags, draft.tags) { model.edit(draft.copy(tags = it)) }
                        var name by remember { mutableStateOf("") }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                name,
                                { if (it.length <= 50) name = it },
                                label = { Text(stringResource(R.string.new_tag)) },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                enabled = name.isNotBlank(),
                                onClick = {
                                    model.createAndAttachTag(name, tags.size.mod(12)) { name = "" }
                                },
                            ) {
                                PixIcon(PixSymbol.PLUS, stringResource(R.string.new_tag))
                            }
                        }
                    }
                }
                TextButton(
                    onClick = { panel = null },
                    modifier = Modifier.align(Alignment.End).testTag("editor-panel-done"),
                ) {
                    Text(stringResource(R.string.done))
                }
            }
        }
    if (scopeRequest)
        RecurrenceScopeDialog(
            allowOnly = draft.recurrenceRule == null || draft.recurrenceRule == live?.series?.rule,
            dismiss = model::cancelScope,
            choose = model::chooseScope,
        )
    if (deleting) {
        if (task.seriesId != null)
            RecurrenceScopeDialog(delete = true, dismiss = { deleting = false }) {
                model.deleteTask(task.id, it)
                deleting = false
            }
        else
            ConfirmDialog(
                stringResource(R.string.delete_task_question),
                stringResource(R.string.delete_task_body),
                { deleting = false },
            ) {
                model.deleteTask(task.id)
            }
    }
}

@Composable
private fun SubtaskRow(subtask: SubtaskEntity, model: TasksViewModel) {
    var title by remember(subtask.title) { mutableStateOf(subtask.title) }
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.alpha(if (subtask.isCompleted) .55f else 1f)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(subtask.isCompleted, { model.saveSubtask(subtask.copy(isCompleted = it)) })
            TextField(
                title,
                { if (it.length <= 200) title = it },
                colors = plainFieldColors(),
                singleLine = false,
                textStyle =
                    MaterialTheme.typography.bodyMedium.copy(
                        textDecoration =
                            if (subtask.isCompleted) TextDecoration.LineThrough
                            else TextDecoration.None
                    ),
                modifier = Modifier.weight(1f).testTag("subtask-${subtask.id}"),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions =
                    KeyboardActions(
                        onDone = {
                            if (title.isNotBlank()) model.saveSubtask(subtask.copy(title = title))
                        }
                    ),
            )
            if (title != subtask.title && title.isNotBlank())
                IconButton(onClick = { model.saveSubtask(subtask.copy(title = title)) }) {
                    PixIcon(PixSymbol.CHECK, stringResource(R.string.save))
                }
            Box {
                IconButton(onClick = { menu = true }) {
                    PixIcon(PixSymbol.MORE, stringResource(R.string.more_actions))
                }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_up)) },
                        onClick = {
                            menu = false
                            model.moveSubtask(subtask, -1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.move_down)) },
                        onClick = {
                            menu = false
                            model.moveSubtask(subtask, 1)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.delete)) },
                        onClick = {
                            menu = false
                            model.deleteSubtask(subtask.id)
                        },
                    )
                }
            }
        }
        HorizontalDivider(
            Modifier.padding(start = 48.dp),
            color = MaterialTheme.colorScheme.outlineVariant,
        )
    }
}

@Composable
private fun DateTimeFields(task: TaskEntity, edit: (TaskEntity) -> Unit) {
    val context = LocalContext.current
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(
            onClick = {
                val initial = task.dueDay?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
                DatePickerDialog(
                        context,
                        { _, y, m, d ->
                            edit(task.copy(dueDay = LocalDate.of(y, m + 1, d).toEpochDay()))
                        },
                        initial.year,
                        initial.monthValue - 1,
                        initial.dayOfMonth,
                    )
                    .show()
            }
        ) {
            Text(
                task.dueDay?.let {
                    LocalDate.ofEpochDay(it)
                        .format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
                } ?: stringResource(R.string.date)
            )
        }
        if (task.dueDay != null)
            TextButton(
                onClick = {
                    edit(task.copy(dueDay = null, minuteOfDay = null, durationMinutes = null))
                }
            ) {
                Text(stringResource(R.string.no_date))
            }
    }
    if (task.dueDay != null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.all_day), Modifier.weight(1f))
            Switch(
                checked = task.minuteOfDay == null,
                onCheckedChange = { edit(TaskTiming.allDay(task, it)) },
            )
        }
        task.minuteOfDay?.let { minute ->
            OutlinedButton(
                onClick = {
                    TimePickerDialog(
                            context,
                            { _, h, m -> edit(task.copy(minuteOfDay = h * 60 + m)) },
                            minute / 60,
                            minute % 60,
                            android.text.format.DateFormat.is24HourFormat(context),
                        )
                        .show()
                }
            ) {
                Text(
                    stringResource(R.string.time) +
                        ": " +
                        LocalTime.of(minute / 60, minute % 60)
                            .format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                )
            }
        }
    }
    DurationFields(task, edit)
}

@Composable
fun ChoiceRow(choices: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        choices.forEach { (id, label) ->
            FilterChip(
                selected = selected == id,
                onClick = { onSelect(id) },
                label = { Text(label) },
            )
        }
    }
}

@Composable
fun PriorityPicker(priority: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        priorityValues.forEachIndexed { index, value ->
            FilterChip(
                selected = priority == value,
                onClick = { onSelect(value) },
                label = { Text(stringResource(priorityLabels[index])) },
                leadingIcon = {
                    PixIcon(
                        PixSymbol.FLAG,
                        modifier = Modifier.size(16.dp),
                        tint = priorityColor(value),
                    )
                },
                colors =
                    FilterChipDefaults.filterChipColors(
                        labelColor = priorityColor(value),
                        selectedLabelColor = priorityColor(value),
                        selectedContainerColor = priorityColor(value).copy(alpha = .16f),
                    ),
            )
        }
    }
}

@Composable
fun TagPicker(tags: List<TagWithCount>, selected: Set<String>, onSelect: (Set<String>) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { row ->
            FilterChip(
                selected = row.tag.id in selected,
                onClick = {
                    onSelect(
                        if (row.tag.id in selected) selected - row.tag.id else selected + row.tag.id
                    )
                },
                label = { Text("#${row.tag.name}") },
            )
        }
    }
}
