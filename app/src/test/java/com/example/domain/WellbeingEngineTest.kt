package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class WellbeingEngineTest {
    @Test
    fun `fewer than MIN_CHECKINS real entries refuses to call any pattern`() {
        val r = WellbeingEngine.evaluate(listOf(3, 4, 3, 2))
        assertEquals(WellbeingEngine.Pattern.INSUFFICIENT_DATA, r.pattern)
        assertNull(r.note)
    }

    @Test
    fun `mostly-low recent ratings are read as a persistent pattern, with a supportive non-diagnostic note`() {
        val r = WellbeingEngine.evaluate(listOf(7, 7, 3, 2, 4, 3, 2))
        assertEquals(WellbeingEngine.Pattern.PERSISTENT_LOW, r.pattern)
        assertNotNull(r.note)
        assertFalse("must never sound diagnostic", r.note!!.contains("depress", ignoreCase = true))
        assertFalse("must never claim a cause", r.note!!.contains("testosterone", ignoreCase = true))
    }

    @Test
    fun `one bad day among mostly-good ratings is never called a persistent pattern`() {
        val r = WellbeingEngine.evaluate(listOf(7, 8, 7, 2, 8, 7, 8))
        assertEquals(WellbeingEngine.Pattern.STABLE, r.pattern)
        assertNull(r.note)
    }

    @Test
    fun `a real recent improvement is read as IMPROVING, not silently STABLE`() {
        val r = WellbeingEngine.evaluate(listOf(3, 3, 4, 3, 7, 8, 8))
        assertEquals(WellbeingEngine.Pattern.IMPROVING, r.pattern)
        assertNull(r.note)
    }

    @Test
    fun `steady mid-range ratings read as STABLE, not an invented trend`() {
        val r = WellbeingEngine.evaluate(listOf(6, 5, 6, 5, 6, 5, 6))
        assertEquals(WellbeingEngine.Pattern.STABLE, r.pattern)
    }
}
