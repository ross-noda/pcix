package com.example.pix.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.unit.IntOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.domain.Markdown
import org.commonmark.node.*
import org.commonmark.ext.gfm.strikethrough.Strikethrough

/** Source text is never shortened by rendering: IME, selection and clipboard share its offsets. */
@Composable
fun MarkdownDescription(id: String, source: String, onChange: (String)->Unit) {
    var field by rememberSaveable(id, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue(source)) }
    val focus = remember { FocusRequester() }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val density = LocalDensity.current
    val style = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.lineHeight * 1.25f)
    val document = remember(field.text) { Markdown.parse(field.text) }
    val checks = remember(document) {
        buildList {
            fun visit(node: Node) {
                if (node is org.commonmark.node.Paragraph && node.parent is ListItem)
                    Markdown.checkboxOffset(field.text, node)?.let { add(it) }
                node.nodes().forEach(::visit)
            }
            visit(document)
        }
    }
    val codeColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val quoteColor = MaterialTheme.colorScheme.onSurfaceVariant
    val live = remember(document, field.text, style, codeColor, quoteColor) {
        val styled = AnnotatedString.Builder(field.text)
        fun visit(node: Node) {
            val span = when(node) {
                is Heading -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = style.fontSize * when(node.level) { 1 -> 1.5f; 2 -> 1.3f; else -> 1.15f })
                is StrongEmphasis -> SpanStyle(fontWeight = FontWeight.Bold)
                is Emphasis -> SpanStyle(fontStyle = FontStyle.Italic)
                is Strikethrough -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                is Code, is FencedCodeBlock, is IndentedCodeBlock -> SpanStyle(fontFamily = FontFamily.Monospace, background = codeColor)
                is BlockQuote -> SpanStyle(color = quoteColor, fontStyle = FontStyle.Italic)
                else -> null
            }
            if (span != null) node.sourceSpans.forEach {
                val end = (it.inputIndex + it.length).coerceAtMost(field.text.length)
                if (it.inputIndex < end) styled.addStyle(span, it.inputIndex, end)
            }
            node.nodes().forEach(::visit)
        }
        visit(document)
        styled.toAnnotatedString()
    }
    LaunchedEffect(source) {
        if (field.text != source) field = field.copy(text = source,
            selection = TextRange(field.selection.start.coerceAtMost(source.length), field.selection.end.coerceAtMost(source.length)), composition = null)
    }
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.description), Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
            IconButton(onClick = {
                val edit = Markdown.insertChecklist(field.text, field.selection.min, field.selection.max)
                field = TextFieldValue(edit.text, TextRange(edit.cursor))
                onChange(edit.text)
                focus.requestFocus()
            }, modifier = Modifier.testTag("markdown-checklist")) {
                PixIcon(PixSymbol.CHECKLIST, stringResource(R.string.markdown_checklist))
            }
        }
        Box(Modifier.fillMaxWidth()) {
            BasicTextField(field, { next ->
                val edit = if (next.composition == null && next.selection.collapsed)
                    Markdown.continueChecklist(field.text, next.text, next.selection.end)
                else Markdown.Edit(next.text, next.selection.end)
                val textChanged = edit.text != field.text
                field = if (edit.text == next.text) next else TextFieldValue(edit.text, TextRange(edit.cursor))
                if (textChanged) onChange(edit.text)
            }, modifier = Modifier.fillMaxWidth().padding(start = 36.dp).focusRequester(focus).testTag("detail-notes"),
                minLines = 3, textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                visualTransformation = VisualTransformation { input ->
                    TransformedText(if (input.text == live.text) live else input, OffsetMapping.Identity)
                }, onTextLayout = { layout = it },
                decorationBox = { inner ->
                    Box {
                        if (field.text.isEmpty()) Text(stringResource(R.string.markdown_hint), style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        inner()
                    }
                })
            // Separate gutter: tapping a checkbox never steals a source-text character or cursor position.
            layout?.takeIf { it.layoutInput.text.text == field.text }?.let { result ->
                checks.forEach { offset ->
                    val line = result.getLineForOffset(offset)
                    val top = result.getLineTop(line)
                    val height = result.getLineBottom(line) - top
                    val checkboxLabel = field.text.substring(offset + 2).substringBefore('\n').trim()
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Checkbox(field.text[offset] != ' ', {
                        val next = Markdown.toggle(field.text, offset)
                        field = field.copy(text = next, composition = null)
                        onChange(next)
                    }, modifier = Modifier.offset { IntOffset(0, (top + (height - with(density) { 28.dp.toPx() }) / 2).toInt()) }
                        .size(28.dp).testTag("markdown-toggle-$offset").semantics {
                            contentDescription = checkboxLabel
                        })
                    }
                }
            }
        }
    }
}

private fun Node.nodes(): List<Node> = generateSequence(firstChild) { it.next }.toList()

