package com.example.pix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.domain.DescriptionText

/** Always-editable description, without a separate preview or Markdown rendering. */
@Composable
fun MarkdownDescription(id: String, source: String, onChange: (String) -> Unit) {
    var checklist by rememberSaveable(id) { mutableStateOf(DescriptionText.isChecklist(source)) }
    val rows = remember(source) { DescriptionText.items(source) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.description), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            TextButton(onClick = {
                checklist = !checklist
                onChange(DescriptionText.convert(source, checklist))
            }, modifier = Modifier.testTag("description-mode")) {
                PixIcon(PixSymbol.CHECKLIST, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(if (checklist) R.string.description_plain else R.string.description_checklist))
            }
        }
        if (!checklist) {
            OutlinedTextField(source, onChange, Modifier.fillMaxWidth().testTag("detail-notes"),
                minLines = 4, placeholder = { Text(stringResource(R.string.description_hint)) },
                textStyle = MaterialTheme.typography.bodyMedium)
        } else {
            rows.forEachIndexed { index, item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(item.checked == true, { checked ->
                        onChange(DescriptionText.encode(rows.toMutableList().apply { set(index, item.copy(checked = checked)) }))
                    }, Modifier.testTag("description-check-$index").semantics { contentDescription = item.text })
                    OutlinedTextField(item.text, { onChange(DescriptionText.replace(source, index, it)) },
                        Modifier.weight(1f).testTag("description-item-$index"),
                        placeholder = { Text(stringResource(R.string.description_item)) },
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            textDecoration = if (item.checked == true) TextDecoration.LineThrough else TextDecoration.None))
                    IconButton(onClick = {
                        val remaining = rows.toMutableList().apply { removeAt(index) }
                        onChange(DescriptionText.encode(remaining.ifEmpty { listOf(DescriptionText.Item("", false)) }))
                    }, modifier = Modifier.testTag("description-delete-$index")) {
                        PixIcon(PixSymbol.DELETE, stringResource(R.string.description_remove), modifier = Modifier.size(18.dp))
                    }
                }
            }
            TextButton(onClick = { onChange(DescriptionText.encode(rows + DescriptionText.Item("", false))) },
                modifier = Modifier.testTag("description-add")) {
                PixIcon(PixSymbol.PLUS, null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.description_add))
            }
        }
    }
}
