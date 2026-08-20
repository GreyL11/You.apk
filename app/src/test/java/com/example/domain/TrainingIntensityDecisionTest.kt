package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class TrainingIntensityDecisionTest {
    private fun recovery(state: RecoveryEngine.State, vararg reasons: String) = RecoveryEngine.Reading(state, reasons.toList())

    private fun load(
        state: TrainingLoadEngine.State,
        recentSets: Int = 20,
        typicalWeeklySets: Double? = if (state == TrainingLoadEngine.State.INSUFFICIENT_DATA) null else 15.0,
    ) = TrainingLoadEngine.Reading(recentSets, typicalWeeklySets, state)

    @Test
    fun `no real evidence on either side refuses to decide`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.INSUFFICIENT_DATA), load(TrainingLoadEngine.State.INSUFFICIENT_DATA))
        assertEquals(TrainingIntensityDecision.Decision.INSUFFICIENT_DATA, r.decision)
    }

    @Test
    fun `recovery needed always wins as a recovery day, at high confidence`() {
        val r = TrainingIntensityDecision.decide(
            recovery(RecoveryEngine.State.RECOVERY_NEEDED, "sleep well below your normal", "recent training load well above your normal"),
            load(TrainingLoadEngine.State.HIGH_LOAD),
        )
        assertEquals(TrainingIntensityDecision.Decision.RECOVERY_DAY, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.HIGH, r.confidence)
        assertTrue(r.reason.contains("sleep well below your normal"))
    }

    @Test
    fun `moderate recovery plus high load is a reduced session, not a full stop`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.MODERATE), load(TrainingLoadEngine.State.HIGH_LOAD))
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `high load alone is enough for a reduced session even with good recovery`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), load(TrainingLoadEngine.State.HIGH_LOAD))
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `moderate recovery alone (appropriate load) still reduces, never a silent full session`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.MODERATE), load(TrainingLoadEngine.State.APPROPRIATE))
        assertEquals(TrainingIntensityDecision.Decision.REDUCED_SESSION, r.decision)
    }

    @Test
    fun `ready recovery and appropriate load is a real full session at high confidence`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), load(TrainingLoadEngine.State.APPROPRIATE))
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.HIGH, r.confidence)
    }

    @Test
    fun `underloaded never argues against training even at ready recovery`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), load(TrainingLoadEngine.State.UNDERLOADED))
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
    }

    @Test
    fun `good recovery with thin load history still greenlights training, but at lower confidence`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), load(TrainingLoadEngine.State.INSUFFICIENT_DATA))
        assertEquals(TrainingIntensityDecision.Decision.FULL_SESSION, r.decision)
        assertEquals(TrainingIntensityDecision.Confidence.LOW, r.confidence)
    }

    // ── real numbers in the reason, not just a qualifier ────────────────────────────────────

    @Test
    fun `a high-load reason names the real recent-vs-typical sets`() {
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.READY), load(TrainingLoadEngine.State.HIGH_LOAD, recentSets = 30, typicalWeeklySets = 18.0))
        assertTrue(r.reason.contains("30 sets"))
        assertTrue(r.reason.contains("typical 18/week"))
    }

    @Test
    fun `moderate recovery alone never fabricates load numbers it doesn't have`() {
        // APPROPRIATE load with no real typicalWeeklySets shouldn't happen from a real evaluate()
        // call, but the reason for this branch never touches load numbers anyway -- confirms it.
        val r = TrainingIntensityDecision.decide(recovery(RecoveryEngine.State.MODERATE), load(TrainingLoadEngine.State.APPROPRIATE))
        assertFalse(r.reason.contains("typical"))
    }
}
