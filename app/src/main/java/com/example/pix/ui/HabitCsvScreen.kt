package com.example.pix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R

@Composable
fun HabitCsvScreen(model: HabitsViewModel) {
    val busy by model.csvBusy.collectAsStateWithLifecycle()
    val preview by model.csvPreview.collectAsStateWithLifecycle()
    val result by model.csvResult.collectAsStateWithLifecycle()
    val reason by model.csvReason.collectAsStateWithLifecycle()
    val error by model.csvError.collectAsStateWithLifecycle()
    val exported by model.csvExported.collectAsStateWithLifecycle()
    var replace by remember { mutableStateOf(false) }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(model::prepareCsv) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri -> uri?.let(model::exportCsv) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.h_csv_description))
        Button({ import.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream")) }, enabled = !busy) { Text(stringResource(R.string.h_csv_import)) }
        OutlinedButton({ export.launch("pcix-habits-${java.time.LocalDate.now()}.csv") }, enabled = !busy) { Text(stringResource(R.string.h_csv_export)) }
        Text(stringResource(R.string.h_csv_limits), style = MaterialTheme.typography.bodySmall)
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(if (it >= 0) stringResource(R.string.h_csv_invalid, it, stringResource(when (reason) {
            com.example.pix.data.HabitCsv.Reason.FORMAT -> R.string.h_csv_error_format
            com.example.pix.data.HabitCsv.Reason.IDENTITY -> R.string.h_csv_error_identity
            com.example.pix.data.HabitCsv.Reason.UNIT -> R.string.h_csv_error_unit
            com.example.pix.data.HabitCsv.Reason.DATE -> R.string.h_csv_error_date
            com.example.pix.data.HabitCsv.Reason.COUNT -> R.string.h_csv_error_count
            com.example.pix.data.HabitCsv.Reason.STATUS -> R.string.h_csv_error_status
            com.example.pix.data.HabitCsv.Reason.DUPLICATE -> R.string.h_csv_error_duplicate
        })) else stringResource(R.string.error), color = MaterialTheme.colorScheme.error) }
        if (exported) Text(stringResource(R.string.h_csv_exported))
        result?.let { Text(stringResource(R.string.h_csv_result, it.newHabits, it.writtenLogs, it.keptLogs)) }
    }
    preview?.let { pending ->
        LaunchedEffect(pending) { replace = false }
        AlertDialog(onDismissRequest = { if (!busy) model.cancelCsv() }, title = { Text(stringResource(R.string.h_csv_import)) }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.h_csv_summary, pending.document.habits.size, pending.document.logCount, pending.newHabits, pending.existingLogs))
                if (pending.futureLogs > 0) Text(stringResource(R.string.h_csv_future, pending.futureLogs))
                Text(stringResource(R.string.h_csv_defaults))
                pending.document.habits.forEach { Text(stringResource(R.string.h_csv_habit_summary, it.name, it.entries.size)) }
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(replace, { replace = it }, enabled = !busy)
                    Text(stringResource(R.string.h_csv_replace), Modifier.weight(1f))
                }
            }
        }, confirmButton = { TextButton({ model.importCsv(replace) }, enabled = !busy) { Text(stringResource(R.string.h_csv_import)) } }, dismissButton = { TextButton(model::cancelCsv, enabled = !busy) { Text(stringResource(R.string.cancel)) } })
    }
}
