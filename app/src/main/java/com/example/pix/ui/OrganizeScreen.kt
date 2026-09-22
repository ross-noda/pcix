package com.example.pix.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.*
import com.example.pix.ui.theme.ListColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizeScreen(
    model: TasksViewModel,
    lists: List<ListWithCount>,
    tags: List<TagWithCount>,
    open: (String?, String?) -> Unit,
) {
    val reorderState = remember { ReorderState() }
    var iconList by remember { mutableStateOf<ListEntity?>(null) }
    var tagTab by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var listToEdit by remember { mutableStateOf<ListWithCount?>(null) }
    var tagToEdit by remember { mutableStateOf<TagWithCount?>(null) }
    var deleting by remember { mutableStateOf(false) }
    Column {
        SecondaryTabRow(selectedTabIndex = if (tagTab) 1 else 0) {
            Tab(
                selected = !tagTab,
                onClick = { tagTab = false },
                text = { Text(stringResource(R.string.lists)) },
            )
            Tab(
                selected = tagTab,
                onClick = { tagTab = true },
                text = { Text(stringResource(R.string.tags)) },
            )
        }
        TextButton(
            onClick = {
                listToEdit = null
                tagToEdit = null
                editing = true
            },
            modifier = Modifier.padding(8.dp),
        ) {
            Text(stringResource(if (tagTab) R.string.new_tag else R.string.new_list))
        }
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (!tagTab)
                items(lists, key = { it.list.id }) { row ->
                    val listRow: @Composable () -> Unit = {
                        ListItem(
                            headlineContent = { Text(row.list.name) },
                            supportingContent = {
                                Text(stringResource(R.string.active_count, row.activeCount))
                            },
                            leadingContent = {
                                IconButton(onClick = { iconList = row.list }) {
                                    Text(row.list.icon, style = MaterialTheme.typography.titleLarge)
                                }
                            },
                            trailingContent = {
                                if (row.list.id != INBOX_ID)
                                    TextButton(
                                        onClick = {
                                            listToEdit = row
                                            tagToEdit = null
                                            editing = true
                                        }
                                    ) {
                                        Text(stringResource(R.string.edit))
                                    }
                            },
                            modifier =
                                Modifier.testTag("organize-list-${row.list.id}")
                                    .clip(MaterialTheme.shapes.large)
                                    .clickable { open(row.list.id, null) },
                        )
                    }
                    if (row.list.id == INBOX_ID) listRow()
                    else
                        ReorderItem(
                            row.list.id,
                            lists.filter { it.list.id != INBOX_ID }.map { it.list.id },
                            reorderState,
                            model::reorderList,
                            listRow,
                        )
                }
            else
                items(tags, key = { it.tag.id }) { row ->
                    ListItem(
                        headlineContent = { Text("#${row.tag.name}") },
                        supportingContent = {
                            Text(stringResource(R.string.active_count, row.activeCount))
                        },
                        leadingContent = {
                            Box(
                                Modifier.size(12.dp)
                                    .clip(CircleShape)
                                    .background(ListColors[row.tag.color.mod(12)])
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    tagToEdit = row
                                    listToEdit = null
                                    editing = true
                                }
                            ) {
                                Text(stringResource(R.string.edit))
                            }
                        },
                        modifier =
                            Modifier.clip(MaterialTheme.shapes.large).clickable {
                                open(null, row.tag.id)
                            },
                    )
                }
        }
    }
    iconList?.let { list ->
        ListIconPicker(list.icon, { iconList = null }) {
            model.setListIcon(list.id, it)
            iconList = null
        }
    }
    if (editing) {
        var name by remember {
            mutableStateOf(
                if (tagTab) tagToEdit?.tag?.name.orEmpty() else listToEdit?.list?.name.orEmpty()
            )
        }
        var color by remember {
            mutableIntStateOf(
                if (tagTab) tagToEdit?.tag?.color ?: tags.size.mod(12)
                else listToEdit?.list?.color ?: lists.size.mod(12)
            )
        }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(if (tagTab) R.string.tags else R.string.lists)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { if (it.length <= 50) name = it },
                        label = { Text(stringResource(R.string.name)) },
                        singleLine = true,
                    )
                    ColorPicker(color) { color = it }
                    if (listToEdit != null || tagToEdit != null)
                        TextButton(onClick = { deleting = true }) {
                            Text(
                                stringResource(R.string.delete),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank(),
                    onClick = {
                        if (tagTab)
                            model.saveTag(name, color, tagToEdit?.tag?.id) { editing = false }
                        else
                            model.saveList(
                                (listToEdit?.list ?: ListEntity(name = name)).copy(
                                    name = name,
                                    color = color,
                                )
                            ) {
                                editing = false
                            }
                    },
                ) {
                    Text(stringResource(R.string.save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (deleting)
        ConfirmDialog(
            stringResource(R.string.delete),
            if (tagTab) stringResource(R.string.delete_tag_body)
            else stringResource(R.string.delete_list_body, listToEdit?.activeCount ?: 0),
            { deleting = false },
            {
                if (tagTab) tagToEdit?.let { model.deleteTag(it.tag.id) }
                else listToEdit?.let { model.deleteList(it.list.id) }
                deleting = false
                editing = false
            },
        )
}

@Composable
fun ColorPicker(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        repeat(2) { row ->
            Row {
                repeat(6) { column ->
                    val index = row * 6 + column
                    val description = stringResource(R.string.choose_color, index + 1)
                    Box(
                        Modifier.size(44.dp)
                            .semantics {
                                contentDescription = description
                                this.selected = selected == index
                            }
                            .clickable { onSelect(index) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier.size(if (selected == index) 32.dp else 22.dp)
                                .clip(CircleShape)
                                .background(ListColors[index]),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (selected == index)
                                Text("✓", color = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            }
        }
    }
}
