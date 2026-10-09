package com.example.pix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.pix.R
import com.example.pix.domain.IconSearch

@Composable
fun HabitIcon(raw: String, description: String? = null, tint: Color = LocalContentColor.current) {
    val entry = LucideCatalog.byId[raw]
    if (entry != null) Icon(painterResource(entry.drawable), description, Modifier.size(24.dp), tint)
    else PixIcon(habitSymbol(raw), description, tint = tint)
}

@Composable
fun HabitIconPicker(selected: String, choose: (String) -> Unit, dismiss: () -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) {
        val terms = IconSearch.terms(query)
        LucideCatalog.entries.filter { IconSearch.matches(it.keywords, terms) }
    }
    Dialog(onDismissRequest = dismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize().systemBarsPadding(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(16.dp).imePadding(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.h_lucide_title), style = MaterialTheme.typography.titleLarge)
                    IconButton(dismiss) { PixIcon(PixSymbol.CLOSE, stringResource(R.string.cancel)) }
                }
                OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text(stringResource(R.string.h_icon_search)) },
                    leadingIcon = { PixIcon(PixSymbol.SEARCH) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton({ query = "" }) { PixIcon(PixSymbol.CLOSE, stringResource(R.string.h_icon_clear)) } })
                Text(stringResource(R.string.h_icon_search_help), style = MaterialTheme.typography.bodySmall)
                Text(stringResource(R.string.h_icon_results, results.size), style = MaterialTheme.typography.labelMedium)
                if (results.isEmpty()) Text(stringResource(R.string.h_icon_empty))
                LazyVerticalGrid(columns = GridCells.Adaptive(104.dp), modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(results, key = { it.id }) { entry ->
                        val label = stringResource(entry.label)
                        val active = selected == entry.id
                        Surface(onClick = { choose(entry.id) }, shape = MaterialTheme.shapes.medium,
                            color = if (active) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.semantics { this.selected = active; contentDescription = label }) {
                            Column(Modifier.padding(12.dp), horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                HabitIcon(entry.id)
                                Text(label, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}
