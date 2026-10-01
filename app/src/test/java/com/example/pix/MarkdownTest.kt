package com.example.pix

import com.example.pix.domain.Markdown
import org.commonmark.node.*
import org.junit.Assert.*
import org.junit.Test

class MarkdownTest {
    @Test fun emptyDescriptionIsValid() { assertNull(Markdown.parse("").firstChild) }
    @Test fun parsesRequiredBlocksAndInlineFormatting() {
        val source="## Heading\n\n**bold** *italic* ~~strike~~ [link](https://example.com) `inline`\n\n> quote\n\n- item\n\n1. ordered\n\n```kotlin\nval x=1\n```"
        val types=mutableSetOf<String>()
        fun visit(n:Node) {types+=n.javaClass.simpleName;var c=n.firstChild;while(c!=null){visit(c);c=c.next}}
        visit(Markdown.parse(source))
        assertTrue(types.containsAll(setOf("Heading","StrongEmphasis","Emphasis","Strikethrough","Link","Code","BlockQuote","BulletList","OrderedList","FencedCodeBlock")))
    }
    @Test fun insertChecklistAtSelectionPreservesSuffix() {
        val edit=Markdown.insertChecklist("before AFTER",7,12)
        assertEquals("- [ ] before AFTER",edit.text);assertEquals(edit.text.length,edit.cursor)
    }
    @Test fun checklistConvertsSelectedLinesWithoutDroppingText() {
        val edit = Markdown.insertChecklist("first\nsecond\nlast", 0, 13)
        assertEquals("- [ ] first\n- [ ] second\nlast", edit.text)
        assertEquals(24, edit.cursor)
        assertEquals("- [ ] first", Markdown.insertChecklist("- [ ] first", 8).text)
    }
    @Test fun insertOnCurrentLineKeepsCursorAndSuffix() {
        val edit = Markdown.insertChecklist("before\nhello world", 12)
        assertEquals("before\n- [ ] hello world", edit.text)
        assertEquals(18, edit.cursor)
        assertEquals("- [ ] ", Markdown.insertChecklist("", 0).text)
    }
    @Test fun checklistEnterContinuesAndEmptyLineExits() {
        val old="- [x] done"
        val next=Markdown.continueChecklist(old,old+"\n",old.length+1)
        assertEquals("- [x] done\n- [ ] ",next.text)
        val exit=Markdown.continueChecklist(next.text,next.text+"\n",next.text.length+1)
        assertEquals("- [x] done\n\n",exit.text)
    }
    @Test fun multilinePasteIsNotRewritten() {
        val text="- [ ] first\nsecond\nthird"
        assertEquals(text,Markdown.continueChecklist("",text,text.length).text)
    }
    @Test fun toggleOnlyTouchesExactMarkerEvenWithDuplicatesAndCrlf() {
        val source="- [ ] same\r\n- [x] same\r\n"
        val doc=Markdown.parse(source)
        val paragraph=doc.firstChild.lastChild.firstChild
        val offset=Markdown.checkboxOffset(source,paragraph)!!
        val toggled=Markdown.toggle(source,offset)
        assertEquals("- [ ] same\r\n- [ ] same\r\n",toggled)
        assertEquals(source,Markdown.toggle(toggled,offset))
        assertEquals(source,Markdown.toggle(source,1))
    }
    @Test fun fencedCodeIsNotInteractiveChecklist() {
        val doc=Markdown.parse("```\n- [ ] literal\n```")
        assertTrue(doc.firstChild is FencedCodeBlock)
        assertNull(Markdown.checkboxOffset("```\n- [ ] literal\n```",doc.firstChild))
    }
}
