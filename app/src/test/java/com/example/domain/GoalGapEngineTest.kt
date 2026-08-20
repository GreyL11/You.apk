package com.example.domain

import org.junit.Assert.*
import org.junit.Test

class GoalGapEngineTest {
    private fun snapshot(
        nutrition: HealthStateEngine.State = HealthStateEngine.State.ON_TRACK,
        hydration: HealthStateEngine.State = HealthStateEngine.State.ON_TRACK,
        sleep: HealthStateEngine.State = HealthStateEngine.State.ON_TRACK,
        training: HealthStateEngine.State = HealthStateEngine.State.ON_TRACK,
        skinRoutine: HealthStateEngine.State = HealthStateEngine.State.ON_TRACK,
    ) = HealthStateEngine.Snapshot(nutrition, hydration, sleep, training, skinRoutine)

    private fun recovery(state: RecoveryEngine.State) = RecoveryEngine.Reading(state, emptyList())

    @Test
    fun `everything on track has no bottleneck`() {
        val reading = GoalGapEngine.evaluate(snapshot(), recovery(RecoveryEngine.State.READY))
        assertNull(reading.bottleneck)
        assertTrue(reading.dimensions.all { it.gap == GoalGapEngine.Gap.ON_TRACK })
    }

    @Test
    fun `a single large gap becomes the bottleneck over several moderate ones`() {
        val snap = snapshot(
            nutrition = HealthStateEngine.State.WATCH,
            hydration = HealthStateEngine.State.WATCH,
            training = HealthStateEngine.State.NEEDS_ATTENTION,
        )
        val reading = GoalGapEngine.evaluate(snap, recovery(RecoveryEngine.State.READY))
        assertEquals(GoalGapEngine.Dimension.TRAINING_CONSISTENCY, reading.bottleneck)
    }

    @Test
    fun `sleep outranks recovery and training when multiple real large gaps exist`() {
        val snap = snapshot(sleep = HealthStateEngine.State.NEEDS_ATTENTION, training = HealthStateEngine.State.NEEDS_ATTENTION)
        val reading = GoalGapEngine.evaluate(snap, recovery(RecoveryEngine.State.RECOVERY_NEEDED))
        assertEquals(GoalGapEngine.Dimension.SLEEP_CONSISTENCY, reading.bottleneck)
    }

    @Test
    fun `insufficient data on a dimension never wins over a real moderate gap elsewhere`() {
        val snap = snapshot(hydration = HealthStateEngine.State.INSUFFICIENT_DATA, skinRoutine = HealthStateEngine.State.WATCH)
        val reading = GoalGapEngine.evaluate(snap, recovery(RecoveryEngine.State.READY))
        assertEquals(GoalGapEngine.Dimension.SKIN_ROUTINE, reading.bottleneck)
    }

    @Test
    fun `all dimensions insufficient data reports no bottleneck rather than guessing`() {
        val snap = snapshot(
            nutrition = HealthStateEngine.State.INSUFFICIENT_DATA, hydration = HealthStateEngine.State.INSUFFICIENT_DATA,
            sleep = HealthStateEngine.State.INSUFFICIENT_DATA, training = HealthStateEngine.State.INSUFFICIENT_DATA,
            skinRoutine = HealthStateEngine.State.INSUFFICIENT_DATA,
        )
        val reading = GoalGapEngine.evaluate(snap, recovery(RecoveryEngine.State.INSUFFICIENT_DATA))
        assertNull(reading.bottleneck)
        assertTrue(reading.dimensions.all { it.gap == GoalGapEngine.Gap.INSUFFICIENT_DATA })
    }
}
