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
        // 55.0 * 0.9 = 49.5, and the legacy insights.js round2() takes that to the nearest 2.5 -> 50.0.
        // 49.5 was the old answer and it is not loadable: a 20 kg bar with 2.5s cannot make it.
        assertEquals(50.0, result.nextLoad, 0.001)
        assertFalse(result.isClean)
    }

    // ── reps hit and form clean are separate conditions, and both gate an increase ───────────

    @Test
    fun `clean form on missed reps does not earn weight`() {
        // 3 of 15 reps, flawlessly. The old code added weight for this, because INCREASE only ever
        // checked the fault rate.
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 3, currentLoad = 60.0, currentFaultCount = 0,
            exId = "squat", targetReps = 15,
        )
        assertEquals(Coach.Progression.HOLD, result.progression)
        assertEquals("reps missed", result.reason)
        assertEquals(60.0, result.nextLoad, 0.001)
        // Clean is still true — it was clean. It just was not enough on its own.
        assertTrue(result.isClean)
    }

    @Test
    fun `hitting the reps with broken form is a different verdict from missing them`() {
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 5, currentLoad = 60.0, currentFaultCount = 3,
            exId = "squat", targetReps = 5,
        )
        assertEquals(Coach.Progression.HOLD, result.progression)
        assertEquals("form broke down", result.reason)
        assertFalse(result.isClean)
    }

    @Test
    fun `both conditions met still progresses`() {
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 5, currentLoad = 60.0, currentFaultCount = 0,
            exId = "squat", targetReps = 5,
        )
        assertEquals(Coach.Progression.INCREASE, result.progression)
        assertEquals("all reps clean", result.reason)
    }

    @Test
    fun `no target to compare against withholds the reps-missed verdict rather than inventing one`() {
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 5, currentLoad = 60.0, currentFaultCount = 0,
            exId = "squat", targetReps = null,
        )
        assertEquals(Coach.Progression.INCREASE, result.progression)
        assertEquals(true, result.evidence["repsHit"])
        assertEquals(null, result.evidence["targetReps"])
    }

    // ── a fault event is one event, however many braces it contains ─────────────────────────

    @Test
    fun `a fault event with a nested object counts once, not twice`() {
        val entry = LogEntry(
            exId = "squat", at = "", reps = 10, sets = 1, load = 50.0,
            faultEvents = """[{"rep":3,"joint":{"name":"knee","angle":95}}]""",
            correctedFrom = null,
        )
        // Brace-counting said 2 here. Phantom faults push a clean session into a stall, and a stall
        // into a deload.
        assertEquals(1, Coach.faultCountOf(entry))
        assertTrue(Coach.isExecutionClean(entry.reps, Coach.faultCountOf(entry)))
    }

    @Test
    fun `unparseable fault json counts as no faults rather than crashing a verdict`() {
        val entry = LogEntry(exId = "squat", at = "", reps = 5, sets = 1, load = 50.0, faultEvents = "not json", correctedFrom = null)
        assertEquals(0, Coach.faultCountOf(entry))
    }

    // ── the verdict carries the numbers it was drawn from ───────────────────────────────────

    @Test
    fun `evidence holds the figures the decision actually used`() {
        val result = Coach.evaluateSession(
            history = emptyList(), currentReps = 10, currentLoad = 60.0, currentFaultCount = 4,
            exId = "squat", targetReps = 10,
        )
        assertEquals("form broke down", result.reason)
        // 4/10 = 0.40 against the 0.34 limit: the reason for the conclusion, not the conclusion.
        assertEquals(0.40, result.evidence["faultsPerRep"] as Double, 0.001)
        assertEquals(Coach.CLEAN_FAULTS_PER_REP, result.evidence["cleanLimit"] as Double, 0.001)
        assertEquals(10, result.evidence["totalReps"])
        assertEquals(4, result.evidence["totalFaults"])
        assertEquals(60.0, result.evidence["from"] as Double, 0.001)
        assertEquals(60.0, result.evidence["to"] as Double, 0.001)
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
