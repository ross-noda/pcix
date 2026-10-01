package com.example.pix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentTaskPicker(model:TasksViewModel,id:String,dismiss:()->Unit,choose:(String)->Unit) {
    var query by remember { mutableStateOf("") }
    val candidates by remember(id,query) { model.parentCandidates(id,query) }.collectAsStateWithLifecycle(initialValue=emptyList())
    ModalBottomSheet(onDismissRequest=dismiss,sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().imePadding().padding(16.dp)) {
            Text(stringResource(R.string.choose_parent),style=MaterialTheme.typography.titleLarge)
            OutlinedTextField(query,{query=it},label={Text(stringResource(R.string.parent_search))},singleLine=true,
                modifier=Modifier.fillMaxWidth().testTag("parent-search"))
            if(candidates.isEmpty()) Text(stringResource(R.string.parent_empty),Modifier.padding(16.dp))
            LazyColumn(Modifier.heightIn(max=400.dp)) {
                items(candidates,key={it.id}) { task -> TextButton(onClick={choose(task.id)},modifier=Modifier.fillMaxWidth().testTag("parent-${task.id}")) {
                    Text(task.title,Modifier.fillMaxWidth())
                } }
            }
        }
    }
}

@Composable
fun ChildTaskRow(child:TaskEntity,parent:TaskEntity,lists:List<ListWithCount>,model:TasksViewModel) {
    var menu by remember { mutableStateOf(false) }
    Row {
        Box(Modifier.weight(1f).testTag("child-${child.id}")) {
            TaskRowContent(TaskWithDetails(child,null,lists.find { it.list.id==child.listId }?.list ?: ListEntity(id=child.listId,name=""),emptyList(),emptyList(),parent=parent),
                onOpen={model.openTask(child.id)},onComplete={model.complete(child)})
        }
        Box {
            IconButton(onClick={menu=true}) {PixIcon(PixSymbol.MORE,stringResource(R.string.more_actions))}
            DropdownMenu(menu,{menu=false}) {
                DropdownMenuItem(text={Text(stringResource(R.string.move_up))},onClick={menu=false;model.moveChild(child.id,child.parentTaskId!!,-1)})
                DropdownMenuItem(text={Text(stringResource(R.string.move_down))},onClick={menu=false;model.moveChild(child.id,child.parentTaskId!!,1)})
                DropdownMenuItem(text={Text(stringResource(R.string.unlink_parent))},onClick={menu=false;model.linkParent(child.id,null)})
            }
        }
    }
}
