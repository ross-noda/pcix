package com.example.pix.ui

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

internal val matrixHeadingLabels = listOf(R.string.matrix_heading_do, R.string.matrix_heading_plan, R.string.matrix_heading_delegate, R.string.matrix_heading_drop)

@Composable
internal fun matrixTitle(config: MatrixConfig, id: Int): String =
    MatrixRules.card(config, id).title.ifBlank { stringResource(matrixHeadingLabels[id]) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MatrixCardEditor(config: MatrixConfig, lists: List<ListWithCount>, tags: List<TagWithCount>,
    save: (MatrixConfig) -> Unit, dismiss: () -> Unit) {
    var working by remember { mutableStateOf(config) }
    var editing by remember { mutableStateOf<Int?>(null) }
    var draft by remember { mutableStateOf(MatrixCard()) }
    val order = remember { ReorderState() }
    fun commit(value: MatrixConfig) { working = value; save(value) }
    fun back() { if (editing != null) editing = null else dismiss() }
    Dialog(onDismissRequest = { back() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler { back() }
        Surface(Modifier.fillMaxSize()) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(R.string.matrix_edit_cards)) },
                    navigationIcon = { IconButton(onClick = { back() }) { PixIcon(PixSymbol.BACK, stringResource(R.string.back)) } },
                    actions = {
                        if (editing != null) TextButton(onClick = {
                            val id = editing!!
                            val cards = List(4) { MatrixRules.card(working, it) }.toMutableList()
                            cards[id] = draft.copy(title = draft.title.trim())
                            commit(working.copy(cards = cards)); editing = null
                        }, enabled = draft.date != MatrixDate.RANGE || !draft.custom || MatrixRules.dateRange(draft, LocalDate.now()) != null,
                            modifier = Modifier.testTag("matrix-card-save")) { Text(stringResource(R.string.save)) }
                    })
            }) { padding ->
                if (editing == null) {
                    LazyColumn(Modifier.padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item { Text(stringResource(R.string.matrix_reorder_hint), style = MaterialTheme.typography.bodySmall) }
                        items(MatrixRules.orderedIds(working), key = { it }) { id ->
                            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
                                ReorderItem(id.toString(), MatrixRules.orderedIds(working).map { it.toString() }, order, { from, to ->
                                    val ids = MatrixRules.orderedIds(working).toMutableList()
                                    val destination = ids.indexOf(to.toInt())
                                    ids.remove(from.toInt()); ids.add(destination, from.toInt())
                                    commit(working.copy(cardOrder = ids))
                                }) {
                                    val card = MatrixRules.card(working, id)
                                    ListItem(headlineContent = { Text(matrixTitle(working, id), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                        leadingContent = { MatrixBadge(id) },
                                        supportingContent = { Text(stringResource(if (card.custom) R.string.matrix_custom_filters else R.string.matrix_automatic_card)) },
                                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                                        modifier = Modifier.clickable { draft = card; editing = id }.testTag("matrix-edit-card-$id"))
                                }
                            }
                        }
                    }
                } else Column(Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MatrixBadge(editing!!)
                        Spacer(Modifier.width(12.dp))
                        OutlinedTextField(draft.title, { draft = draft.copy(title = it) },
                            Modifier.weight(1f).testTag("matrix-card-title"), label = { Text(stringResource(R.string.matrix_card_title)) },
                            placeholder = { Text(stringResource(matrixHeadingLabels[editing!!])) }, singleLine = true)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.matrix_custom_filters), Modifier.weight(1f))
                        Switch(draft.custom, { draft = draft.copy(custom = it) }, Modifier.testTag("matrix-card-custom"))
                    }
                    Text(stringResource(if (draft.custom) R.string.matrix_custom_hint else R.string.matrix_automatic_card), style = MaterialTheme.typography.bodySmall)
                    MatrixCardFilters(draft, lists, tags) { draft = it.copy(custom = true) }
                    TextButton(onClick = { draft = MatrixCard() }, Modifier.align(Alignment.CenterHorizontally)) { Text(stringResource(R.string.reset)) }
                }
            }
        }
    }
}

@Composable
private fun MatrixBadge(id: Int) {
    Box(Modifier.size(24.dp).background(quadrantColors[id], CircleShape), contentAlignment = Alignment.Center) {
        Text(listOf("I", "II", "III", "IV")[id], color = androidx.compose.ui.graphics.Color.Black, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun MatrixCardFilters(card: MatrixCard, lists: List<ListWithCount>, tags: List<TagWithCount>, change: (MatrixCard) -> Unit) {
    var panel by remember { mutableStateOf<String?>(null) }
    val all = stringResource(R.string.all)
    val removed = stringResource(R.string.matrix_missing_filter)
    val priorities = listOf(0 to R.string.none, 1 to R.string.low, 3 to R.string.medium, 5 to R.string.high).map { it.first.toString() to stringResource(it.second) }
    val dates = listOf(MatrixDate.ALL to R.string.all, MatrixDate.TODAY to R.string.today, MatrixDate.TOMORROW to R.string.tomorrow,
        MatrixDate.THIS_WEEK to R.string.matrix_this_week, MatrixDate.NEXT_WEEK to R.string.matrix_next_week,
        MatrixDate.THIS_MONTH to R.string.matrix_this_month, MatrixDate.NEXT_MONTH to R.string.matrix_next_month, MatrixDate.RANGE to R.string.matrix_day_range)
    val listChoices = lists.map { it.list.id to (it.list.icon + " " + it.list.name) }
    val tagChoices = tags.map { it.tag.id to it.tag.name }
    fun summary(ids: Set<String>, choices: List<Pair<String, String>>) = if (ids.isEmpty()) all else ids.joinToString(", ") { id -> choices.find { it.first == id }?.second ?: removed }
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column {
            @Composable fun row(label: Int, value: String, key: String) {
                ListItem(headlineContent = { Text(stringResource(label)) }, supportingContent = { Text(value, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.clickable { panel = key }.testTag("matrix-filter-$key"))
            }
            row(R.string.lists, summary(card.listIds, listChoices), "lists")
            row(R.string.tags, summary(card.tagIds, tagChoices), "tags")
            row(R.string.date, stringResource(dates.first { it.first == card.date }.second), "date")
            row(R.string.priority, summary(card.priorities.map { it.toString() }.toSet(), priorities), "priority")
        }
    }
    if (card.date == MatrixDate.RANGE) {
        val context = LocalContext.current
        val format = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        fun pick(from: Boolean) {
            val initial = (if (from) card.fromDay else card.toDay)?.let(LocalDate::ofEpochDay) ?: LocalDate.now()
            DatePickerDialog(context, { _, year, month, day ->
                val value = LocalDate.of(year, month + 1, day).toEpochDay()
                change(if (from) card.copy(fromDay = value) else card.copy(toDay = value))
            }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { pick(true) }, Modifier.weight(1f)) { Text(stringResource(R.string.matrix_from) + " " + (card.fromDay?.let { LocalDate.ofEpochDay(it).format(format) } ?: "—")) }
            OutlinedButton(onClick = { pick(false) }, Modifier.weight(1f)) { Text(stringResource(R.string.matrix_to) + " " + (card.toDay?.let { LocalDate.ofEpochDay(it).format(format) } ?: "—")) }
        }
        if (MatrixRules.dateRange(card, LocalDate.now()) == null) Text(stringResource(R.string.matrix_invalid_range), color = MaterialTheme.colorScheme.error)
    }
    val selectedPanel = panel
    if (selectedPanel == "date") AlertDialog(onDismissRequest = { panel = null }, title = { Text(stringResource(R.string.date)) },
        text = { Column(Modifier.verticalScroll(rememberScrollState())) { dates.forEach { (value, label) ->
            Row(Modifier.fillMaxWidth().clickable {
                change(card.copy(date = value)); panel = null
            }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(card.date == value, onClick = { change(card.copy(date = value)); panel = null })
                Text(stringResource(label))
            }
        } } }, confirmButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.close)) } })
    else if (selectedPanel != null) {
        val choices = when (selectedPanel) { "lists" -> listChoices; "tags" -> tagChoices; else -> priorities }
        val selected = when (selectedPanel) { "lists" -> card.listIds; "tags" -> card.tagIds; else -> card.priorities.map { it.toString() }.toSet() }
        fun update(ids: Set<String>) { change(when (selectedPanel) {
            "lists" -> card.copy(listIds = ids); "tags" -> card.copy(tagIds = ids); else -> card.copy(priorities = ids.map { it.toInt() }.toSet())
        }) }
        AlertDialog(onDismissRequest = { panel = null }, title = { Text(stringResource(when(selectedPanel) { "lists" -> R.string.lists; "tags" -> R.string.tags; else -> R.string.priority })) },
            text = { LazyColumn { item {
                Row(Modifier.fillMaxWidth().clickable { update(emptySet()) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(selected.isEmpty(), { update(emptySet()) }); Text(all)
                }
            }; items(choices, key = { it.first }) { (id, label) ->
                Row(Modifier.fillMaxWidth().clickable { update(if (id in selected) selected - id else selected + id) }, verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(id in selected, { update(if (it) selected + id else selected - id) }); Text(label)
                }
            } } }, confirmButton = { TextButton(onClick = { panel = null }) { Text(stringResource(R.string.close)) } })
    }
}
