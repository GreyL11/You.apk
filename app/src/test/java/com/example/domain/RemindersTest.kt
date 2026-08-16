package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class RemindersTest {
    @Test
    fun `disabled or invalid window returns empty schedule`() {
        assertTrue(Reminders.hydrationHours(false, 8, 20, 2).isEmpty())
        assertTrue(Reminders.hydrationHours(true, 20, 8, 2).isEmpty())
        assertTrue(Reminders.hydrationHours(true, 8, 20, 0).isEmpty())
        assertTrue(Reminders.hydrationHours(true, 8, 20, -1).isEmpty())
    }
    
    @Test
    fun `computes exact hour slot with no duplicates`() {
        val hours = Reminders.hydrationHours(true, 8, 13, 2)
        assertEquals(listOf(8, 10, 12), hours)
    }
    
    @Test
    fun `every hour stays inside bounds`() {
        val hours = Reminders.hydrationHours(true, 9, 17, 3)
        assertEquals(listOf(9, 12, 15), hours)
    }
    
    @Test
    fun `id determinism`() {
        assertEquals(Reminders.hydrationId(8), Reminders.hydrationId(8))
        assertEquals(Reminders.postponeId("act1"), Reminders.postponeId("act1"))
    }
    
    @Test
    fun `different hours get different ids`() {
        assertNotEquals(Reminders.hydrationId(8), Reminders.hydrationId(9))
    }
    
    @Test
    fun `hydration and postpone never collide`() {
        assertNotEquals(Reminders.hydrationId(8), Reminders.postponeId("8"))
    }
    
    @Test
    fun `ids stay positive`() {
        assertTrue(Reminders.hydrationId(8) > 0)
        assertTrue(Reminders.postponeId("very_long_string_that_might_overflow_hash_if_not_careful") > 0)
    }
}
