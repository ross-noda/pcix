package com.example.pix.widget
import org.junit.Assert.*
import org.junit.Test
class WidgetSizingTest {
    @Test fun shrinkUsesBothDimensionsAndKeepsReadableFloor() {
        assertEquals(1f, widgetContentScale(320f,260f,260f), .001f)
        assertTrue(widgetContentScale(240f,260f,260f) < 1f)
        assertTrue(widgetContentScale(320f,200f,260f) < 1f)
        assertEquals(.75f, widgetContentScale(100f,60f,260f), .001f)
        assertEquals(1.1f, widgetContentScale(800f,800f,260f), .001f)
    }
}
