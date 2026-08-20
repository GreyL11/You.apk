package com.example.domain

import com.example.data.LogEntry
import org.junit.Assert.*
import org.junit.Test

class TrainingLoadEngineTest {
    private fun logsOnDays(vararg daysAgo: Int, from: String = "2026-08-29", sets: Int = 3) =
        daysAgo.map { d ->
            val date = java.time.LocalDate.parse(from).minusDays(d.toLong())
            LogEntry(exId = "squat", at = "${date}T10:00:00", reps = 5, sets = sets, load = 50.0, faultEvents = "[]", correctedFrom = null)
        }

    @Test
    fun `fewer than MIN_WEEKS_HISTORY of real prior volume refuses to compare`() {
        // Only 1 prior week has any logged volume.
        val logs = logsOnDays(10, 12) // both land in week 2 back, weeks 1,3,4 empty
        val reading = TrainingLoadEngine.evaluate(logs, nowDayKey = "2026-08-29")
        assertEquals(TrainingLoadEngine.State.INSUFFICIENT_DATA, reading.state)
        assertNull(reading.typicalWeeklySets)
    }

    @Test
    fun `a real week far above this person's own typical reads HIGH_LOAD`() {
        // 3 prior weeks at 6 sets/week each (2 sessions x 3 sets), this week suddenly 21 sets.
        val priorWeeks = (1..3).flatMap { w -> logsOnDays(7 * w, 7 * w + 2, sets = 3) }
        val thisWeek = logsOnDays(0, 1, 2, sets = 7) // 21 sets this week
        val reading = TrainingLoadEngine.evaluate(priorWeeks + thisWeek, nowDayKey = "2026-08-29")
        assertEquals(6.0, reading.typicalWeeklySets!!, 0.001)
        assertEquals(21, reading.recentSets)
        assertEquals(TrainingLoadEngine.State.HIGH_LOAD, reading.state)
    }

    @Test
    fun `a real week far below typical reads UNDERLOADED, comparable volume reads APPROPRIATE`() {
        val priorWeeks = (1..3).flatMap { w -> logsOnDays(7 * w, 7 * w + 2, sets = 3) } // 6/week typical
        val quietWeek = logsOnDays(0, sets = 2) // 2 sets this week
        val under = TrainingLoadEngine.evaluate(priorWeeks + quietWeek, nowDayKey = "2026-08-29")
        assertEquals(TrainingLoadEngine.State.UNDERLOADED, under.state)

        val normalWeek = logsOnDays(0, 1, sets = 3) // 6 sets, matches typical
        val appropriate = TrainingLoadEngine.evaluate(priorWeeks + normalWeek, nowDayKey = "2026-08-29")
        assertEquals(TrainingLoadEngine.State.APPROPRIATE, appropriate.state)
    }
}
