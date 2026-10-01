package com.example.pix.domain

import org.commonmark.node.Node
import org.commonmark.parser.Parser
import org.commonmark.parser.IncludeSourceSpans
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension

object Markdown {
    private val parser = Parser.builder().extensions(listOf(StrikethroughExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES).build()
    fun parse(source: String): Node = parser.parse(source)
    data class Edit(val text: String, val cursor: Int)
    private val checklist = Regex("""^(\s*(?:[-+*]\s+)?)[\[]([ xX])[\]] ?(.*)$""")

    fun insertChecklist(text: String, start: Int, end: Int = start): Edit {
        val from = text.lastIndexOf('\n', (start - 1).coerceAtMost(text.lastIndex)) + 1
        val last = if (end > start && text.getOrNull(end - 1) == '\n') end - 1 else end
        val to = text.indexOf('\n', last).let { if (it < 0) text.length else it }
        val original = text.substring(from, to)
        val lines = original.split('\n')
        val converted = lines.joinToString("\n") { line ->
            if (checklist.matches(line)) line else {
                val indent = line.takeWhile { it == ' ' || it == '\t' }
                indent + "- [ ] " + line.drop(indent.length).replaceFirst(Regex("^(?:[-+*]|[0-9]+[.)])\\s+"), "")
            }
        }
        val delta = converted.length - original.length
        val cursor = if (start == end) (start + delta).coerceIn(from, from + converted.length) else from + converted.length
        return Edit(text.take(from) + converted + text.drop(to), cursor)
    }

    fun continueChecklist(old: String, text: String, cursor: Int): Edit {
        if(cursor<=0 || text.length!=old.length+1 || text[cursor-1]!='\n' || text.removeRange(cursor-1,cursor)!=old) return Edit(text,cursor)
        val start=text.lastIndexOf('\n',cursor-2)+1
        val line=text.substring(start,cursor-1)
        val match=checklist.matchEntire(line) ?: return Edit(text,cursor)
        if(match.groupValues[3].isBlank()) return Edit(text.removeRange(start,cursor-1),start+1)
        val indent=line.takeWhile { it==' ' || it=='\t' }
        val prefix=indent+"- [ ] "
        return Edit(text.take(cursor)+prefix+text.drop(cursor),cursor+prefix.length)
    }

    fun checkboxOffset(source: String,node: Node): Int? {
        val start=node.sourceSpans.firstOrNull()?.inputIndex ?: return null
        val line=source.substring(start).substringBefore('\n').removeSuffix("\r")
        val match=checklist.matchEntire(line) ?: return null
        return start+match.groups[2]!!.range.first
    }

    fun toggle(source: String, offset: Int): String {
        if(offset !in 1 until source.lastIndex || source[offset-1]!='[' || source[offset+1]!=']' || source[offset] !in " xX") return source
        return source.replaceRange(offset,offset+1,if(source[offset]==' ') "x" else " ")
    }
}
