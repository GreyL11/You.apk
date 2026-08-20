package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class TrainingIntensityDecisionTest {
    private fun recovery(state: RecoveryEngine.State, vararg reasons: String) = RecoveryEngine.Reading(state, reasons.toList())

    @Test
    fun `no real evidence on either side refuses to decide`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.INSUFFICIENT_DATA), TrainingLoadEngine.State.INSUFFICIENT_DATA)
        assertEquals(TrainingIntensityDecision.Decision.INSUFFICIENT_DATA, r.decision)
    }

    @Test
    fun `recovery needed always wins as a recovery day, at high confidence`() {
        val r = TrainingIntensityDecision.decide(
            recovery(RecoveryEngine.State.RECOVERY_NEEDED, "sleep well below your normal", "recent training load well above your normal"),
            TrainingLoadEngine.State.HIGH_LOAD,
        )
        assertEquals(TrainingIntensityDecision.Decision.RECOVERY_DAY, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.HIGH, r.confidence)
        assertTrue(r.reason.contains("sleep well below your normal"))
    }

    @Test
    fun `moderate recovery plus high load is a reduced session, not a full stop`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.MODERATE), TrainingLoadEngine.State.HIGH_LOAD)
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `high load alone is enough for a reduced session even with good recovery`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), TrainingLoadEngine.State.HIGH_LOAD)
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `moderate recovery alone (appropriate load) still reduces, never a silent full session`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.MODERATE), TrainingLoadEngine.State.APPROPRIATE)
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `ready recovery and appropriate load is a real full session at high confidence`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), TrainingLoadEngine.State.APPROPRIATE)
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `underloaded never argues against training even at ready recovery`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), TrainingLoadEngine.State.UNDERLOADED)
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
    }

    @Test
    fun `good recovery with thin load history still greenlights training, but at lower confidence`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), TrainingLoadEngine.State.INSUFFICIENT_DATA)
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.LOW, r.confidence)
    }
}
