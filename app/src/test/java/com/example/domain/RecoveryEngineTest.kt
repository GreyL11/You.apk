package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class RecoveryEngineTest {
    @Test
    fun `no real evidence on either side is INSUFFICIENT_DATA, not a guessed READY`() {
        val r = RecoveryEngine.evaluate(HealthStateEngine.State.INSUFFICIENT_DATA, TrainingLoadEngine.State.INSUFFICIENT_DATA)
        assertEquals(RecoveryEngine.State.INSUFFICIENT_DATA, r.state)
    }

    @Test
    fun `poor sleep and high load together is RECOVERY_NEEDED, with both reasons named`() {
        val r = RecoveryEngine.evaluate(HealthStateEngine.State.NEEDS_ATTENTION, TrainingLoadEngine.State.HIGH_LOAD)
        assertEquals(RecoveryEngine.State.RECOVERY_NEEDED, r.state)
        assertEquals(2, r.reasons.size)
    }

    @Test
    fun `only one real red flag is MODERATE, not the full RECOVERY_NEEDED`() {
        val poorSleepOnly = RecoveryEngine.evaluate(HealthStateEngine.State.NEEDS_ATTENTION, TrainingLoadEngine.State.APPROPRIATE)
        assertEquals(RecoveryEngine.State.MODERATE, poorSleepOnly.state)
        val highLoadOnly = RecoveryEngine.evaluate(HealthStateEngine.State.ON_TRACK, TrainingLoadEngine.State.HIGH_LOAD)
        assertEquals(RecoveryEngine.State.MODERATE, highLoadOnly.state)
    }

    @Test
    fun `on-track sleep and appropriate load reads READY`() {
        val r = RecoveryEngine.evaluate(HealthStateEngine.State.ON_TRACK, TrainingLoadEngine.State.APPROPRIATE)
        assertEquals(RecoveryEngine.State.READY, r.state)
        assertTrue(r.reasons.isEmpty())
    }

    @Test
    fun `partial evidence never rounds up to READY`() {
        val r = RecoveryEngine.evaluate(HealthStateEngine.State.ON_TRACK, TrainingLoadEngine.State.INSUFFICIENT_DATA)
        assertEquals(RecoveryEngine.State.MODERATE, r.state)
    }
}
