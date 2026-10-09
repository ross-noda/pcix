package com.example.pix

import com.example.pix.domain.IconSearch
import com.example.pix.ui.LucideCatalog
import com.example.pix.cloud.HabitCodec
import com.example.pix.data.HabitEntity
import org.junit.Assert.*
import org.junit.Test

class IconSearchTest {
    private fun search(query: String) = LucideCatalog.entries.filter { IconSearch.matches(it.keywords, IconSearch.terms(query)) }
    @Test fun catalogHasUniqueStableIdsAndCompleteResources() {
        assertEquals(1866, LucideCatalog.entries.size)
        assertEquals(1866, LucideCatalog.byId.size)
        assertTrue(LucideCatalog.entries.all { it.drawable != 0 && it.label != 0 })
        assertEquals(1866, search("").size)
    }
    @Test fun italianEnglishCaseAccentsAndMultipleTermsWork() {
        assertTrue(search("ACQUA").any { it.name == "droplet" })
        assertTrue(search("caffè").any { it.name == "coffee" })
        assertTrue(search("sonno").any { it.name == "bed" })
        assertTrue(search("book open").any { it.name == "book-open" })
        assertTrue(search("unfindablexyz").isEmpty())
    }
    @Test fun persistedIconIdentifiersRoundTripAndLegacyRemainsValid() {
        for (icon in listOf("REPEAT", "lucide:droplet")) {
            val habit = HabitEntity(name = "Water", icon = icon)
            assertEquals(habit, HabitCodec.parseHabit(HabitCodec.habit(habit)))
        }
    }
}
