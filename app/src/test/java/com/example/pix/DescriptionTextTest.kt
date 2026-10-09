package com.example.pix

import com.example.pix.domain.DescriptionText as D
import org.junit.Assert.*
import org.junit.Test

class DescriptionTextTest {
    @Test fun plainFormattingCharactersAndBlankLinesSurviveConversion() {
        val text = "# Literal heading\n**literal**\n\nLast"
        assertFalse(D.isChecklist(text))
        assertEquals(text, D.convert(D.convert(text, true), false))
        assertEquals(4, D.items(D.convert(text, true)).size)
    }
    @Test fun legacyChecklistKeepsCompletedStateAndLiteralText() {
        val source = "- [X] **done**\n- [ ] next"
        assertTrue(D.isChecklist(source))
        assertEquals(D.Item("**done**", true), D.items(source)[0])
        assertEquals("- [x] edited\n- [ ] next", D.replace(source, 0, "edited"))
    }
    @Test fun PastingMultipleLinesKeepsOtherItemsAndCreatesUncheckedRows() {
        assertEquals("- [x] first\n- [ ] second\n- [ ] third\n- [x] keep",
            D.replace("- [x] old\n- [x] keep", 0, "first\nsecond\nthird"))
    }
    @Test fun emptyAndUnicodeDescriptionsAreNotTruncated() {
        val text = "è 🌻 ".repeat(2000)
        assertEquals(text, D.convert(D.convert(text, true), false))
        assertEquals("- [ ] ", D.convert("", true))
    }
}
