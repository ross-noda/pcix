package com.example.pix.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pix.R

@Stable
class ReorderState {
    val bounds = mutableMapOf<String, Rect>()
    var source by mutableStateOf<String?>(null)
    var target by mutableStateOf<String?>(null)
    var delta by mutableFloatStateOf(0f)
    var startY = 0f

    fun clear() {
        source = null
        target = null
        delta = 0f
    }
}

/** A dedicated handle keeps reordering distinct from row actions and text editing. */
@Composable
fun ReorderItem(
    id: String,
    candidates: List<String>,
    state: ReorderState,
    move: (String, String) -> Unit,
    content: @Composable () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    var handleY by remember { mutableFloatStateOf(0f) }
    val currentMove by rememberUpdatedState(move)
    val currentCandidates by rememberUpdatedState(candidates)
    val dragged = state.source == id
    DisposableEffect(id) { onDispose { state.bounds.remove(id) } }
    Row(
        Modifier.fillMaxWidth()
            .zIndex(if (dragged) 1f else 0f)
            .onGloballyPositioned { state.bounds[id] = it.boundsInRoot() }
            .graphicsLayer {
                translationY = if (dragged) state.delta else 0f
                alpha = if (dragged) .85f else 1f
            }
            .then(
                if (state.target == id)
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.shapes.medium,
                    )
                else Modifier
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { content() }
        Box {
            IconButton(
                onClick = { menu = true },
                modifier =
                    Modifier.testTag("reorder-$id")
                        .onGloballyPositioned { handleY = it.boundsInRoot().center.y }
                        .pointerInput(id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    state.source = id
                                    state.startY = handleY
                                    state.delta = 0f
                                },
                                onDragCancel = { state.clear() },
                                onDragEnd = {
                                    val target = state.target
                                    state.clear()
                                    if (target != null) currentMove(id, target)
                                },
                                onDrag = { change, amount ->
                                    change.consume()
                                    state.delta += amount.y
                                    val y = state.startY + state.delta
                                    state.target =
                                        state.bounds.entries
                                            .firstOrNull { (key, rect) ->
                                                key != id &&
                                                    key in currentCandidates &&
                                                    y >= rect.top &&
                                                    y <= rect.bottom
                                            }
                                            ?.key
                                },
                            )
                        },
            ) {
                PixIcon(PixSymbol.MENU, stringResource(R.string.reorder_item))
            }
            DropdownMenu(menu, { menu = false }) {
                val index = candidates.indexOf(id)
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_up)) },
                    enabled = index > 0,
                    onClick = {
                        menu = false
                        currentMove(id, candidates[index - 1])
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.move_down)) },
                    enabled = index >= 0 && index < candidates.lastIndex,
                    onClick = {
                        menu = false
                        currentMove(id, candidates[index + 1])
                    },
                )
            }
        }
    }
}
