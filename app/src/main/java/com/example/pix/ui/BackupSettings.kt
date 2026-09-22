package com.example.pix.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R

@Composable
fun BackupSettings(model: TasksViewModel) {
    val busy by model.backupBusy.collectAsStateWithLifecycle()
    val pending by model.pendingBackup.collectAsStateWithLifecycle()
    val message by model.backupMessage.collectAsStateWithLifecycle()
    val export =
        rememberLauncherForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) {
            it?.let(model::exportBackup)
        }
    val import =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
            it?.let(model::prepareBackup)
        }
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_export)) },
                supportingContent = { Text(stringResource(R.string.backup_contents)) },
                modifier =
                    Modifier.clickable(enabled = !busy) {
                        export.launch("pcix-${java.time.LocalDate.now()}.zip")
                    },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.backup_import)) },
                supportingContent = { Text(stringResource(R.string.backup_replace_hint)) },
                modifier =
                    Modifier.clickable(enabled = !busy) {
                        import.launch(
                            arrayOf(
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream",
                            )
                        )
                    },
            )
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (message != null) Text(stringResource(message!!), Modifier.padding(16.dp))
        }
    }
    pending?.let { backup ->
        AlertDialog(
            onDismissRequest = model::cancelBackup,
            title = { Text(stringResource(R.string.backup_confirm)) },
            text = {
                Text(
                    stringResource(R.string.backup_summary, backup.tasks, backup.lists, backup.tags)
                )
            },
            confirmButton = {
                TextButton(onClick = model::restoreBackup) {
                    Text(stringResource(R.string.backup_restore))
                }
            },
            dismissButton = {
                TextButton(onClick = model::cancelBackup) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}
