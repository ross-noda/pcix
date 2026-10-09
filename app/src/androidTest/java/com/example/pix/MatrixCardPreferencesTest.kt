package com.example.pix

import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.*
import org.junit.Assert.*
import org.junit.Test

class MatrixCardPreferencesTest {
    @Test fun cardsAndOrderSurviveReopeningPreferences() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val name = "matrix-test-${java.util.UUID.randomUUID()}"
        val prefs = context.getSharedPreferences(name, 0)
        try {
            val card = MatrixCard(title = "FALLO SUBITO 🌻", custom = true, listIds = setOf("a", "b"), tagIds = setOf("x"),
                date = MatrixDate.RANGE, fromDay = 20000, toDay = 20030, priorities = setOf(0, 5))
            val config = MatrixConfig(cards = listOf(card, MatrixCard(), MatrixCard(title = "Personal"), card.copy(title = "Last")), cardOrder = listOf(3, 1, 0, 2))
            prefs.edit().putMatrixCards(config).commit()
            val read = context.getSharedPreferences(name, 0).readMatrixConfig()
            assertEquals(config.cards, read.cards)
            assertEquals(config.cardOrder, read.cardOrder)
            prefs.edit().putString("card:0", "broken-json").putString("cardOrder", "3,3,8").commit()
            val recovered = prefs.readMatrixConfig()
            assertEquals(MatrixCard(), recovered.cards[0])
            assertEquals("Last", recovered.cards[3].title)
            assertEquals(listOf(3,0,1,2), recovered.cardOrder)
        } finally { context.deleteSharedPreferences(name) }
    }
}
