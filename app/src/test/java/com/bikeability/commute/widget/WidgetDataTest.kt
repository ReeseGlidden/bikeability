package com.bikeability.commute.widget

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WidgetDataTest {

    @Test
    fun `header label flags the evening flip to tomorrow`() {
        val today = LocalDate.of(2026, 7, 11)
        assertEquals("Sat Jul 11", displayDateLabel(today, today))
        assertEquals("Tomorrow · Sun Jul 12", displayDateLabel(today.plusDays(1), today))
    }

    @Test
    fun `forecast day titles`() {
        val today = LocalDate.of(2026, 7, 11)
        assertEquals("Today · Sat Jul 11", dayTitle(today, today))
        assertEquals("Tomorrow · Sun Jul 12", dayTitle(today.plusDays(1), today))
        assertEquals("Mon Jul 13", dayTitle(today.plusDays(2), today))
    }
}
