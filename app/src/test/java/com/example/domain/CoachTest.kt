package com.example.domain

import com.example.data.LogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class CoachTest {

    @Test
    fun `coach constants match spec`() {
        assertEquals(0.34, Coach.CLEAN_FAULTS_PER_REP, 0.001)
        assertEquals(3, Coach.STALL_LIMIT)
        assertEquals(0.9, Coach.DELOAD_FACTOR, 0.001)
    }

    @Test
    fun `estimate1RM normal case`() {
        // Epley: 100 * (1 + 10/30) = 133.333
        assertEquals(133.333, Coach.estimate1RM(10, 100.0), 0.001)
    }

    @Test
    fun `estimate1RM zero reps returns zero`() {
        assertEquals(0.0, Coach.estimate1RM(0, 100.0), 0.001)
    }

    @Test
    fun `estimate1RM invalid load returns zero`() {
        assertEquals(0.0, Coach.estimate1RM(10, -50.0), 0.001)
    }
    
    @Test
    fun `isExecutionClean true when below fault threshold`() {
        // 3 faults / 10 reps = 0.30 < 0.34
        assertTrue(Coach.isExecutionClean(10, 3))
    }

    @Test
    fun `isExecutionClean false when above fault threshold`() {
        // 4 faults / 10 reps = 0.40 > 0.34
        assertFalse(Coach.isExecutionClean(10, 4))
    }

    @Test
    fun `isExecutionClean zero reps is always false`() {
        assertFalse(Coach.isExecutionClean(0, 0))
    }

    @Test
    fun `evaluateSession clean success progression`() {
        val history = listOf(
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null)
        )
        val result = Coach.evaluateSession(history, currentReps = 5, currentLoad = 55.0, currentFaultCount = 0)
        
        assertEquals(Coach.Progression.INCREASE, result.progression)
        assertEquals(57.5, result.nextLoad, 0.001)
        assertTrue(result.isClean)
    }

    @Test
    fun `evaluateSession below stall limit holds`() {
        // History has 1 stall at 55.0
        val history = listOf(
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null),
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 55.0, faultEvents = "[{},{},{},{}]", correctedFrom = null) // faulted
        )
        // Current is 2nd stall
        val result = Coach.evaluateSession(history, currentReps = 5, currentLoad = 55.0, currentFaultCount = 3)
        
        assertEquals(Coach.Progression.HOLD, result.progression)
        assertEquals(55.0, result.nextLoad, 0.001)
        assertFalse(result.isClean)
    }

    @Test
    fun `evaluateSession hits stall limit and deloads`() {
        // History has 2 stalls at 55.0
        val history = listOf(
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 50.0, faultEvents = "[]", correctedFrom = null),
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 55.0, faultEvents = "[{},{},{},{}]", correctedFrom = null), // stall 1
            LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 55.0, faultEvents = "[{},{},{},{}]", correctedFrom = null)  // stall 2
        )
        // Current is 3rd stall -> trigger deload
        val result = Coach.evaluateSession(history, currentReps = 5, currentLoad = 55.0, currentFaultCount = 4)
        
        assertEquals(Coach.Progression.DELOAD, result.progression)
        // 55.0 * 0.9 = 49.5 -> rounded to nearest 0.5 is 49.5
        assertEquals(49.5, result.nextLoad, 0.001)
        assertFalse(result.isClean)
    }

    @Test
    fun `evaluateSession no history baseline`() {
        val history = emptyList<LogEntry>()
        val result = Coach.evaluateSession(history, currentReps = 5, currentLoad = 20.0, currentFaultCount = 0)
        
        assertEquals(Coach.Progression.INCREASE, result.progression)
        assertEquals(22.5, result.nextLoad, 0.001)
        assertTrue(result.isClean)
    }

    @Test
    fun `evaluateSession invalid data holds`() {
        val history = emptyList<LogEntry>()
        val result = Coach.evaluateSession(history, currentReps = 0, currentLoad = -10.0, currentFaultCount = 0)

        assertEquals(Coach.Progression.HOLD, result.progression)
        assertFalse(result.isClean)
    }

    // ── P0-3: bodyweight lifts progress reps, never a fabricated load ──────────────────────

    @Test
    fun `a clean bodyweight session progresses the rep target, not an invented load`() {
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 10, currentLoad = 0.0, currentFaultCount = 0, exId = "pushup",
        )
        assertEquals(Coach.Progression.INCREASE, result.progression)
        assertEquals(Coach.Unit.REPS, result.unit)
        assertEquals(11.0, result.nextLoad, 0.001) // reps + 1, never a kg value
    }

    @Test
    fun `a bodyweight lift never deloads a load, however many sessions it stalls`() {
        val history = List(3) { LogEntry(exId = "pushup", at = "", reps = 8, sets = 1, load = 0.0, faultEvents = "[{},{},{},{}]", correctedFrom = null) }
        val result = Coach.evaluateSession(history, currentReps = 8, currentLoad = 0.0, currentFaultCount = 4, exId = "pushup")
        assertEquals(Coach.Progression.HOLD, result.progression) // never DELOAD for a bodyweight lift
        assertEquals(Coach.Unit.REPS, result.unit)
    }

    // ── P0-3: a real exId snaps progression to the actual plate grid ────────────────────────

    @Test
    fun `a loaded lift's clean progression snaps to a real plate grid, not a flat unsnapped +2_5`() {
        val coarse = TrainingProfile(plates = listOf(20.0, 10.0)) // barbellStep = 20
        val history = listOf(LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 40.0, faultEvents = "[]", correctedFrom = null))
        val result = Coach.evaluateSession(history, currentReps = 5, currentLoad = 40.0, currentFaultCount = 0, exId = "squat", profile = coarse)
        assertEquals(Coach.Progression.INCREASE, result.progression)
        // squat's own increment (5kg, a Legs compound) floored by barbellStep(20) -> 20; 40+20=60, already on the grid
        assertEquals(60.0, result.nextLoad, 0.001)
    }

    @Test
    fun `without a real exId, progression falls back to the flat +2_5 rather than crashing`() {
        // Regression guard: evaluateSession must not NPE when the pre-P0-3 4-arg call shape is used.
        val result = Coach.evaluateSession(emptyList(), currentReps = 5, currentLoad = 20.0, currentFaultCount = 0)
        assertEquals(22.5, result.nextLoad, 0.001)
    }
}
