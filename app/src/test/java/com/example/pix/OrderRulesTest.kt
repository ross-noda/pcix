package com.example.pix

import com.example.pix.domain.OrderRules
import org.junit.Assert.*
import org.junit.Test

class OrderRulesTest {
    @Test
    fun movesBothDirectionsWithoutLosingItems() {
        assertEquals(
            listOf("b", "c", "a", "d"),
            OrderRules.move(listOf("a", "b", "c", "d"), "a", "c"),
        )
        assertEquals(
            listOf("d", "a", "b", "c"),
            OrderRules.move(listOf("a", "b", "c", "d"), "d", "a"),
        )
    }

    @Test
    fun missingOrSameTargetLeavesOrderUnchanged() {
        val ids = listOf("a", "b")
        assertEquals(ids, OrderRules.move(ids, "deleted", "a"))
        assertEquals(ids, OrderRules.move(ids, "a", "deleted"))
        assertEquals(ids, OrderRules.move(ids, "a", "a"))
    }
}
