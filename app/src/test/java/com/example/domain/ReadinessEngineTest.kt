package com.example.domain

import com.example.data.DayRow
import com.example.data.LogEntry
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class ReadinessEngineTest {
    private val today = LocalDate.parse("2026-08-20")

    private fun row(
        energy: Int? = null, soreness: Int? = null, stress: Int? = null, refreshed: Boolean? = null,
    ) = DayRow(
        dayKey = today.toString(), mood = null, bed = null, wake = null, sleeps = null, plans = null,
        skin = null, energy = energy, soreness = soreness, stress = stress, refreshed = refreshed,
    )

    private fun recovery(state: RecoveryEngine.State, vararg reasons: String) =
        RecoveryEngine.Reading(state, reasons.toList())

    private fun log(day: String, difficulty: Int? = null) =
        LogEntry(exId = "bench", at = "${day}T10:00:00", reps = 8, sets = 3, load = 50.0, faultEvents = "[]", correctedFrom = null, difficulty = difficulty)

    @Test
    fun `no recovery evidence and no self-reports refuses to produce a readiness at all`() {
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.INSUFFICIENT_DATA), null, emptyList(), today)
        assertEquals(ReadinessEngine.Level.INSUFFICIENT_DATA, r.level)
        assertEquals(ReadinessEngine.Confidence.INSUFFICIENT, r.confidence)
    }

    @Test
    fun `real recovery need plus a second signal reads as very low`() {
        val r = ReadinessEngine.evaluate(
            recovery(RecoveryEngine.State.RECOVERY_NEEDED, "sleep well below your normal", "recent training load well above your normal"),
            row(energy = 3), emptyList(), today,
        )
        assertEquals(ReadinessEngine.Level.VERY_LOW, r.level)
        assertTrue(r.factors.any { it.contains("sleep well below") })
    }

    @Test
    fun `strong positives with nothing negative read as excellent`() {
        // soreness 2 is a real answer that is neither a positive nor a negative -- it earns the
        // third self-report that lifts confidence to HIGH without moving the level.
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(energy = 9, soreness = 2, refreshed = true), emptyList(), today)
        assertEquals(ReadinessEngine.Level.EXCELLENT, r.level)
        assertEquals(ReadinessEngine.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `a single mild negative reads as moderate, not low`() {
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(energy = 3), emptyList(), today)
        assertEquals(ReadinessEngine.Level.MODERATE, r.level)
        assertTrue(r.factors.any { it.contains("energy is low") })
    }

    @Test
    fun `severe soreness alone is enough to read low`() {
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(soreness = 9), emptyList(), today)
        assertEquals(ReadinessEngine.Level.LOW, r.level)
    }

    @Test
    fun `a long training streak counts as a real negative on its own`() {
        val logs = (17..20).map { log("2026-08-$it") }
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), null, logs, today)
        assertTrue(r.factors.any { it.contains("4 days in a row") })
    }

    @Test
    fun `recent hard sessions register even with no check-in today`() {
        val logs = (17..19).map { log("2026-08-$it", difficulty = 3) }
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), null, logs, today)
        assertTrue(r.factors.any { it.contains("felt hard") })
    }

    @Test
    fun `confidence rises with the amount of real evidence, never with how extreme the reading is`() {
        val thin = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), null, emptyList(), today)
        val some = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(energy = 7), emptyList(), today)
        val rich = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(energy = 7, soreness = 2, stress = 3), emptyList(), today)
        assertEquals("recovery read but zero check-in answers", ReadinessEngine.Confidence.LOW, thin.confidence)
        assertEquals(ReadinessEngine.Confidence.MODERATE, some.confidence)
        assertEquals(ReadinessEngine.Confidence.HIGH, rich.confidence)
    }

    @Test
    fun `self-reports alone, with no recovery read, still produce an honest low-confidence answer`() {
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.INSUFFICIENT_DATA), row(energy = 8, refreshed = true), emptyList(), today)
        assertNotEquals(ReadinessEngine.Level.INSUFFICIENT_DATA, r.level)
        assertEquals(ReadinessEngine.Confidence.LOW, r.confidence)
    }

    @Test
    fun `hard work is only ever allowed at genuinely good readiness`() {
        assertTrue(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.EXCELLENT))
        assertTrue(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.GOOD))
        assertFalse(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.MODERATE))
        assertFalse(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.LOW))
        assertFalse(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.VERY_LOW))
        assertFalse(ReadinessEngine.allowsHardWork(ReadinessEngine.Level.INSUFFICIENT_DATA))
    }

    @Test
    fun `readiness never reports a fake precise score anywhere in its output`() {
        val r = ReadinessEngine.evaluate(recovery(RecoveryEngine.State.READY), row(energy = 9, refreshed = true), emptyList(), today)
        // Levels are categories by construction; this guards the factor text too.
        r.factors.forEach { assertFalse("no invented percentages: $it", it.contains("%")) }
    }
}
