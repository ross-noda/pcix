package com.example.pix.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp

@Composable
fun EditorToolRow(
    icon: PixSymbol,
    title: String,
    value: String? = null,
    enabled: Boolean = true,
    click: () -> Unit,
) {
    Row(
        Modifier.testTag("editor-tool-${icon.name}")
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .semantics(mergeDescendants = true) { contentDescription = title }
            .clickable(enabled = enabled, onClick = click)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val color =
            if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
            else MaterialTheme.colorScheme.onSurface.copy(alpha = .38f)
        PixIcon(icon, modifier = Modifier.size(18.dp), tint = color)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodySmall, color = color)
            if (!value.isNullOrBlank())
                Text(
                    value,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
        }
        PixIcon(PixSymbol.CHEVRON, modifier = Modifier.size(16.dp), tint = color)
    }
}
